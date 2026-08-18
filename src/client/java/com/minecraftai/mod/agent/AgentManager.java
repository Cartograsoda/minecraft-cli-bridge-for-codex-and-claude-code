package com.minecraftai.mod.agent;

import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.gui.*;
import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.workspace.WorkspaceMode;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class AgentManager {

    private static AgentManager instance;

    private final ClaudeProvider claudeProvider = new ClaudeProvider();
    private final CodexProvider codexProvider = new CodexProvider();
    private String focusedInstanceId = "claude/main";

    public AgentManager() {
        // Initialize default primary instances
        ProjectProfile defaultProj = ProjectManager.getInstance().getActiveProject();
        claudeProvider.spawnInstance("main", defaultProj, WorkspaceMode.SHARED_WORKING_TREE);
        codexProvider.spawnInstance("main", defaultProj, WorkspaceMode.SHARED_WORKING_TREE);
    }

    public static synchronized AgentManager getInstance() {
        if (instance == null) {
            instance = new AgentManager();
        }
        return instance;
    }

    public ClaudeProvider getClaudeProvider() {
        return claudeProvider;
    }

    public CodexProvider getCodexProvider() {
        return codexProvider;
    }

    public List<AgentInstance> getAllInstances() {
        List<AgentInstance> list = new ArrayList<>();
        list.addAll(claudeProvider.getAllInstances());
        list.addAll(codexProvider.getAllInstances());
        return list;
    }

    public AgentInstance getFocusedInstance() {
        for (AgentInstance inst : getAllInstances()) {
            if (inst.getInstanceId().equalsIgnoreCase(focusedInstanceId) || inst.getLabel().equalsIgnoreCase(focusedInstanceId)) {
                return inst;
            }
        }
        List<AgentInstance> all = getAllInstances();
        return !all.isEmpty() ? all.get(0) : null;
    }

    public AgentInstance getFocusedClaudeInstance() {
        AgentInstance focused = getFocusedInstance();
        if (focused != null && "Claude".equalsIgnoreCase(focused.getProviderType())) {
            return focused;
        }
        AgentInstance main = claudeProvider.getInstance("main");
        if (main != null) return main;
        List<AgentInstance> all = claudeProvider.getAllInstances();
        return !all.isEmpty() ? all.get(0) : null;
    }

    public AgentInstance getFocusedCodexInstance() {
        AgentInstance focused = getFocusedInstance();
        if (focused != null && "Codex".equalsIgnoreCase(focused.getProviderType())) {
            return focused;
        }
        AgentInstance main = codexProvider.getInstance("main");
        if (main != null) return main;
        List<AgentInstance> all = codexProvider.getAllInstances();
        return !all.isEmpty() ? all.get(0) : null;
    }

    public boolean setFocusedInstance(String query) {
        if (query == null || query.isBlank()) return false;
        String clean = query.trim().toLowerCase();

        // 1. Exact instance ID match (e.g. claude/main, codex/auth)
        for (AgentInstance inst : getAllInstances()) {
            if (inst.getInstanceId().equalsIgnoreCase(clean)) {
                applyFocus(inst);
                return true;
            }
        }

        // 2. Unambiguous label match
        List<AgentInstance> matches = new ArrayList<>();
        for (AgentInstance inst : getAllInstances()) {
            if (inst.getLabel().equalsIgnoreCase(clean)) {
                matches.add(inst);
            }
        }

        if (matches.size() == 1) {
            applyFocus(matches.get(0));
            return true;
        } else if (matches.size() > 1) {
            // Ambiguous match (e.g. "main" exists on both Claude and Codex)
            AgentInstance curFocused = getFocusedInstance();
            for (AgentInstance m : matches) {
                if (curFocused != null && m.getProviderType().equalsIgnoreCase(curFocused.getProviderType())) {
                    applyFocus(m);
                    return true;
                }
            }
            // If still ambiguous, prioritize the first match and advise provider prefix
            applyFocus(matches.get(0));
            ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§7(Ambiguous target '" + query + "'. Specify e.g. /ai focus claude/" + clean + " or codex/" + clean + ")");
            return true;
        }

        ChatNotifier.sendError("Agent instance '" + query + "' not found.");
        return false;
    }

    private void applyFocus(AgentInstance inst) {
        this.focusedInstanceId = inst.getInstanceId();
        String ws = inst.isIsolated() ? " §7(isolated: " + inst.getWorktreeBranch() + ")" : " §7(shared)";
        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§bFocused Agent: §f" + inst.getAgentName() + ws + "§r");
    }

    public AgentInstance cycleNextInstance() {
        List<AgentInstance> all = getAllInstances();
        if (all.isEmpty()) return null;

        int curIdx = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getInstanceId().equalsIgnoreCase(focusedInstanceId)) {
                curIdx = i;
                break;
            }
        }

        int nextIdx = (curIdx + 1) % all.size();
        AgentInstance next = all.get(nextIdx);
        applyFocus(next);
        return next;
    }

    public AgentInstance spawnInstance(String providerType, String label, WorkspaceMode mode) {
        ProjectProfile project = ProjectManager.getInstance().getActiveProject();
        AgentInstance instance;
        if ("Codex".equalsIgnoreCase(providerType)) {
            instance = codexProvider.spawnInstance(label, project, mode);
        } else {
            instance = claudeProvider.spawnInstance(label, project, mode);
        }
        applyFocus(instance);
        String ws = instance.isIsolated() ? "isolated worktree " + instance.getWorktreeBranch() : "shared working tree";
        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§aSpawned new agent instance: §f" + instance.getAgentName() + " §7(" + ws + ")§r");
        return instance;
    }

    // Direct Authoritative AgentAdapter delegates without split-brain proxy layers
    public AgentInstance getClaudeAdapter() {
        return getFocusedClaudeInstance();
    }

    public AgentInstance getCodexAdapter() {
        return getFocusedCodexInstance();
    }

    public void executeClaude(String prompt) {
        AgentInstance inst = getFocusedClaudeInstance();
        if (inst != null) {
            inst.sendPrompt(prompt, false, false);
        } else {
            ChatNotifier.sendError("No Claude instance available.");
        }
    }

    public void executeCodex(String prompt) {
        AgentInstance inst = getFocusedCodexInstance();
        if (inst != null) {
            inst.sendPrompt(prompt, false, false);
        } else {
            ChatNotifier.sendError("No Codex instance available.");
        }
    }

    public void stop(String target) {
        if ("all".equalsIgnoreCase(target)) {
            for (AgentInstance inst : getAllInstances()) {
                inst.interrupt();
            }
            ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§6Stopped all active agent instances.§r");
        } else if (target != null && !target.isBlank()) {
            for (AgentInstance inst : getAllInstances()) {
                if (inst.getLabel().equalsIgnoreCase(target) || inst.getInstanceId().equalsIgnoreCase(target)) {
                    inst.interrupt();
                    return;
                }
            }
            ChatNotifier.sendError("Instance '" + target + "' not found to stop.");
        } else {
            // Default target: stop only the focused agent instance!
            AgentInstance focused = getFocusedInstance();
            if (focused != null) {
                focused.interrupt();
            }
        }
    }

    public void openComposer() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(new AiComposerScreen()));
        }
    }

    public void openTranscript() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(new TranscriptViewerScreen(null, getFocusedInstance())));
        }
    }

    public void openDiff() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(new DiffViewerScreen(null, getFocusedInstance())));
        }
    }

    public void openModelPicker(boolean isClaude) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            AgentInstance target = isClaude ? getFocusedClaudeInstance() : getFocusedCodexInstance();
            client.execute(() -> client.setScreen(new ModelPickerScreen(null, target)));
        }
    }

    public void openTasks() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(new TasksScreen(null)));
        }
    }

    public void shutdown() {
        for (AgentInstance inst : getAllInstances()) {
            inst.stop();
        }
    }
}
