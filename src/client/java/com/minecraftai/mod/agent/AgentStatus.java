package com.minecraftai.mod.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentStatus {
    public enum State {
        ERROR,
        WAITING_INPUT,
        STOPPING,
        PLAN_MODE,
        RUNNING,
        IDLE
    }

    private State state = State.IDLE;
    private long startTime = 0;
    private String lastError = null;
    private String currentActivity = "";
    private String currentTool = "";
    private final List<SubagentInfo> subagents = new ArrayList<>();
    private final List<ChangedFileInfo> changedFiles = new ArrayList<>();
    private ApprovalRequest pendingApproval = null;

    public synchronized State getState() {
        return state;
    }

    public synchronized void setRunning(String activity) {
        this.state = State.RUNNING;
        this.startTime = System.currentTimeMillis();
        this.currentActivity = activity != null ? activity : "Working...";
        this.lastError = null;
        this.pendingApproval = null;
    }

    public synchronized void setIdle() {
        this.state = State.IDLE;
        this.startTime = 0;
        this.currentActivity = "";
        this.currentTool = "";
        this.pendingApproval = null;
    }

    public synchronized void setWaitingInput(ApprovalRequest request) {
        this.state = State.WAITING_INPUT;
        this.pendingApproval = request;
    }

    public synchronized void setPlanMode() {
        this.state = State.PLAN_MODE;
    }

    public synchronized void setStopping() {
        this.state = State.STOPPING;
    }

    public synchronized void setError(String error) {
        this.state = State.ERROR;
        this.lastError = error;
    }

    public synchronized boolean isError() {
        return state == State.ERROR;
    }

    public synchronized boolean isWaitingInput() {
        return state == State.WAITING_INPUT;
    }

    public synchronized boolean isRunning() {
        return state == State.RUNNING || state == State.PLAN_MODE;
    }

    public synchronized boolean isIdle() {
        return state == State.IDLE;
    }

    public synchronized long getElapsedSeconds() {
        if ((isRunning() || isWaitingInput()) && startTime > 0) {
            return (System.currentTimeMillis() - startTime) / 1000;
        }
        return 0;
    }

    public synchronized String getLastError() {
        return lastError;
    }

    public synchronized String getCurrentActivity() {
        return currentActivity;
    }

    public synchronized void setCurrentActivity(String currentActivity) {
        this.currentActivity = currentActivity;
    }

    public synchronized String getCurrentTool() {
        return currentTool;
    }

    public synchronized void setCurrentTool(String currentTool) {
        this.currentTool = currentTool;
    }

    public synchronized List<SubagentInfo> getSubagents() {
        return new ArrayList<>(subagents);
    }

    public synchronized void addOrUpdateSubagent(String name, String task, SubagentInfo.Status status) {
        for (SubagentInfo s : subagents) {
            if (s.getName().equals(name)) {
                s.setStatus(status);
                return;
            }
        }
        subagents.add(new SubagentInfo(name, task, status));
    }

    public synchronized void clearSubagents() {
        subagents.clear();
    }

    public synchronized List<ChangedFileInfo> getChangedFiles() {
        return new ArrayList<>(changedFiles);
    }

    public synchronized void addChangedFile(String path, ChangedFileInfo.ChangeType type, int adds, int dels) {
        changedFiles.add(new ChangedFileInfo(path, type, adds, dels));
    }

    public synchronized void clearChangedFiles() {
        changedFiles.clear();
    }

    public synchronized ApprovalRequest getPendingApproval() {
        return pendingApproval;
    }

    public synchronized void setPendingApproval(ApprovalRequest pendingApproval) {
        this.pendingApproval = pendingApproval;
    }

    public synchronized String getStatusDot() {
        if (state == State.ERROR) return "§c✗";
        if (state == State.WAITING_INPUT) return "§e⚠";
        if (state == State.STOPPING) return "§6■";
        if (state == State.PLAN_MODE) return "§d◇";
        if (state == State.RUNNING) return "§a●";
        return "§7○";
    }

    public synchronized String getStatusLabel() {
        if (state == State.ERROR) return "error (" + (lastError != null ? lastError : "unknown") + ")";
        if (state == State.WAITING_INPUT) return "waiting for approval";
        if (state == State.STOPPING) return "stopping...";
        if (state == State.PLAN_MODE) return "plan mode";
        if (state == State.RUNNING) return !currentActivity.isBlank() ? currentActivity : "running...";
        return "idle";
    }

    public synchronized String formatStatus(String agentName, String sessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append(agentName).append(": ");
        if (state == State.ERROR) {
            sb.append("§cerror (").append(lastError != null ? lastError : "unknown").append(")§r");
        } else if (state == State.WAITING_INPUT) {
            sb.append("§e⚠ WAITING FOR APPROVAL§r");
        } else if (state == State.STOPPING) {
            sb.append("§6stopping§r");
        } else if (state == State.PLAN_MODE) {
            sb.append("§d◇ PLAN MODE§r");
        } else if (state == State.RUNNING) {
            sb.append("§erunning — ").append(getElapsedSeconds()).append("s§r");
            if (!currentActivity.isBlank()) {
                sb.append(" §7(").append(currentActivity).append(")§r");
            }
        } else {
            sb.append("§aidle§r");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            sb.append(" §7(session: ").append(sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId).append(")§r");
        }
        return sb.toString();
    }
}
