package com.minecraftai.mod.agent;

import java.time.Instant;

public class TurnInfo {

    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED
    }

    private final String turnId;
    private final String prompt;
    private final String summary;
    private final Instant timestamp;
    private final String gitCheckpointCommit;
    private Status status;

    public TurnInfo(String turnId, String prompt, String summary, String gitCheckpointCommit) {
        this.turnId = turnId;
        this.prompt = prompt;
        this.summary = summary;
        this.timestamp = Instant.now();
        this.gitCheckpointCommit = gitCheckpointCommit;
        this.status = Status.RUNNING;
    }

    public String getTurnId() {
        return turnId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getGitCheckpointCommit() {
        return gitCheckpointCommit;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
