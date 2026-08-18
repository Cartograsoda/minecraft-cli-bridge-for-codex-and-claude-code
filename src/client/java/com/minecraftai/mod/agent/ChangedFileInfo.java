package com.minecraftai.mod.agent;

public class ChangedFileInfo {
    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }

    private final String path;
    private final ChangeType type;
    private final int additions;
    private final int deletions;

    public ChangedFileInfo(String path, ChangeType type, int additions, int deletions) {
        this.path = path;
        this.type = type;
        this.additions = additions;
        this.deletions = deletions;
    }

    public String getPath() {
        return path;
    }

    public ChangeType getType() {
        return type;
    }

    public int getAdditions() {
        return additions;
    }

    public int getDeletions() {
        return deletions;
    }

    public String getSymbol() {
        switch (type) {
            case ADDED: return "+";
            case DELETED: return "-";
            case MODIFIED: default: return "~";
        }
    }
}
