package com.minecraftai.mod.project;

import com.minecraftai.mod.agent.AgentManager;
import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.config.ModConfig;

import java.io.File;
import java.util.Collection;

public class ProjectManager {

    private static ProjectManager instance;

    public static synchronized ProjectManager getInstance() {
        if (instance == null) {
            instance = new ProjectManager();
        }
        return instance;
    }

    public ProjectProfile getActiveProject() {
        ModConfig config = ConfigManager.getInstance().getConfig();
        String activeName = config.getActiveProjectName();
        ProjectProfile profile = config.getProjects().get(activeName.toLowerCase());
        if (profile == null) {
            profile = new ProjectProfile(activeName, config.getWorkingDirectory());
            config.getProjects().put(activeName.toLowerCase(), profile);
        }
        return profile;
    }

    public ProjectProfile getProject(String name) {
        if (name == null) return null;
        return ConfigManager.getInstance().getConfig().getProjects().get(name.trim().toLowerCase());
    }

    public Collection<ProjectProfile> getAllProjects() {
        return ConfigManager.getInstance().getConfig().getProjects().values();
    }

    public boolean addProject(String name, String directory) {
        if (name == null || name.isBlank() || directory == null || directory.isBlank()) {
            return false;
        }
        File dir = new File(directory.trim());
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        ModConfig config = ConfigManager.getInstance().getConfig();
        ProjectProfile profile = new ProjectProfile(name.trim(), dir.getAbsolutePath());
        config.getProjects().put(name.trim().toLowerCase(), profile);
        ConfigManager.getInstance().save();
        return true;
    }

    public boolean removeProject(String name) {
        if (name == null || "default".equalsIgnoreCase(name)) {
            return false;
        }
        ModConfig config = ConfigManager.getInstance().getConfig();
        boolean removed = config.getProjects().remove(name.trim().toLowerCase()) != null;
        if (removed) {
            if (name.equalsIgnoreCase(config.getActiveProjectName())) {
                config.setActiveProjectName("default");
            }
            ConfigManager.getInstance().save();
        }
        return removed;
    }

    public boolean switchProject(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        ModConfig config = ConfigManager.getInstance().getConfig();
        ProjectProfile profile = config.getProjects().get(name.trim().toLowerCase());

        // Also check directory aliases if no explicit project profile exists
        if (profile == null) {
            String aliasDir = config.getAlias(name);
            if (aliasDir != null) {
                File dir = new File(aliasDir);
                if (dir.exists() && dir.isDirectory()) {
                    profile = new ProjectProfile(name.trim(), dir.getAbsolutePath());
                    config.getProjects().put(name.trim().toLowerCase(), profile);
                }
            }
        }

        if (profile == null) {
            ChatNotifier.sendError("Project profile '" + name + "' not found. Add it with: /project add " + name + " <path>");
            return false;
        }

        File dir = new File(profile.getWorkingDirectory());
        if (!dir.exists() || !dir.isDirectory()) {
            ChatNotifier.sendError("Project directory does not exist: " + profile.getWorkingDirectory());
            return false;
        }

        config.setActiveProjectName(profile.getName());
        config.setWorkingDirectory(profile.getWorkingDirectory());
        ConfigManager.getInstance().save();

        // Restore project-specific default models & permission modes
        AgentManager agentManager = AgentManager.getInstance();
        agentManager.getClaudeAdapter().setModel(profile.getDefaultClaudeModel());
        agentManager.getClaudeAdapter().setPermissionMode(profile.getDefaultClaudePermissionMode());
        agentManager.getCodexAdapter().setModel(profile.getDefaultCodexModel());
        agentManager.getCodexAdapter().setPermissionMode(profile.getDefaultCodexPermissionMode());

        // Restore sessions if available
        if (profile.getLastClaudeSession() != null && !profile.getLastClaudeSession().isBlank()) {
            agentManager.getClaudeAdapter().setSessionId(profile.getLastClaudeSession());
        } else {
            agentManager.getClaudeAdapter().setSessionId(null);
        }

        if (profile.getLastCodexSession() != null && !profile.getLastCodexSession().isBlank()) {
            agentManager.getCodexAdapter().setSessionId(profile.getLastCodexSession());
        } else {
            agentManager.getCodexAdapter().setSessionId(null);
        }

        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§aSwitched active project to: §f" + profile.getName() +
                " §7(" + profile.getWorkingDirectory() + ")§r");
        return true;
    }

    public void updateActiveProjectSessions(String claudeSession, String codexSession) {
        ProjectProfile profile = getActiveProject();
        if (claudeSession != null) {
            profile.setLastClaudeSession(claudeSession);
        }
        if (codexSession != null) {
            profile.setLastCodexSession(codexSession);
        }
        ConfigManager.getInstance().save();
    }
}
