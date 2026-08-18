package com.minecraftai.mod.agent;

public class ApprovalRequest {
    private final String id;
    private final String agentName;
    private final String toolName;
    private final String commandOrDetails;
    private final long timestamp;

    public ApprovalRequest(String id, String agentName, String toolName, String commandOrDetails) {
        this.id = id;
        this.agentName = agentName;
        this.toolName = toolName;
        this.commandOrDetails = commandOrDetails;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getToolName() {
        return toolName;
    }

    public String getCommandOrDetails() {
        return commandOrDetails;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
