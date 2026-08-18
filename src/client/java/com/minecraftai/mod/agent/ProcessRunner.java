package com.minecraftai.mod.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ProcessRunner {

    private static final Set<Process> ACTIVE_PROCESSES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "AI-ProcessRunner-Worker");
        t.setDaemon(true);
        return t;
    });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process p : ACTIVE_PROCESSES) {
                try {
                    p.destroyForcibly();
                } catch (Throwable ignored) {
                }
            }
        }, "AI-ProcessRunner-ShutdownHook"));
    }

    private Process process;
    private final List<String> command;
    private final File workingDirectory;
    private final Consumer<String> stdoutConsumer;
    private final Consumer<String> stderrConsumer;
    private final Consumer<Integer> exitConsumer;

    public ProcessRunner(
            List<String> command,
            File workingDirectory,
            Consumer<String> stdoutConsumer,
            Consumer<String> stderrConsumer,
            Consumer<Integer> exitConsumer
    ) {
        this.command = new ArrayList<>(command);
        this.workingDirectory = workingDirectory;
        this.stdoutConsumer = stdoutConsumer;
        this.stderrConsumer = stderrConsumer;
        this.exitConsumer = exitConsumer;
    }

    public synchronized void start() throws Exception {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        // Resolve executable on Windows if necessary
        List<String> resolvedCommand = resolveCommand(command);

        ProcessBuilder pb = new ProcessBuilder(resolvedCommand);
        if (workingDirectory != null && workingDirectory.exists() && workingDirectory.isDirectory()) {
            pb.directory(workingDirectory);
        }

        // Redirect input from null / closed stream to avoid stdin blocking
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        this.process = pb.start();
        ACTIVE_PROCESSES.add(this.process);

        // Close stdin immediately
        try {
            process.getOutputStream().close();
        } catch (Exception ignored) {
        }

        // Read stdout
        EXECUTOR.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdoutConsumer != null) {
                        stdoutConsumer.accept(line);
                    }
                }
            } catch (Exception ignored) {
            }
        });

        // Read stderr
        EXECUTOR.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderrConsumer != null) {
                        stderrConsumer.accept(line);
                    }
                }
            } catch (Exception ignored) {
            }
        });

        // Monitor process exit
        EXECUTOR.submit(() -> {
            int exitCode = -1;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                exitCode = -1;
            } finally {
                ACTIVE_PROCESSES.remove(process);
                if (exitConsumer != null) {
                    exitConsumer.accept(exitCode);
                }
            }
        });
    }

    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            ACTIVE_PROCESSES.remove(process);
            try {
                // Attempt graceful termination
                process.destroy();
                EXECUTOR.submit(() -> {
                    try {
                        if (!process.waitFor(2, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                    } catch (Exception e) {
                        process.destroyForcibly();
                    }
                });
            } catch (Exception e) {
                process.destroyForcibly();
            }
        }
    }

    public synchronized boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Resolves command executables on Windows (e.g. codex -> codex.cmd / claude -> claude.exe)
     */
    public static List<String> resolveCommand(List<String> rawCommand) {
        if (rawCommand == null || rawCommand.isEmpty()) {
            return rawCommand;
        }

        List<String> result = new ArrayList<>(rawCommand);
        String executable = result.get(0);

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) {
            return result;
        }

        File directFile = new File(executable);
        if (directFile.exists() && directFile.isFile()) {
            return result;
        }

        // Check if executable already has extension
        String lower = executable.toLowerCase();
        if (lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat")) {
            return result;
        }

        // Search PATH for executable with extensions
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] extensions = {".exe", ".cmd", ".bat"};
            for (String dir : pathEnv.split(File.pathSeparator)) {
                for (String ext : extensions) {
                    File candidate = new File(dir, executable + ext);
                    if (candidate.exists() && candidate.isFile()) {
                        result.set(0, candidate.getAbsolutePath());
                        return result;
                    }
                }
            }
        }

        return result;
    }
}
