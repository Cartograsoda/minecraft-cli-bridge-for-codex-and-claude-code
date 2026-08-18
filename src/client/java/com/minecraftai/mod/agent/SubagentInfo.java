package com.minecraftai.mod.agent;

public class SubagentInfo {
    public enum Status {
        RUNNING,
        DONE,
        ERROR
    }

    private final String name;
    private final String task;
    private Status status;

    public SubagentInfo(String name, String task, Status status) {
        this.name = name;
        this.task = task;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public String getTask() {
        return task;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
