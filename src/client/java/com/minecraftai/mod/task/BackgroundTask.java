package com.minecraftai.mod.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackgroundTask {

    public enum Type {
        BASH,
        SUBAGENT,
        DEV_SERVER,
        TEST_RUNNER
    }

    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED,
        STOPPED
    }

    private static final int MAX_MEMORY_LINES = 2000;

    private final String id;
    private final String name;
    private final String commandOrGoal;
    private final Type type;
    private final String ownerAgent;
    private final Instant startTime;
    private Instant endTime;
    private volatile Status status;
    private int exitCode = -1;
    private final List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
    private Process process;
    private File logFile;
    private BufferedWriter logWriter;

    public BackgroundTask(String id, String name, String commandOrGoal, Type type) {
        this(id, name, commandOrGoal, type, "global");
    }

    public BackgroundTask(String id, String name, String commandOrGoal, Type type, String ownerAgent) {
        this.id = id;
        this.name = name;
        this.commandOrGoal = commandOrGoal;
        this.type = type;
        this.ownerAgent = ownerAgent != null ? ownerAgent : "global";
        this.startTime = Instant.now();
        this.status = Status.RUNNING;
        initLogFile();
    }

    private void initLogFile() {
        try {
            File logsDir = new File("logs/tasks");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            this.logFile = new File(logsDir, id + ".log");
            this.logWriter = new BufferedWriter(new FileWriter(logFile, StandardCharsets.UTF_8, true));
            logWriter.write("=== Task " + id + " (" + name + ") started at " + startTime + " ===\n");
            logWriter.write("Command: " + commandOrGoal + "\n\n");
            logWriter.flush();
        } catch (Exception e) {
            System.err.println("[BackgroundTask] Failed to create log file for " + id + ": " + e.getMessage());
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCommandOrGoal() {
        return commandOrGoal;
    }

    public Type getType() {
        return type;
    }

    public String getOwnerAgent() {
        return ownerAgent;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status != Status.RUNNING && this.endTime == null) {
            this.endTime = Instant.now();
            closeLogWriter();
        }
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public Process getProcess() {
        return process;
    }

    public File getLogFile() {
        return logFile;
    }

    public void addOutput(String line) {
        if (line == null) return;

        outputLines.add(line);
        if (outputLines.size() > MAX_MEMORY_LINES) {
            outputLines.remove(0);
        }

        if (logWriter != null) {
            try {
                logWriter.write(line);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException ignored) {
            }
        }
    }

    public List<String> getOutputLines() {
        return new ArrayList<>(outputLines);
    }

    public long getElapsedSeconds() {
        Instant end = (endTime != null) ? endTime : Instant.now();
        return Duration.between(startTime, end).getSeconds();
    }

    public String getFormattedElapsed() {
        long sec = getElapsedSeconds();
        if (sec < 60) return sec + "s";
        long min = sec / 60;
        long remSec = sec % 60;
        if (min < 60) return min + "m " + remSec + "s";
        long hr = min / 60;
        long remMin = min % 60;
        return hr + "h " + remMin + "m";
    }

    private synchronized void closeLogWriter() {
        if (logWriter != null) {
            try {
                logWriter.write("\n=== Task finished at " + Instant.now() + " with exit code " + exitCode + " (status: " + status + ") ===\n");
                logWriter.flush();
                logWriter.close();
            } catch (Exception ignored) {
            }
            logWriter = null;
        }
    }

    public void stop() {
        if (process != null && process.isAlive()) {
            try {
                // Recursively terminate all descendant child processes (cmd.exe children on Windows, subshells on Unix)
                ProcessHandle handle = process.toHandle();
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                handle.destroyForcibly();
            } catch (Throwable t) {
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {
                }
            }
        }
        setStatus(Status.STOPPED);
        closeLogWriter();
    }
}
