package com.minecraftai.mod.parser;

import com.minecraftai.mod.agent.ApprovalRequest;

public class AgentEvent {
    public enum EventType {
        ACTIVITY,
        COMMAND,
        SUCCESS,
        ERROR,
        RESPONSE,
        DONE,
        SESSION_ID,
        TELEMETRY,
        APPROVAL_REQUEST,
        SUBAGENT,
        DIFF
    }

    private final EventType type;
    private final String content;
    private final String detail;
    private Object payload;

    public AgentEvent(EventType type, String content, String detail) {
        this.type = type;
        this.content = content;
        this.detail = detail;
    }

    public AgentEvent(EventType type, String content, String detail, Object payload) {
        this.type = type;
        this.content = content;
        this.detail = detail;
        this.payload = payload;
    }

    public static AgentEvent activity(String action) {
        return new AgentEvent(EventType.ACTIVITY, action, null);
    }

    public static AgentEvent command(String cmd) {
        return new AgentEvent(EventType.COMMAND, cmd, null);
    }

    public static AgentEvent success(String message) {
        return new AgentEvent(EventType.SUCCESS, message, null);
    }

    public static AgentEvent error(String error) {
        return new AgentEvent(EventType.ERROR, error, null);
    }

    public static AgentEvent response(String text) {
        return new AgentEvent(EventType.RESPONSE, text, null);
    }

    public static AgentEvent done(String summary) {
        return new AgentEvent(EventType.DONE, summary, null);
    }

    public static AgentEvent sessionId(String id) {
        return new AgentEvent(EventType.SESSION_ID, id, null);
    }

    public static AgentEvent approvalRequest(ApprovalRequest request) {
        return new AgentEvent(EventType.APPROVAL_REQUEST, request.getToolName(), request.getCommandOrDetails(), request);
    }

    public EventType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getDetail() {
        return detail;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "AgentEvent{" + "type=" + type + ", content='" + content + '\'' + '}';
    }
}
