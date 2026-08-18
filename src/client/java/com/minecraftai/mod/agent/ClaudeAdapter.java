package com.minecraftai.mod.agent;

import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatFormatter;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.config.ModConfig;
import com.minecraftai.mod.hud.HudNotificationManager;
import com.minecraftai.mod.parser.AgentEvent;
import com.minecraftai.mod.parser.ClaudeStreamParser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class ClaudeAdapter implements AgentAdapter {

    private final AgentStatus status = new AgentStatus();
    private final AgentTelemetry telemetry = new AgentTelemetry();
    private final ClaudeStreamParser parser = new ClaudeStreamParser();
    private final List<String> transcript = new LinkedList<>();
    private final List<String> queuedPrompts = new LinkedList<>();
    private final List<TurnInfo> turnHistory = new LinkedList<>();

    private String sessionId = null;
    private String model = "claude-opus-5";
    private String effort = "xhigh";
    private String permissionMode = "bypassPermissions"; // plan, manual, auto, bypassPermissions
    private ProcessRunner currentRunner = null;
    private String latestDiff = "";

    private String lastPrompt = "";
    private String awayRecap = null;

    public ClaudeAdapter() {
        parser.setTelemetry(telemetry);
    }

    @Override
    public String getAgentName() {
        return "Claude";
    }

    @Override
    public synchronized void sendPrompt(String prompt, boolean steer, boolean queue) {
        if (queue) {
            queuedPrompts.add(prompt);
            ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§7Queued prompt: §f" + prompt);
            return;
        }

        if (status.isRunning()) {
            if (steer) {
                // Steer: interrupt running turn safely and immediately send steer instruction
                ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§eSteering active run: §f" + prompt);
                stop();
                startPrompt(prompt);
                return;
            } else {
                ChatNotifier.sendError("Claude is currently running. Use Steer or Queue.");
                return;
            }
        }

        startPrompt(prompt);
    }

    @Override
    public synchronized void startPrompt(String prompt) {
        if (status.isRunning()) {
            ChatNotifier.sendError("Claude is already running a task. Use '/ai stop claude' first.");
            return;
        }

        this.lastPrompt = prompt;
        ModConfig config = ConfigManager.getInstance().getConfig();
        String executable = config.getClaudeExecutable();
        String workDir = config.getWorkingDirectory();

        boolean isNewSession = (sessionId == null || sessionId.isBlank());
        if (isNewSession) {
            sessionId = UUID.randomUUID().toString();
        }

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-p");
        command.add(prompt);
        command.add("--output-format");
        command.add("stream-json");
        command.add("--verbose");

        if (isNewSession) {
            command.add("--session-id");
            command.add(sessionId);
        } else {
            command.add("--resume");
            command.add(sessionId);
        }

        if (permissionMode.equalsIgnoreCase("bypassPermissions") || permissionMode.equalsIgnoreCase("yolo")) {
            command.add("--permission-mode");
            command.add("bypassPermissions");
        } else if (permissionMode.equalsIgnoreCase("plan")) {
            command.add("--permission-mode");
            command.add("plan");
        } else if (permissionMode.equalsIgnoreCase("auto")) {
            command.add("--permission-mode");
            command.add("auto");
        }

        parser.reset();
        status.setRunning("Sending prompt...");
        addTranscript(">> [Prompt] " + prompt);

        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§7Prompt sent: §f" + prompt + "§r");

        File dir = new File(workDir);
        currentRunner = new ProcessRunner(
                command,
                dir.exists() && dir.isDirectory() ? dir : null,
                this::handleStdout,
                this::handleStderr,
                this::handleExit
        );

        try {
            currentRunner.start();
        } catch (Exception e) {
            status.setError(e.getMessage());
            ChatNotifier.sendError("Failed to start Claude: " + e.getMessage());
        }
    }

    private void handleStdout(String line) {
        addTranscript(line);
        parser.parseLine(line, this::handleEvent);
    }

    private void handleStderr(String line) {
        if (line != null && !line.isBlank()) {
            addTranscript("[ERR] " + line);
            if (line.contains("Error") || line.contains("error")) {
                ChatNotifier.sendError("Claude: " + line);
            }
        }
    }

    private void handleEvent(AgentEvent event) {
        ModConfig config = ConfigManager.getInstance().getConfig();
        int maxLen = config.getMaxChatLineLength();

        switch (event.getType()) {
            case ACTIVITY:
                status.setCurrentActivity(event.getContent());
                if (config.isShowActivityEvents()) {
                    ChatNotifier.sendMessage(ChatFormatter.formatActivity("Claude", event.getContent()));
                }
                break;
            case COMMAND:
                status.setCurrentTool(event.getContent());
                if (config.isShowToolCommands()) {
                    ChatNotifier.sendMessage(ChatFormatter.formatCommand("Claude", event.getContent()));
                }
                break;
            case SUCCESS:
                ChatNotifier.sendMessage(ChatFormatter.formatSuccess("Claude", event.getContent()));
                break;
            case ERROR:
                status.setError(event.getContent());
                ChatNotifier.sendMessage(ChatFormatter.formatError("Claude", event.getContent()));
                break;
            case RESPONSE:
                List<String> lines = ChatFormatter.splitMessage(ChatColorUtil.CLAUDE_PREFIX, event.getContent(), maxLen);
                ChatNotifier.sendMessages(lines);
                break;
            case DONE:
                status.setIdle();
                recordCompletedTurn(event.getContent());
                ChatNotifier.sendMessage(ChatFormatter.formatDone("Claude", event.getContent()));
                HudNotificationManager.notifyCompletion("Claude", event.getContent());
                checkQueuedPrompts();
                break;
            case SESSION_ID:
                this.sessionId = event.getContent();
                break;
            case APPROVAL_REQUEST:
                ApprovalRequest req = (ApprovalRequest) event.getPayload();
                status.setWaitingInput(req);
                HudNotificationManager.notifyWaitingForInput("Claude", req.getToolName() + " " + req.getCommandOrDetails());
                ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§c⚠ APPROVAL REQUIRED: §f" + req.getToolName() + " " + req.getCommandOrDetails());
                ChatNotifier.sendMessage("  §a[ALLOW ONCE: /ai approve " + req.getId() + "] §c[DENY: /ai deny " + req.getId() + "]");
                break;
        }
    }

    private void recordCompletedTurn(String summary) {
        String turnId = "turn-" + (turnHistory.size() + 1);
        TurnInfo turn = new TurnInfo(turnId, lastPrompt, summary != null && !summary.isBlank() ? summary : lastPrompt, "");
        turn.setStatus(TurnInfo.Status.SUCCESS);
        turnHistory.add(turn);

        // Generate Away Recap summary
        String changed = status.getChangedFiles().isEmpty() ? "" : (status.getChangedFiles().size() + " files modified, ");
        this.awayRecap = "Claude completed turn: \"" + (lastPrompt.length() > 36 ? lastPrompt.substring(0, 33) + "..." : lastPrompt) +
                "\". (" + changed + "turn finished successfully).";
    }

    private void handleExit(int exitCode) {
        if (currentRunner != null) {
            currentRunner = null;
        }
        if (exitCode != 0) {
            status.setError("Exit code " + exitCode);
            ChatNotifier.sendError("Claude process exited with code " + exitCode);
        } else {
            status.setIdle();
            checkQueuedPrompts();
        }
    }

    private void checkQueuedPrompts() {
        if (!queuedPrompts.isEmpty() && !status.isRunning()) {
            String next = queuedPrompts.remove(0);
            ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§6Executing queued prompt: §f" + next);
            startPrompt(next);
        }
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
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§6Rewinding conversation to checkpoint: §f" + turnId + "§r");
        startPrompt("/rewind " + turnId);
    }

    @Override
    public void forkFromTurn(String id, String turnId) {
        stop();
        String newId = UUID.randomUUID().toString();
        this.sessionId = newId;
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§aForked session from " + turnId + " into: §f" + newId + "§r");
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
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§6■ Interrupted turn.§r");
    }

    @Override
    public synchronized void stop() {
        if (currentRunner != null) {
            status.setStopping();
            currentRunner.stop();
            currentRunner = null;
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
        String dir = ConfigManager.getInstance().getConfig().getWorkingDirectory();
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§aStarted new session on: §f" + dir + "§r");
    }

    @Override
    public synchronized void resumeSession(String id, String title) {
        stop();
        this.sessionId = id;
        this.awayRecap = null;
        String dir = ConfigManager.getInstance().getConfig().getWorkingDirectory();
        String titleStr = (title != null && !title.isBlank()) ? " (\"" + title + "\")" : "";
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§aResumed session: §f" + id + titleStr + " §7(dir: " + dir + ")§r");
    }

    @Override
    public synchronized void forkSession(String id) {
        forkFromTurn(id, "latest");
    }

    @Override
    public synchronized void approve(String approvalId, boolean forSession) {
        status.setRunning("Approved tool execution");
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§aApproved tool request (" + approvalId + ")");
    }

    @Override
    public synchronized void deny(String approvalId) {
        status.setIdle();
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§cDenied tool request (" + approvalId + ")");
    }

    @Override
    public synchronized void compact() {
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§6Compact requested. Sending compact turn...");
        startPrompt("/compact");
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
    public String getLatestDiff() {
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
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§7Model set to: §f" + model);
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
        ChatNotifier.sendMessage(ChatColorUtil.CLAUDE_PREFIX + "§7Permission mode: §e" + permissionMode);
    }

    @Override
    public void cyclePermissionMode() {
        if (permissionMode.equalsIgnoreCase("plan")) {
            setPermissionMode("manual");
        } else if (permissionMode.equalsIgnoreCase("manual")) {
            setPermissionMode("auto");
        } else if (permissionMode.equalsIgnoreCase("auto")) {
            setPermissionMode("bypassPermissions");
        } else {
            setPermissionMode("plan");
        }
    }
}
