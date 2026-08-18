package com.minecraftai.mod.project;

public class ProjectProfile {
    private String name;
    private String workingDirectory;
    private String lastClaudeSession;
    private String lastCodexSession;
    private String defaultClaudeModel = "claude-opus-5";
    private String defaultCodexModel = "gpt-5.x";
    private String defaultClaudePermissionMode = "plan";
    private String defaultCodexPermissionMode = "workspace-write";
    private String defaultEffort = "xhigh";

    public ProjectProfile() {
    }

    public ProjectProfile(String name, String workingDirectory) {
        this.name = name;
        this.workingDirectory = workingDirectory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getLastClaudeSession() {
        return lastClaudeSession;
    }

    public void setLastClaudeSession(String lastClaudeSession) {
        this.lastClaudeSession = lastClaudeSession;
    }

    public String getLastCodexSession() {
        return lastCodexSession;
    }

    public void setLastCodexSession(String lastCodexSession) {
        this.lastCodexSession = lastCodexSession;
    }

    public String getDefaultClaudeModel() {
        return defaultClaudeModel != null ? defaultClaudeModel : "claude-opus-5";
    }

    public void setDefaultClaudeModel(String defaultClaudeModel) {
        this.defaultClaudeModel = defaultClaudeModel;
    }

    public String getDefaultCodexModel() {
        return defaultCodexModel != null ? defaultCodexModel : "gpt-5.x";
    }

    public void setDefaultCodexModel(String defaultCodexModel) {
        this.defaultCodexModel = defaultCodexModel;
    }

    public String getDefaultClaudePermissionMode() {
        return defaultClaudePermissionMode != null ? defaultClaudePermissionMode : "plan";
    }

    public void setDefaultClaudePermissionMode(String defaultClaudePermissionMode) {
        this.defaultClaudePermissionMode = defaultClaudePermissionMode;
    }

    public String getDefaultCodexPermissionMode() {
        return defaultCodexPermissionMode != null ? defaultCodexPermissionMode : "workspace-write";
    }

    public void setDefaultCodexPermissionMode(String defaultCodexPermissionMode) {
        this.defaultCodexPermissionMode = defaultCodexPermissionMode;
    }

    public String getDefaultEffort() {
        return defaultEffort != null ? defaultEffort : "xhigh";
    }

    public void setDefaultEffort(String defaultEffort) {
        this.defaultEffort = defaultEffort;
    }
}
