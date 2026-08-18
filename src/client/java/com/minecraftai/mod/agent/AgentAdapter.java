package com.minecraftai.mod.agent;

import java.util.List;

public interface AgentAdapter {
    String getAgentName();

    void sendPrompt(String prompt, boolean steer, boolean queue);
    void startPrompt(String prompt);
    void interrupt();
    void stop();

    void newSession();
    void resumeSession(String id, String title);
    void forkSession(String id);
    void forkFromTurn(String id, String turnId);

    AgentStatus getStatus();
    AgentTelemetry getTelemetry();
    String getSessionId();
    void setSessionId(String sessionId);

    String getModel();
    void setModel(String model);

    String getEffort();
    void setEffort(String effort);

    String getPermissionMode();
    void setPermissionMode(String mode);
    void cyclePermissionMode();

    void approve(String approvalId, boolean forSession);
    void deny(String approvalId);
    void compact();

    List<String> getPromptQueue();
    void removeQueuedPrompt(int index);
    String takeBackQueuedPrompt(int index);

    List<TurnInfo> getTurnHistory();
    void rewindToCheckpoint(String turnId);

    String getAwayRecap();
    void clearAwayRecap();

    List<String> getTranscript();
    String getLatestDiff();
}
