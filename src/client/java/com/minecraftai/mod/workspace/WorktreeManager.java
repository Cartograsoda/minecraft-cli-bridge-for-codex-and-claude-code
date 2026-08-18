package com.minecraftai.mod.workspace;

import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class WorktreeManager {

    private static WorktreeManager instance;

    public static synchronized WorktreeManager getInstance() {
        if (instance == null) {
            instance = new WorktreeManager();
        }
        return instance;
    }

    public boolean isGitRepository(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        File gitDir = new File(dir, ".git");
        return gitDir.exists();
    }

    public static String sanitizeLabel(String label) {
        if (label == null || label.isBlank()) return "main";
        return label.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }

    public static String getBranchName(String providerType, String label) {
        String p = (providerType != null && !providerType.isBlank()) ? providerType.trim().toLowerCase() : "agent";
        return "ai/" + p + "-" + sanitizeLabel(label);
    }

    public File createWorktree(File baseRepo, String label) {
        return createWorktree(baseRepo, "agent", label);
    }

    /**
     * Creates an isolated Git worktree for an agent.
     * FAILS CLOSED: If creation fails or repository is invalid, returns null (never silently returns base repo).
     */
    public File createWorktree(File baseRepo, String providerType, String label) {
        if (!isGitRepository(baseRepo)) {
            ChatNotifier.sendError("Worktree isolation failed: " + baseRepo.getPath() + " is not a valid Git repository.");
            return null;
        }

        String cleanLabel = sanitizeLabel(label);
        String branchName = getBranchName(providerType, cleanLabel);

        try {
            File worktreesRoot = new File(baseRepo, ".minecraft-ai/worktrees");
            if (!worktreesRoot.exists() && !worktreesRoot.mkdirs()) {
                ChatNotifier.sendError("Failed to create worktree directory structure in " + baseRepo.getPath());
                return null;
            }

            File targetWorktreeDir = new File(worktreesRoot, providerType.toLowerCase() + "-" + cleanLabel);

            // If worktree already exists and is a valid directory, return it
            if (targetWorktreeDir.exists() && targetWorktreeDir.isDirectory()) {
                return targetWorktreeDir;
            }

            // Check if branch already exists
            boolean branchExists = runGitCommand(baseRepo, "rev-parse", "--verify", branchName) == 0;

            int code;
            if (branchExists) {
                // Checkout existing branch into new worktree
                code = runGitCommand(baseRepo, "worktree", "add", targetWorktreeDir.getAbsolutePath(), branchName);
            } else {
                // Create new branch and worktree
                code = runGitCommand(baseRepo, "worktree", "add", "-b", branchName, targetWorktreeDir.getAbsolutePath());
            }

            if (code == 0 && targetWorktreeDir.exists()) {
                ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§aCreated isolated Git worktree: §f" + branchName + " §7in " + targetWorktreeDir.getName() + "§r");
                return targetWorktreeDir;
            } else {
                ChatNotifier.sendError("Git worktree command failed with exit code " + code);
            }
        } catch (Exception e) {
            ChatNotifier.sendError("Failed to create worktree: " + e.getMessage());
        }
        return null;
    }

    public boolean applyToMain(File baseRepo, String label) {
        return applyToMain(baseRepo, "agent", label);
    }

    public boolean applyToMain(File baseRepo, String providerType, String label) {
        if (!isGitRepository(baseRepo)) return false;
        String cleanLabel = sanitizeLabel(label);
        String branchName = getBranchName(providerType, cleanLabel);
        File targetWorktreeDir = new File(baseRepo, ".minecraft-ai/worktrees/" + providerType.toLowerCase() + "-" + cleanLabel);

        try {
            // 1. If worktree has uncommitted changes, commit them to the branch first
            if (targetWorktreeDir.exists() && targetWorktreeDir.isDirectory()) {
                String status = getCommandOutput(targetWorktreeDir, "status", "--porcelain");
                if (!status.isBlank()) {
                    runGitCommand(targetWorktreeDir, "add", "-A");
                    runGitCommand(targetWorktreeDir, "commit", "-m", "Worktree snapshot: " + branchName);
                }
            }

            // 2. Merge branch into base repo
            ProcessBuilder pb = new ProcessBuilder("git", "merge", branchName, "--no-edit");
            pb.directory(baseRepo);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            int code = p.waitFor();
            if (code == 0) {
                ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§aSuccessfully merged branch §f" + branchName + "§a into main!§r");
                return true;
            } else {
                ChatNotifier.sendError("Git merge conflict/error:\n" + output);
            }
        } catch (Exception e) {
            ChatNotifier.sendError("Failed to merge worktree branch: " + e.getMessage());
        }
        return false;
    }

    public boolean removeWorktree(File baseRepo, String label) {
        return removeWorktree(baseRepo, "agent", label);
    }

    public boolean removeWorktree(File baseRepo, String providerType, String label) {
        if (!isGitRepository(baseRepo)) return false;
        String cleanLabel = sanitizeLabel(label);
        File target = new File(baseRepo, ".minecraft-ai/worktrees/" + providerType.toLowerCase() + "-" + cleanLabel);
        if (!target.exists()) return true;

        try {
            // Check for uncommitted work before force removal
            String status = getCommandOutput(target, "status", "--porcelain");
            if (!status.isBlank()) {
                ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§cWarning: Worktree " + cleanLabel + " has uncommitted changes.");
            }

            int code = runGitCommand(baseRepo, "worktree", "remove", "--force", target.getAbsolutePath());
            if (code == 0) {
                ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§7Removed worktree §f" + cleanLabel + "§r");
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public String getWorktreeDiff(File baseRepo, String label) {
        return getWorktreeDiff(baseRepo, "agent", label);
    }

    /**
     * Inspects the actual linked worktree directory for uncommitted changes + branch diff against HEAD.
     */
    public String getWorktreeDiff(File baseRepo, String providerType, String label) {
        if (!isGitRepository(baseRepo)) return "";
        String cleanLabel = sanitizeLabel(label);
        String branchName = getBranchName(providerType, cleanLabel);
        File targetWorktreeDir = new File(baseRepo, ".minecraft-ai/worktrees/" + providerType.toLowerCase() + "-" + cleanLabel);

        StringBuilder fullDiff = new StringBuilder();

        // 1. Inspect uncommitted edits directly inside the worktree working directory
        if (targetWorktreeDir.exists() && targetWorktreeDir.isDirectory()) {
            String workingDiff = getCommandOutput(targetWorktreeDir, "diff", "HEAD");
            if (!workingDiff.isBlank()) {
                fullDiff.append("=== Uncommitted Working Tree Edits ===\n").append(workingDiff).append("\n");
            } else {
                String unstaged = getCommandOutput(targetWorktreeDir, "diff");
                if (!unstaged.isBlank()) {
                    fullDiff.append("=== Unstaged Edits ===\n").append(unstaged).append("\n");
                }
            }

            String untracked = getCommandOutput(targetWorktreeDir, "status", "--porcelain");
            if (!untracked.isBlank()) {
                fullDiff.append("=== Status ===\n").append(untracked).append("\n");
            }
        }

        // 2. Diff committed branch commits against base repository HEAD
        String branchDiff = getCommandOutput(baseRepo, "diff", "HEAD..." + branchName);
        if (!branchDiff.isBlank()) {
            fullDiff.append("=== Branch Commits (").append(branchName).append(") ===\n").append(branchDiff);
        }

        return fullDiff.toString();
    }

    private int runGitCommand(File dir, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dir != null && dir.exists()) pb.directory(dir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor();
        } catch (Exception e) {
            return -1;
        }
    }

    private String getCommandOutput(File dir, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dir != null && dir.exists()) pb.directory(dir);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
