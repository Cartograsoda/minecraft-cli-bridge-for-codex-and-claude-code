package com.minecraftai.mod.agent;

import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.config.ModConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CapabilityDetector {

    private static CapabilityDetector instance;

    private boolean claudeAvailable = false;
    private String claudeVersion = "Unknown";
    private boolean codexAvailable = false;
    private String codexVersion = "Unknown";

    private boolean supportsResume = true;
    private boolean supportsFork = true;
    private boolean supportsBypassPermissions = true;
    private boolean supportsEffort = true;
    private boolean supportsReasoning = true;
    private boolean supportsAppServer = false;

    public static synchronized CapabilityDetector getInstance() {
        if (instance == null) {
            instance = new CapabilityDetector();
        }
        return instance;
    }

    public CompletableFuture<Void> detectAsync() {
        return CompletableFuture.runAsync(this::detect);
    }

    public synchronized void detect() {
        ModConfig config = ConfigManager.getInstance().getConfig();

        // 1. Probe Claude
        try {
            List<String> cmd = ProcessRunner.resolveCommand(List.of(config.getClaudeExecutable(), "-v"));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    claudeVersion = line.trim();
                    claudeAvailable = true;
                }
            }
            p.waitFor();
        } catch (Exception e) {
            claudeAvailable = false;
        }

        // 2. Probe Codex
        try {
            List<String> cmd = ProcessRunner.resolveCommand(List.of(config.getCodexExecutable(), "-V"));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    codexVersion = line.trim();
                    codexAvailable = true;
                }
            }
            p.waitFor();
        } catch (Exception e) {
            codexAvailable = false;
        }

        System.out.println("[ClaudeCodexChat] Capabilities probed: Claude=" + (claudeAvailable ? claudeVersion : "N/A") +
                ", Codex=" + (codexAvailable ? codexVersion : "N/A"));
    }

    public boolean isClaudeAvailable() {
        return claudeAvailable;
    }

    public String getClaudeVersion() {
        return claudeVersion;
    }

    public boolean isCodexAvailable() {
        return codexAvailable;
    }

    public String getCodexVersion() {
        return codexVersion;
    }

    public boolean isSupportsResume() {
        return supportsResume;
    }

    public boolean isSupportsFork() {
        return supportsFork;
    }

    public boolean isSupportsBypassPermissions() {
        return supportsBypassPermissions;
    }

    public boolean isSupportsEffort() {
        return supportsEffort;
    }

    public boolean isSupportsReasoning() {
        return supportsReasoning;
    }

    public boolean isSupportsAppServer() {
        return supportsAppServer;
    }
}
