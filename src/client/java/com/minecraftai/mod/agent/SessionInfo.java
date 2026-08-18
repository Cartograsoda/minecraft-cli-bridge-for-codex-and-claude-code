package com.minecraftai.mod.agent;

import java.util.ArrayList;
import java.util.List;

public class SessionInfo {
    private final String agent; // "Claude" or "Codex"
    private final String id;
    private String title;
    private final long lastModified;
    private final String project;
    private boolean pinned = false;
    private boolean archived = false;
    private final List<TurnInfo> turns = new ArrayList<>();

    public SessionInfo(String agent, String id, String title, long lastModified, String project) {
        this.agent = agent;
        this.id = id;
        this.title = title != null && !title.isBlank() ? title : "Untitled Session";
        this.lastModified = lastModified;
        this.project = project;
    }

    public String getAgent() {
        return agent;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getProject() {
        return project;
    }

    public String getShortId() {
        return id != null && id.length() > 8 ? id.substring(0, 8) : id;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public List<TurnInfo> getTurns() {
        return turns;
    }

    public void addTurn(TurnInfo turn) {
        turns.add(turn);
    }
}
