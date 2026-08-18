package com.minecraftai.mod.agent;

import com.minecraftai.mod.codex.CodexAppServerClient;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.workspace.WorkspaceMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodexProvider {

    private final Map<String, AgentInstance> instances = Collections.synchronizedMap(new LinkedHashMap<>());

    public AgentInstance spawnInstance(String label, ProjectProfile project, WorkspaceMode mode) {
        String cleanLabel = label.trim().toLowerCase();
        String id = "codex/" + cleanLabel;

        // Clean up previous instance with same label if existing
        AgentInstance existing = instances.remove(cleanLabel);
        if (existing != null) {
            existing.stop();
        }

        AgentInstance instance = new AgentInstance(id, cleanLabel, "Codex", project, mode);
        instances.put(cleanLabel, instance);
        return instance;
    }

    public AgentInstance getInstance(String label) {
        if (label == null) return null;
        return instances.get(label.trim().toLowerCase());
    }

    public List<AgentInstance> getAllInstances() {
        return new ArrayList<>(instances.values());
    }

    public boolean removeInstance(String label) {
        if (label == null) return false;
        AgentInstance instance = instances.remove(label.trim().toLowerCase());
        if (instance != null) {
            instance.stop();
            return true;
        }
        return false;
    }

    public ProviderTelemetrySnapshot getTelemetrySnapshot() {
        return CodexAppServerClient.getInstance().getTelemetrySnapshot();
    }

    public double getAccount5HourRemaining() {
        return CodexAppServerClient.getInstance().getFiveHourRemainingPercent();
    }

    public double getAccountWeeklyRemaining() {
        return CodexAppServerClient.getInstance().getWeeklyRemainingPercent();
    }

    public int getResetCredits() {
        return CodexAppServerClient.getInstance().getResetCredits();
    }
}
