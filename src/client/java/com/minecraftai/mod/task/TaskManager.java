package com.minecraftai.mod.task;

import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskManager {

    private static TaskManager instance;
    private final Map<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public static synchronized TaskManager getInstance() {
        if (instance == null) {
            instance = new TaskManager();
        }
        return instance;
    }

    public BackgroundTask launchShellTask(String command, File workingDir) {
        return launchShellTask(command, workingDir, "global");
    }

    public BackgroundTask launchShellTask(String command, File workingDir, String ownerAgent) {
        String id = "task-" + idCounter.getAndIncrement();
        String name = command.length() > 24 ? command.substring(0, 21) + "..." : command;
        BackgroundTask.Type type = BackgroundTask.Type.BASH;
        if (command.contains("test") || command.contains("pytest") || command.contains("gradlew test")) {
            type = BackgroundTask.Type.TEST_RUNNER;
        } else if (command.contains("dev") || command.contains("start") || command.contains("serve")) {
            type = BackgroundTask.Type.DEV_SERVER;
        }

        BackgroundTask task = new BackgroundTask(id, name, command, type, ownerAgent);
        tasks.put(id, task);

        Thread thread = new Thread(() -> {
            try {
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                ProcessBuilder pb;
                if (isWindows) {
                    pb = new ProcessBuilder("cmd.exe", "/c", command);
                } else {
                    pb = new ProcessBuilder("bash", "-c", command);
                }

                if (workingDir != null && workingDir.exists()) {
                    pb.directory(workingDir);
                }
                pb.redirectErrorStream(true);
                Process process = pb.start();
                task.setProcess(process);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        task.addOutput(line);
                    }
                }

                int code = process.waitFor();
                task.setExitCode(code);
                task.setStatus(code == 0 ? BackgroundTask.Status.SUCCESS : BackgroundTask.Status.FAILED);

                ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§7Background task §f" + task.getName() + "§7 finished with code §e" + code + "§r");
            } catch (Exception e) {
                task.setStatus(BackgroundTask.Status.FAILED);
                task.addOutput("Error: " + e.getMessage());
            }
        }, "BgTask-" + id);
        thread.setDaemon(true);
        thread.start();

        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§aStarted background task: §f" + task.getName() + " §7(ID: §e" + id + "§7)");
        return task;
    }

    public BackgroundTask registerSubagentTask(String name, String goal) {
        return registerSubagentTask(name, goal, "global");
    }

    public BackgroundTask registerSubagentTask(String name, String goal, String ownerAgent) {
        String id = "agent-" + idCounter.getAndIncrement();
        BackgroundTask task = new BackgroundTask(id, name, goal, BackgroundTask.Type.SUBAGENT, ownerAgent);
        tasks.put(id, task);
        return task;
    }

    public BackgroundTask getTask(String id) {
        return tasks.get(id);
    }

    public List<BackgroundTask> getAllTasks() {
        List<BackgroundTask> list = new ArrayList<>(tasks.values());
        list.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
        return list;
    }

    public List<BackgroundTask> getRunningTasks() {
        List<BackgroundTask> list = new ArrayList<>();
        for (BackgroundTask t : tasks.values()) {
            if (t.getStatus() == BackgroundTask.Status.RUNNING) {
                list.add(t);
            }
        }
        return list;
    }

    public int getRunningCount() {
        int count = 0;
        for (BackgroundTask t : tasks.values()) {
            if (t.getStatus() == BackgroundTask.Status.RUNNING) {
                count++;
            }
        }
        return count;
    }

    public boolean stopTask(String id) {
        BackgroundTask task = tasks.get(id);
        if (task != null) {
            task.stop();
            ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§cStopped task: §f" + task.getName());
            return true;
        }
        return false;
    }

    public void stopAll() {
        for (BackgroundTask t : tasks.values()) {
            if (t.getStatus() == BackgroundTask.Status.RUNNING) {
                t.stop();
            }
        }
        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§cStopped all background tasks.");
    }

    public void shutdown() {
        for (BackgroundTask t : tasks.values()) {
            if (t.getStatus() == BackgroundTask.Status.RUNNING) {
                t.stop();
            }
        }
    }
}
