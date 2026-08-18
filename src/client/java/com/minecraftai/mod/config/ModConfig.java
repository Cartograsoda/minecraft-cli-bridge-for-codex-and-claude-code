package com.minecraftai.mod.config;

import com.minecraftai.mod.hud.HudConfig;
import com.minecraftai.mod.project.ProjectProfile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ModConfig {
    private String workingDirectory;
    private String claudeExecutable = "claude";
    private String codexExecutable = "codex";
    private boolean showActivityEvents = true;
    private boolean showToolCommands = true;
    private String chatPrefix = "";
    private int maxChatLineLength = 120;
    private String outputVerbosity = "normal"; // "minimal", "normal", "verbose"

    private String activeProjectName = "default";
    private Map<String, ProjectProfile> projects = new HashMap<>();
    private Map<String, String> directoryAliases = new HashMap<>();

    private HudConfig hud = new HudConfig();

    public ModConfig() {
        String defaultDir = System.getProperty("user.dir");
        if (defaultDir == null || defaultDir.isBlank()) {
            defaultDir = System.getProperty("user.home");
        }
        this.workingDirectory = defaultDir;
        getProjects().put("default", new ProjectProfile("default", defaultDir));
    }

    public String getWorkingDirectory() {
        if (workingDirectory == null || workingDirectory.isBlank()) {
            workingDirectory = System.getProperty("user.home");
        }
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getClaudeExecutable() {
        return claudeExecutable != null && !claudeExecutable.isBlank() ? claudeExecutable : "claude";
    }

    public void setClaudeExecutable(String claudeExecutable) {
        this.claudeExecutable = claudeExecutable;
    }

    public String getCodexExecutable() {
        return codexExecutable != null && !codexExecutable.isBlank() ? codexExecutable : "codex";
    }

    public void setCodexExecutable(String codexExecutable) {
        this.codexExecutable = codexExecutable;
    }

    public boolean isShowActivityEvents() {
        return showActivityEvents;
    }

    public void setShowActivityEvents(boolean showActivityEvents) {
        this.showActivityEvents = showActivityEvents;
    }

    public boolean isShowToolCommands() {
        return showToolCommands;
    }

    public void setShowToolCommands(boolean showToolCommands) {
        this.showToolCommands = showToolCommands;
    }

    public String getChatPrefix() {
        return chatPrefix != null ? chatPrefix : "";
    }

    public void setChatPrefix(String chatPrefix) {
        this.chatPrefix = chatPrefix;
    }

    public int getMaxChatLineLength() {
        return maxChatLineLength > 20 ? maxChatLineLength : 120;
    }

    public void setMaxChatLineLength(int maxChatLineLength) {
        this.maxChatLineLength = maxChatLineLength;
    }

    public String getOutputVerbosity() {
        return outputVerbosity != null ? outputVerbosity : "normal";
    }

    public void setOutputVerbosity(String outputVerbosity) {
        this.outputVerbosity = outputVerbosity;
    }

    public String getActiveProjectName() {
        return activeProjectName != null ? activeProjectName : "default";
    }

    public void setActiveProjectName(String activeProjectName) {
        this.activeProjectName = activeProjectName;
    }

    public Map<String, ProjectProfile> getProjects() {
        if (projects == null) {
            projects = new HashMap<>();
        }
        return projects;
    }

    public void setProjects(Map<String, ProjectProfile> projects) {
        this.projects = projects;
    }

    public Map<String, String> getDirectoryAliases() {
        if (directoryAliases == null) {
            directoryAliases = new HashMap<>();
        }
        return directoryAliases;
    }

    public void setDirectoryAliases(Map<String, String> directoryAliases) {
        this.directoryAliases = directoryAliases;
    }

    public void addAlias(String name, String path) {
        if (name != null && path != null) {
            getDirectoryAliases().put(name.trim().toLowerCase(), path.trim());
        }
    }

    public boolean removeAlias(String name) {
        if (name == null) return false;
        return getDirectoryAliases().remove(name.trim().toLowerCase()) != null;
    }

    public String getAlias(String name) {
        if (name == null) return null;
        return getDirectoryAliases().get(name.trim().toLowerCase());
    }

    public String resolveDirectory(String pathOrAlias) {
        if (pathOrAlias == null || pathOrAlias.isBlank()) {
            return null;
        }
        String clean = pathOrAlias.trim();
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
            clean = clean.substring(1, clean.length() - 1);
        }

        String aliasTarget = getAlias(clean);
        if (aliasTarget != null) {
            return aliasTarget;
        }

        ProjectProfile profile = getProjects().get(clean.toLowerCase());
        if (profile != null && profile.getWorkingDirectory() != null) {
            return profile.getWorkingDirectory();
        }

        return clean;
    }

    public HudConfig getHud() {
        if (hud == null) {
            hud = new HudConfig();
        }
        return hud;
    }

    public void setHud(HudConfig hud) {
        this.hud = hud;
    }

    public boolean isValidDirectory(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            File file = new File(path);
            return file.exists() && file.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }
}
