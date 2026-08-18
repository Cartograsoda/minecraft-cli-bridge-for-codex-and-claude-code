package com.minecraftai.mod.hud;

import com.minecraftai.mod.agent.*;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.input.ModKeyMappings;
import com.minecraftai.mod.task.BackgroundTask;
import com.minecraftai.mod.task.TaskManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HudRenderer implements HudElement {

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options.hideGui) {
            return;
        }

        HudConfig config = ConfigManager.getInstance().getConfig().getHud();
        if (!config.isEnabled() || config.getMode() == HudConfig.Mode.OFF) {
            return;
        }

        Font font = client.font;
        if (font == null) return;

        AgentManager am = AgentManager.getInstance();
        List<String> lines = buildHudLines(config.getMode(), am, config);
        if (lines.isEmpty()) return;

        int cardWidth = 0;
        for (String line : lines) {
            int w = font.width(Component.literal(line));
            if (w > cardWidth) cardWidth = w;
        }
        cardWidth += 18;
        int lineHeight = 10;
        int cardHeight = (lines.size() * lineHeight) + 12;

        boolean altHeld = ModKeyMappings.isAltModifierHeld();
        if (altHeld) {
            cardHeight += 16;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int x = 8;
        int y = 8;

        switch (config.getPosition()) {
            case TOP_RIGHT:
                x = screenWidth - cardWidth - 8;
                y = 8;
                break;
            case BOTTOM_LEFT:
                x = 8;
                y = screenHeight - cardHeight - 20;
                break;
            case BOTTOM_RIGHT:
                x = screenWidth - cardWidth - 8;
                y = screenHeight - cardHeight - 20;
                break;
            case TOP_LEFT:
            default:
                x = 8;
                y = 8;
                break;
        }

        int alpha = (int) (config.getOpacity() * 255.0f);
        int bgColor = (alpha << 24) | 0x0f111a;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, bgColor);

        // Accent top bar indicating focused agent state
        AgentInstance focused = am.getFocusedInstance();
        int accentColor = 0xFF445070;
        if (focused != null) {
            AgentStatus.State st = focused.getStatus().getState();
            if (st == AgentStatus.State.ERROR) {
                accentColor = 0xFFFF3333; // Red
            } else if (st == AgentStatus.State.WAITING_INPUT) {
                accentColor = 0xFFFFCC00; // Yellow
            } else if (st == AgentStatus.State.RUNNING) {
                accentColor = 0xFF00D4FF; // Cyan / Active
            } else if (st == AgentStatus.State.PLAN_MODE) {
                accentColor = 0xFFBB66FF; // Purple
            }
        }
        graphics.fill(x, y, x + cardWidth, y + 2, accentColor);

        // Text lines
        int textY = y + 6;
        for (String line : lines) {
            graphics.text(font, Component.literal(line), x + 8, textY, 0xFFFFFFFF);
            textY += lineHeight;
        }

        if (altHeld) {
            int barY = y + cardHeight - 14;
            graphics.fill(x + 4, barY - 2, x + cardWidth - 4, barY + 12, 0xFF243048);
            String actions = "§e[ALT HELD] §b[Ctrl+Tab: Focus] §f[U: Composer] §c[K: Stop All] §a[/ai help]";
            graphics.text(font, Component.literal(actions), x + 8, barY + 1, 0xFFFFFFFF);
        }
    }

    private List<String> buildHudLines(HudConfig.Mode mode, AgentManager am, HudConfig config) {
        switch (mode) {
            case COMPACT:
                return buildCompactHudLines(am, config);
            case DETAILED:
                return buildDetailedHudLines(am, config);
            case NORMAL:
            default:
                return buildNormalHudLines(am, config);
        }
    }

    private List<String> buildCompactHudLines(AgentManager am, HudConfig config) {
        List<String> lines = new ArrayList<>();
        ClaudeProvider cp = am.getClaudeProvider();
        CodexProvider xp = am.getCodexProvider();
        AgentInstance focused = am.getFocusedInstance();

        List<AgentInstance> claudeList = cp.getAllInstances();
        List<AgentInstance> codexList = xp.getAllInstances();

        int cRun = 0, cWait = 0, cErr = 0, cIdle = 0;
        for (AgentInstance c : claudeList) {
            AgentStatus.State st = c.getStatus().getState();
            if (st == AgentStatus.State.ERROR) cErr++;
            else if (st == AgentStatus.State.WAITING_INPUT) cWait++;
            else if (c.getStatus().isRunning()) cRun++;
            else cIdle++;
        }

        int xRun = 0, xWait = 0, xErr = 0, xIdle = 0;
        for (AgentInstance x : codexList) {
            AgentStatus.State st = x.getStatus().getState();
            if (st == AgentStatus.State.ERROR) xErr++;
            else if (st == AgentStatus.State.WAITING_INPUT) xWait++;
            else if (x.getStatus().isRunning()) xRun++;
            else xIdle++;
        }

        String cBadge = formatCountBadge("CLAUDE", cRun, cWait, cErr, cIdle, 0xFF00D4FF);
        String xBadge = formatCountBadge("CODEX", xRun, xWait, xErr, xIdle, 0xFF00E676);
        String fBadge = (focused != null) ? "§fFOCUS: " + focused.getAgentName() + "§r" : "";

        lines.add(cBadge + " │ " + xBadge + (fBadge.isEmpty() ? "" : " │ " + fBadge));

        // Second line: Focused metrics & background tasks
        AgentTelemetry tel = (focused != null) ? focused.getTelemetry() : new AgentTelemetry();
        String ctx = tel.isContextDataAvailable() ? "CTX " + (int) tel.getContextRemainingPercent() + "%" : "CTX --";
        String perm = (focused != null && (focused.getPermissionMode().contains("bypass") || focused.getPermissionMode().contains("danger") || focused.getPermissionMode().contains("yolo"))) ?
                "§c⚠ YOLO§r" : "§7SAFE§r";

        double c5h = cp.getAccount5HourRemaining();
        String c5hStr = !Double.isNaN(c5h) ? "5H " + (int) c5h + "%" : "5H --";

        int bgCount = TaskManager.getInstance().getRunningCount();
        String bgStr = bgCount > 0 ? " │ §e" + bgCount + " tasks§r" : "";

        lines.add("§7" + ctx + " │ " + c5hStr + " │ " + perm + bgStr);
        return lines;
    }

    private List<String> buildNormalHudLines(AgentManager am, HudConfig config) {
        List<String> lines = new ArrayList<>();
        List<AgentInstance> all = am.getAllInstances();
        AgentInstance focused = am.getFocusedInstance();

        for (AgentInstance inst : all) {
            boolean isFoc = (inst == focused);
            String ptr = isFoc ? "§b> §r" : "  ";
            String dot = getStatusDot(inst.getStatus());
            String pfx = "Claude".equalsIgnoreCase(inst.getProviderType()) ? "§bCLAUDE" : "§2CODEX ";
            String label = padRight(inst.getLabel(), 12);
            String act = inst.getStatus().getStatusLabel();
            if (act.length() > 24) act = act.substring(0, 21) + "...";

            String ws = inst.isIsolated() ? " §8(" + inst.getWorktreeBranch() + ")§r" : "";
            lines.add(ptr + pfx + " " + dot + " §f" + label + " §7" + act + ws);
        }

        lines.add("§8──────────────────────────────────────────§r");

        // Footer: Target metrics
        if (focused != null) {
            AgentTelemetry tel = focused.getTelemetry();
            String ctx = tel.isContextDataAvailable() ? "CTX " + (int) tel.getContextRemainingPercent() + "%" : "CTX --";
            String perm = (focused.getPermissionMode().contains("bypass") || focused.getPermissionMode().contains("danger") || focused.getPermissionMode().contains("yolo")) ?
                    "§c⚠ YOLO§r" : "§7SAFE§r";
            int bgCount = TaskManager.getInstance().getRunningCount();
            String bgStr = bgCount > 0 ? " · §e" + bgCount + " tasks§r" : "";
            lines.add("§7Target: §f" + focused.getAgentName() + " §7· " + ctx + " · " + perm + bgStr);
        }

        // Provider quota line
        ClaudeProvider cp = am.getClaudeProvider();
        CodexProvider xp = am.getCodexProvider();
        double c5h = cp.getAccount5HourRemaining();
        double x5h = xp.getAccount5HourRemaining();
        int credits = xp.getResetCredits();

        String cStr = !Double.isNaN(c5h) ? (int) c5h + "%" : "--";
        String xStr = !Double.isNaN(x5h) ? (int) x5h + "%" : "--";
        String credStr = credits > 0 ? " §6(" + credits + " credits)§r" : "";

        lines.add("§7Account 5H: §bClaude " + cStr + "§7 │ §2Codex " + xStr + credStr);
        return lines;
    }

    private List<String> buildDetailedHudLines(AgentManager am, HudConfig config) {
        List<String> lines = new ArrayList<>();
        ClaudeProvider cp = am.getClaudeProvider();
        CodexProvider xp = am.getCodexProvider();
        AgentInstance focused = am.getFocusedInstance();

        List<AgentInstance> claudeList = cp.getAllInstances();
        List<AgentInstance> codexList = xp.getAllInstances();

        // 1. CLAUDE SECTION
        if (!claudeList.isEmpty()) {
            lines.add("§bCLAUDE§r §7(" + claudeList.size() + " instances)§r");
            for (AgentInstance inst : claudeList) {
                boolean isFoc = (inst == focused);
                String ptr = isFoc ? "§b> §r" : "  ";
                String dot = getStatusDot(inst.getStatus());
                String modelEffort = "§8[" + inst.getModel() + " · " + inst.getEffort() + "]§r";
                String label = padRight(inst.getLabel(), 12);
                String ws = inst.isIsolated() ? "§8(worktree: " + inst.getWorktreeBranch() + ")§r" : "";
                String act = inst.getStatus().getStatusLabel();
                if (act.length() > 24) act = act.substring(0, 21) + "...";

                lines.add(ptr + dot + " §f" + label + " " + modelEffort + " §7" + act + " " + ws);

                // Subagents
                for (SubagentInfo s : inst.getStatus().getSubagents()) {
                    String sDot = s.getStatus() == SubagentInfo.Status.DONE ? "§a✓" :
                            (s.getStatus() == SubagentInfo.Status.RUNNING ? "§e●" : "§c✗");
                    lines.add("    §8├─ " + sDot + " §f" + s.getName() + " §7(" + s.getTask() + ")§r");
                }
            }
        }

        // 2. CODEX SECTION
        if (!codexList.isEmpty()) {
            lines.add("§2CODEX§r §7(" + codexList.size() + " instances)§r");
            for (AgentInstance inst : codexList) {
                boolean isFoc = (inst == focused);
                String ptr = isFoc ? "§2> §r" : "  ";
                String dot = getStatusDot(inst.getStatus());
                String modelEffort = "§8[" + inst.getModel() + " · " + inst.getEffort() + "]§r";
                String label = padRight(inst.getLabel(), 12);
                String ws = inst.isIsolated() ? "§8(worktree: " + inst.getWorktreeBranch() + ")§r" : "";
                String act = inst.getStatus().getStatusLabel();
                if (act.length() > 24) act = act.substring(0, 21) + "...";

                lines.add(ptr + dot + " §f" + label + " " + modelEffort + " §7" + act + " " + ws);

                for (SubagentInfo s : inst.getStatus().getSubagents()) {
                    String sDot = s.getStatus() == SubagentInfo.Status.DONE ? "§a✓" :
                            (s.getStatus() == SubagentInfo.Status.RUNNING ? "§e●" : "§c✗");
                    lines.add("    §8├─ " + sDot + " §f" + s.getName() + " §7(" + s.getTask() + ")§r");
                }
            }
        }

        lines.add("§8──────────────────────────────────────────────────§r");

        // 3. TARGET STATUS & CONTEXT
        if (focused != null) {
            AgentTelemetry tel = focused.getTelemetry();
            String ctx = tel.isContextDataAvailable() ? "CTX " + (int) tel.getContextRemainingPercent() + "%" : "CTX --";
            String perm = (focused.getPermissionMode().contains("bypass") || focused.getPermissionMode().contains("danger") || focused.getPermissionMode().contains("yolo")) ?
                    "§c⚠ YOLO§r" : "§7SAFE§r";
            lines.add("§7Target: §f" + focused.getAgentName() + " §8[" + focused.getModel() + "] §7· " + ctx + " · " + perm);
        }

        // 4. DETAILED QUOTA & RESET COUNTDOWNS
        ProviderTelemetrySnapshot snap = xp.getTelemetrySnapshot();
        double c5h = cp.getAccount5HourRemaining();
        String c5hStr = !Double.isNaN(c5h) ? "Claude 5H: " + (int) c5h + "% [" + AgentTelemetry.formatBar(c5h, 8) + "]" : "Claude 5H: --";

        String x5hStr = snap.primaryRemainingPercent().isPresent() ?
                "Codex " + snap.primaryWindowLabel() + ": " + (int) snap.primaryRemainingPercent().getAsDouble() + "% [" + AgentTelemetry.formatBar(snap.primaryRemainingPercent().getAsDouble(), 8) + "] (" + snap.formatPrimaryCountdown() + ")" :
                "Codex 5H: --";

        lines.add("§7" + c5hStr + " │ " + x5hStr);

        if (snap.secondaryRemainingPercent().isPresent() || snap.resetCredits() > 0) {
            String secStr = snap.secondaryRemainingPercent().isPresent() ?
                    "Codex " + snap.secondaryWindowLabel() + ": " + (int) snap.secondaryRemainingPercent().getAsDouble() + "% (" + snap.formatSecondaryCountdown() + ")" : "";
            String credStr = snap.resetCredits() > 0 ? " §6(" + snap.resetCredits() + " reset credits available)§r" : "";
            lines.add("§7" + secStr + credStr);
        }

        // 5. RUNNING BACKGROUND TASKS
        List<BackgroundTask> bgTasks = TaskManager.getInstance().getRunningTasks();
        if (!bgTasks.isEmpty()) {
            lines.add("§eBackground Tasks (" + bgTasks.size() + " running):§r");
            for (BackgroundTask bt : bgTasks) {
                lines.add("  §8• §f" + bt.getName() + " §7(" + bt.getFormattedElapsed() + " elapsed)§r");
            }
        }

        return lines;
    }

    private String getStatusDot(AgentStatus status) {
        AgentStatus.State st = status.getState();
        if (st == AgentStatus.State.ERROR) return "§c✗";
        if (st == AgentStatus.State.WAITING_INPUT) return "§e⚠";
        if (st == AgentStatus.State.STOPPING) return "§6■";
        if (st == AgentStatus.State.PLAN_MODE) return "§d◇";
        if (status.isRunning()) return "§a●";
        return "§7○";
    }

    private String formatCountBadge(String name, int run, int wait, int err, int idle, int colorHex) {
        String colorTag = (name.equalsIgnoreCase("CLAUDE")) ? "§b" : "§2";
        StringBuilder sb = new StringBuilder(colorTag).append(name).append(" ");
        if (err > 0) sb.append("§c").append(err).append("✗ ");
        if (wait > 0) sb.append("§e").append(wait).append("⚠ ");
        if (run > 0) sb.append("§a").append(run).append("● ");
        sb.append("§7").append(idle).append("○§r");
        return sb.toString();
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s;
        return String.format("%-" + n + "s", s);
    }
}
