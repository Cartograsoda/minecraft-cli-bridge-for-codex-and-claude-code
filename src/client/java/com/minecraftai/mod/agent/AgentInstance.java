package com.minecraftai.mod.agent;

import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatFormatter;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.codex.CodexAppServerClient;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.config.ModConfig;
import com.minecraftai.mod.hud.HudNotificationManager;
import com.minecraftai.mod.parser.AgentEvent;
import com.minecraftai.mod.parser.ClaudeStreamParser;
import com.minecraftai.mod.parser.CodexStreamParser;
import com.minecraftai.mod.parser.StreamParser;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.workspace.WorkspaceMode;
import com.minecraftai.mod.workspace.WorktreeManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class AgentInstance implements AgentAdapter {

    private final String instanceId;
    private final String label;
    private final String providerType; // "Claude" or "Codex"
    private final ProjectProfile project;
    private final File baseRepoDirectory;
    private File workingDirectory;
    private WorkspaceMode workspaceMode;
    private final String worktreeBranch;

    private final AgentStatus status = new AgentStatus();
    private final AgentTelemetry telemetry = new AgentTelemetry();
    private final StreamParser parser;
    private final List<String> transcript = Collections.synchronizedList(new LinkedList<>());
    private final List<String> queuedPrompts = Collections.synchronizedList(new LinkedList<>());
    private final List<TurnInfo> turnHistory = Collections.synchronizedList(new LinkedList<>());

    // Generational run management to eliminate concurrency races between old exits and new runs
    private final AtomicLong runCounter = new AtomicLong(0);
    private volatile long activeRunId = 0;

    private String sessionId = null;
    private String model;
    private String effort;
    private String permissionMode;
    private ProcessRunner currentRunner = null;
    private String latestDiff = "";
    private String lastPrompt = "";
    private String awayRecap = null;

    public AgentInstance(String instanceId, String label, String providerType, ProjectProfile project, WorkspaceMode requestedMode) {
        this.instanceId = instanceId;
        this.label = label;
        this.providerType = providerType;
        this.project = project;
        this.worktreeBranch = WorktreeManager.getBranchName(providerType, label);

        this.baseRepoDirectory = new File(project != null ? project.getWorkingDirectory() : ConfigManager.getInstance().getConfig().getWorkingDirectory());

        // Fail-closed worktree isolation
        if (requestedMode == WorkspaceMode.ISOLATED_WORKTREE) {
            File wt = WorktreeManager.getInstance().createWorktree(baseRepoDirectory, providerType, label);
            if (wt != null && wt.exists()) {
                this.workingDirectory = wt;
                this.workspaceMode = WorkspaceMode.ISOLATED_WORKTREE;
            } else {
                this.workingDirectory = baseRepoDirectory;
                this.workspaceMode = WorkspaceMode.SHARED_WORKING_TREE;
                ChatNotifier.sendError("ISOLATION FAILED for " + getAgentName() + ". Falling back to shared repository.");
            }
        } else {
            this.workingDirectory = baseRepoDirectory;
            this.workspaceMode = WorkspaceMode.SHARED_WORKING_TREE;
        }

        if ("Claude".equalsIgnoreCase(providerType)) {
            ClaudeStreamParser cp = new ClaudeStreamParser();
            cp.setTelemetry(telemetry);
            this.parser = cp;
            this.model = project != null ? project.getDefaultClaudeModel() : "claude-opus-5";
            this.effort = "xhigh";
            this.permissionMode = project != null ? project.getDefaultClaudePermissionMode() : "plan";
        } else {
            CodexStreamParser xp = new CodexStreamParser();
            xp.setTelemetry(telemetry);
            this.parser = xp;
            this.model = project != null ? project.getDefaultCodexModel() : "gpt-5.x";
            this.effort = "xhigh";
            this.permissionMode = project != null ? project.getDefaultCodexPermissionMode() : "workspace-write";
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getLabel() {
        return label;
    }

    public String getProviderType() {
        return providerType;
    }

    public ProjectProfile getProject() {
        return project;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public File getBaseRepoDirectory() {
        return baseRepoDirectory;
    }

    public WorkspaceMode getWorkspaceMode() {
        return workspaceMode;
    }

    public String getWorktreeBranch() {
        return worktreeBranch;
    }

    public boolean isIsolated() {
        return workspaceMode == WorkspaceMode.ISOLATED_WORKTREE;
    }

    @Override
    public String getAgentName() {
        return providerType + "/" + label;
    }

    @Override
    public synchronized void sendPrompt(String prompt, boolean steer, boolean queue) {
        if (queue) {
            queuedPrompts.add(prompt);
            ChatNotifier.sendMessage(getPrefix() + "§7Queued prompt: §f" + prompt);
            return;
        }

        if (status.isRunning()) {
            if (steer) {
                ChatNotifier.sendMessage(getPrefix() + "§eInterrupting & sending new prompt: §f" + prompt);
                stop();
                startPrompt(prompt);
                return;
            } else {
                ChatNotifier.sendError(getAgentName() + " is currently running. Use Steer or Queue.");
                return;
            }
        }

        startPrompt(prompt);
    }

    @Override
    public synchronized void startPrompt(String prompt) {
        if (status.isRunning()) {
            ChatNotifier.sendError(getAgentName() + " is already running a task.");
            return;
        }

        long runId = runCounter.incrementAndGet();
        this.activeRunId = runId;
        this.lastPrompt = prompt;
        ModConfig config = ConfigManager.getInstance().getConfig();
        boolean isClaude = "Claude".equalsIgnoreCase(providerType);

        List<String> command = new ArrayList<>();
        if (isClaude) {
            command.add(config.getClaudeExecutable());
            command.add("-p");
            command.add(prompt);
            command.add("--output-format");
            command.add("stream-json");
            command.add("--verbose");
            if (model != null && !model.isBlank()) {
                command.add("--model");
                command.add(model);
            }
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
                command.add("--session-id");
                command.add(sessionId);
            } else {
                command.add("--resume");
                command.add(sessionId);
            }
            if (permissionMode != null && !permissionMode.isBlank()) {
                if (permissionMode.equalsIgnoreCase("bypassPermissions") || permissionMode.equalsIgnoreCase("yolo")) {
                    command.add("--permission-mode");
                    command.add("bypassPermissions");
                } else if (permissionMode.equalsIgnoreCase("acceptEdits")) {
                    command.add("--permission-mode");
                    command.add("acceptEdits");
                } else if (permissionMode.equalsIgnoreCase("plan")) {
                    command.add("--permission-mode");
                    command.add("plan");
                } else if (permissionMode.equalsIgnoreCase("auto")) {
                    command.add("--permission-mode");
                    command.add("auto");
                } else if (permissionMode.equalsIgnoreCase("dontAsk")) {
                    command.add("--permission-mode");
                    command.add("dontAsk");
                }
            }
        } else {
            command.add(config.getCodexExecutable());
            command.add("exec");
            if (model != null && !model.isBlank()) {
                command.add("-m");
                command.add(model);
            }
            if (permissionMode != null && !permissionMode.isBlank()) {
                if (permissionMode.equalsIgnoreCase("danger-full-access") || permissionMode.equalsIgnoreCase("yolo")) {
                    command.add("-s");
                    command.add("danger-full-access");
                } else if (permissionMode.equalsIgnoreCase("read-only")) {
                    command.add("-s");
                    command.add("read-only");
                } else if (permissionMode.equalsIgnoreCase("workspace-write")) {
                    command.add("-s");
                    command.add("workspace-write");
                }
            }
            if (sessionId != null && !sessionId.isBlank()) {
                command.add("resume");
                command.add("--skip-git-repo-check");
                command.add("--json");
                command.add(sessionId);
                command.add(prompt);
            } else {
                command.add("--skip-git-repo-check");
                command.add("--json");
                command.add(prompt);
            }
        }

        parser.reset();
        status.setRunning("Sending prompt...");
        addTranscript(">> [Prompt] " + prompt);

        ChatNotifier.sendMessage(getPrefix() + "§7Prompt sent: §f" + prompt + "§r");

        currentRunner = new ProcessRunner(
                command,
                workingDirectory != null && workingDirectory.exists() && workingDirectory.isDirectory() ? workingDirectory : null,
                line -> handleStdout(runId, line),
                line -> handleStderr(runId, line),
                code -> handleExit(runId, code)
        );

        try {
            currentRunner.start();
        } catch (Exception e) {
            status.setError(e.getMessage());
            ChatNotifier.sendError("Failed to start " + getAgentName() + ": " + e.getMessage());
        }
    }

    private String getPrefix() {
        return "Claude".equalsIgnoreCase(providerType) ? ChatColorUtil.CLAUDE_PREFIX : ChatColorUtil.CODEX_PREFIX;
    }

    private void handleStdout(long runId, String line) {
        if (runId != activeRunId) return;
        addTranscript(line);
        parser.parseLine(line, ev -> handleEvent(runId, ev));
    }

    private void handleStderr(long runId, String line) {
        if (runId != activeRunId) return;
        if (line != null && !line.isBlank()) {
            addTranscript("[ERR] " + line);
            if (line.contains("Error") || line.contains("error")) {
                ChatNotifier.sendError(getAgentName() + ": " + line);
            }
        }
    }

    private void handleEvent(long runId, AgentEvent event) {
        if (runId != activeRunId) return;
        ModConfig config = ConfigManager.getInstance().getConfig();
        int maxLen = config.getMaxChatLineLength();

        switch (event.getType()) {
            case ACTIVITY:
                status.setCurrentActivity(event.getContent());
                if (config.isShowActivityEvents()) {
                    ChatNotifier.sendMessage(ChatFormatter.formatActivity(getAgentName(), event.getContent()));
                }
                break;
            case COMMAND:
                status.setCurrentTool(event.getContent());
                if (config.isShowToolCommands()) {
                    ChatNotifier.sendMessage(ChatFormatter.formatCommand(getAgentName(), event.getContent()));
                }
                break;
            case SUCCESS:
                ChatNotifier.sendMessage(ChatFormatter.formatSuccess(getAgentName(), event.getContent()));
                break;
            case ERROR:
                status.setError(event.getContent());
                ChatNotifier.sendMessage(ChatFormatter.formatError(getAgentName(), event.getContent()));
                break;
            case RESPONSE:
                List<String> lines = ChatFormatter.splitMessage(getPrefix(), event.getContent(), maxLen);
                ChatNotifier.sendMessages(lines);
                break;
            case DONE:
                recordCompletedTurn(event.getContent());
                ChatNotifier.sendMessage(ChatFormatter.formatDone(getAgentName(), event.getContent()));
                HudNotificationManager.notifyCompletion(getAgentName(), event.getContent());
                // We keep running until the process officially exits in handleExit
                break;
            case SESSION_ID:
                this.sessionId = event.getContent();
                break;
            case APPROVAL_REQUEST:
                ApprovalRequest req = (ApprovalRequest) event.getPayload();
                status.setWaitingInput(req);
                HudNotificationManager.notifyWaitingForInput(getAgentName(), req.getToolName() + " " + req.getCommandOrDetails());
                ChatNotifier.sendMessage(getPrefix() + "§c⚠ APPROVAL REQUIRED: §f" + req.getToolName() + " " + req.getCommandOrDetails());
                ChatNotifier.sendMessage("  §a[ALLOW ONCE: /ai approve " + req.getId() + "] §c[DENY: /ai deny " + req.getId() + "]");
                break;
        }
    }

    private void recordCompletedTurn(String summary) {
        String turnId = "turn-" + (turnHistory.size() + 1);
        TurnInfo turn = new TurnInfo(turnId, lastPrompt, summary != null && !summary.isBlank() ? summary : lastPrompt, "");
        turn.setStatus(TurnInfo.Status.SUCCESS);
        turnHistory.add(turn);

        String changed = status.getChangedFiles().isEmpty() ? "" : (status.getChangedFiles().size() + " files modified, ");
        this.awayRecap = getAgentName() + " completed turn: \"" + (lastPrompt.length() > 36 ? lastPrompt.substring(0, 33) + "..." : lastPrompt) +
                "\". (" + changed + "turn finished successfully).";
    }

    private void handleExit(long runId, int exitCode) {
        if (runId != activeRunId) return;

        currentRunner = null;
        if (exitCode != 0) {
            status.setError("Exit code " + exitCode);
        } else {
            status.setIdle();
            checkQueuedPrompts();
        }
    }

    private void checkQueuedPrompts() {
        if (!queuedPrompts.isEmpty() && !status.isRunning()) {
            String next = queuedPrompts.remove(0);
            ChatNotifier.sendMessage(getPrefix() + "§6Executing queued prompt: §f" + next);
            startPrompt(next);
        }
    }

    public void addTranscript(String line) {
        transcript.add(line);
        if (transcript.size() > 500) {
            transcript.remove(0);
        }
    }

    @Override
    public List<String> getTranscript() {
        return new ArrayList<>(transcript);
    }

    @Override
    public List<String> getPromptQueue() {
        return new ArrayList<>(queuedPrompts);
    }

    @Override
    public void removeQueuedPrompt(int index) {
        if (index >= 0 && index < queuedPrompts.size()) {
            queuedPrompts.remove(index);
        }
    }

    @Override
    public String takeBackQueuedPrompt(int index) {
        if (index >= 0 && index < queuedPrompts.size()) {
            return queuedPrompts.remove(index);
        }
        return null;
    }

    @Override
    public List<TurnInfo> getTurnHistory() {
        return new ArrayList<>(turnHistory);
    }

    @Override
    public void rewindToCheckpoint(String turnId) {
        stop();
        ChatNotifier.sendMessage(getPrefix() + "§6Rewinding to checkpoint: §f" + turnId + "§r");
        startPrompt("/rewind " + turnId);
    }

    @Override
    public void forkFromTurn(String id, String turnId) {
        stop();
        if ("Codex".equalsIgnoreCase(providerType) && CodexAppServerClient.getInstance().isConnected()) {
            String forked = CodexAppServerClient.getInstance().forkThread(id, turnId);
            if (forked != null) {
                this.sessionId = forked;
                ChatNotifier.sendMessage(getPrefix() + "§aForked thread from turn " + turnId + " -> ID: §f" + forked + "§r");
                return;
            }
        }
        String newId = UUID.randomUUID().toString();
        this.sessionId = newId;
        ChatNotifier.sendMessage(getPrefix() + "§aForked session -> ID: §f" + newId + "§r");
    }

    @Override
    public String getAwayRecap() {
        return awayRecap;
    }

    @Override
    public void clearAwayRecap() {
        this.awayRecap = null;
    }

    @Override
    public synchronized void interrupt() {
        stop();
        ChatNotifier.sendMessage(getPrefix() + "§6■ Interrupted turn.§r");
    }

    @Override
    public synchronized void stop() {
        activeRunId = 0; // Invalidate current run callbacks
        if (currentRunner != null) {
            status.setStopping();
            ProcessRunner r = currentRunner;
            currentRunner = null;
            r.stop();
            status.setIdle();
        } else {
            status.setIdle();
        }
    }

    @Override
    public synchronized void newSession() {
        stop();
        this.sessionId = null;
        this.turnHistory.clear();
        this.awayRecap = null;
        ChatNotifier.sendMessage(getPrefix() + "§aStarted fresh session for " + getAgentName() + " on " + workingDirectory.getPath());
    }

    @Override
    public synchronized void resumeSession(String id, String title) {
        stop();
        this.sessionId = id;
        this.awayRecap = null;
        String titleStr = (title != null && !title.isBlank()) ? " (\"" + title + "\")" : "";
        ChatNotifier.sendMessage(getPrefix() + "§aResumed " + getAgentName() + ": §f" + id + titleStr);
    }

    @Override
    public synchronized void forkSession(String id) {
        forkFromTurn(id, "latest");
    }

    @Override
    public synchronized void approve(String approvalId, boolean forSession) {
        status.setRunning("Approved tool execution");
        if ("Codex".equalsIgnoreCase(providerType)) {
            CodexAppServerClient.getInstance().respondToServerRequest(approvalId, true, null);
        }
        ChatNotifier.sendMessage(getPrefix() + "§aApproved request (" + approvalId + ")");
    }

    @Override
    public synchronized void deny(String approvalId) {
        status.setIdle();
        if ("Codex".equalsIgnoreCase(providerType)) {
            CodexAppServerClient.getInstance().respondToServerRequest(approvalId, false, "Denied by user");
        }
        ChatNotifier.sendMessage(getPrefix() + "§cDenied request (" + approvalId + ")");
    }

    @Override
    public synchronized void compact() {
        ChatNotifier.sendMessage(getPrefix() + "§6Compact requested.");
        startPrompt("/compact");
    }

    @Override
    public String getLatestDiff() {
        if (isIsolated()) {
            String diff = WorktreeManager.getInstance().getWorktreeDiff(baseRepoDirectory, providerType, label);
            if (!diff.isBlank()) return diff;
        }
        return latestDiff;
    }

    public void setLatestDiff(String diff) {
        this.latestDiff = diff;
    }

    @Override
    public AgentStatus getStatus() {
        return status;
    }

    @Override
    public AgentTelemetry getTelemetry() {
        return telemetry;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public void setModel(String model) {
        this.model = model;
        ChatNotifier.sendMessage(getPrefix() + "§7Model set to: §f" + model);
    }

    @Override
    public String getEffort() {
        return effort;
    }

    @Override
    public void setEffort(String effort) {
        this.effort = effort;
    }

    @Override
    public String getPermissionMode() {
        return permissionMode;
    }

    @Override
    public void setPermissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
        ChatNotifier.sendMessage(getPrefix() + "§7Permission mode: §e" + permissionMode);
    }

    @Override
    public void cyclePermissionMode() {
        if ("Claude".equalsIgnoreCase(providerType)) {
            if (permissionMode.equalsIgnoreCase("plan")) setPermissionMode("auto");
            else if (permissionMode.equalsIgnoreCase("auto")) setPermissionMode("acceptEdits");
            else if (permissionMode.equalsIgnoreCase("acceptEdits")) setPermissionMode("bypassPermissions");
            else setPermissionMode("plan");
        } else {
            if (permissionMode.equalsIgnoreCase("plan")) setPermissionMode("read-only");
            else if (permissionMode.equalsIgnoreCase("read-only")) setPermissionMode("workspace-write");
            else if (permissionMode.equalsIgnoreCase("workspace-write")) setPermissionMode("danger-full-access");
            else setPermissionMode("plan");
        }
    }
}
