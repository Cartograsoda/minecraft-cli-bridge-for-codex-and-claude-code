package com.minecraftai.mod.shell;

import com.minecraftai.mod.agent.AgentInstance;
import com.minecraftai.mod.agent.AgentManager;
import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.task.TaskManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ShellExecutor {

    public static void executeCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) return;
        String cmd = commandLine.startsWith("!") ? commandLine.substring(1).trim() : commandLine.trim();
        if (cmd.isEmpty()) return;

        AgentInstance focused = AgentManager.getInstance().getFocusedInstance();
        ProjectProfile active = ProjectManager.getInstance().getActiveProject();

        File workingDir;
        if (focused != null && focused.getWorkingDirectory() != null && focused.getWorkingDirectory().exists()) {
            workingDir = focused.getWorkingDirectory();
        } else {
            String dirPath = active != null ? active.getWorkingDirectory() : ConfigManager.getInstance().getConfig().getWorkingDirectory();
            workingDir = new File(dirPath);
        }

        String ownerName = focused != null ? focused.getAgentName() : "global";

        // Check if background task requested with trailing &
        if (cmd.endsWith("&")) {
            String bgCmd = cmd.substring(0, cmd.length() - 1).trim();
            TaskManager.getInstance().launchShellTask(bgCmd, workingDir, ownerName);
            return;
        }

        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§6$ " + cmd + " §7(" + workingDir.getName() + ")§r");

        Thread thread = new Thread(() -> {
            try {
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                ProcessBuilder pb = isWindows ?
                        new ProcessBuilder("cmd.exe", "/c", cmd) :
                        new ProcessBuilder("bash", "-c", cmd);

                if (workingDir.exists()) {
                    pb.directory(workingDir);
                }
                pb.redirectErrorStream(true);
                Process process = pb.start();

                int lineCount = 0;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        if (lineCount <= 12) {
                            ChatNotifier.sendMessage("§7" + line + "§r");
                        }
                        if (focused != null) {
                            focused.addTranscript("[$ " + cmd + "] " + line);
                        }
                    }
                }

                int code = process.waitFor();
                if (lineCount > 12) {
                    ChatNotifier.sendMessage("§8... (" + (lineCount - 12) + " more lines in /ai transcript)§r");
                }
                if (code != 0) {
                    ChatNotifier.sendError("Command exited with code " + code);
                } else {
                    ChatNotifier.sendFeedback("§aCommand completed successfully.§r");
                }
            } catch (Exception e) {
                ChatNotifier.sendError("Shell execution error: " + e.getMessage());
            }
        }, "ShellExecThread");
        thread.setDaemon(true);
        thread.start();
    }
}
