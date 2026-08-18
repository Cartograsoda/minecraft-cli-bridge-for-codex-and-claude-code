package com.minecraftai.mod.gui;

import com.minecraftai.mod.task.BackgroundTask;
import com.minecraftai.mod.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class TasksScreen extends Screen {

    private final Screen parent;
    private BackgroundTask selectedTaskForOutput = null;
    private int scrollOffset = 0;

    public TasksScreen(Screen parent) {
        super(Component.literal("Background Tasks"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA0a0c14);

        int boxX = Math.max(20, width / 2 - 250);
        int boxY = Math.max(20, height / 2 - 145);
        int boxWidth = Math.min(width - 40, 500);
        int boxHeight = Math.min(height - 40, 290);

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF141724);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF00AAFF);

        int headerY = boxY + 8;
        List<BackgroundTask> allTasks = TaskManager.getInstance().getAllTasks();
        int runningCount = TaskManager.getInstance().getRunningCount();
        graphics.text(font, Component.literal("§6=== Background Task Manager (" + runningCount + " running) ==="), boxX + 12, headerY, 0xFFFFFFFF);

        int listX = boxX + 12;
        int listY = boxY + 28;
        int listW = boxWidth - 24;
        int listH = boxHeight - 64;
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0d0f17);

        if (selectedTaskForOutput != null) {
            renderTaskOutput(graphics, listX, listY, listW, listH);
        } else {
            renderTaskList(graphics, mouseX, mouseY, allTasks, listX, listY, listW, listH);
        }

        // Bottom Bar
        int footerY = boxY + boxHeight - 28;
        int btnW = 80;
        int btnH = 18;

        if (selectedTaskForOutput != null) {
            int b1X = boxX + 12;
            drawButton(graphics, b1X, footerY, btnW, btnH, "Back to List", false, isHovered(mouseX, mouseY, b1X, footerY, btnW, btnH), 0xFF445070);
        } else {
            int b1X = boxX + 12;
            drawButton(graphics, b1X, footerY, btnW, btnH, "Stop All", false, isHovered(mouseX, mouseY, b1X, footerY, btnW, btnH), 0xFFAA2222);
        }

        int b2X = boxX + boxWidth - btnW - 12;
        drawButton(graphics, b2X, footerY, btnW, btnH, "Close", false, isHovered(mouseX, mouseY, b2X, footerY, btnW, btnH), 0xFFAA2222);
    }

    private void renderTaskList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<BackgroundTask> tasks, int x, int y, int w, int h) {
        if (tasks.isEmpty()) {
            graphics.text(font, Component.literal("§7No background tasks running. Use '!command &' to background a task.§r"), x + 8, y + 8, 0xFF888888);
            return;
        }

        int itemH = 28;
        int maxVis = h / itemH;
        int startIdx = Math.max(0, scrollOffset);
        int endIdx = Math.min(tasks.size(), startIdx + maxVis);

        int itemY = y + 4;
        for (int i = startIdx; i < endIdx; i++) {
            BackgroundTask t = tasks.get(i);
            boolean hov = isHovered(mouseX, mouseY, x + 2, itemY, w - 4, itemH - 2);
            int bg = hov ? 0xFF1b2030 : 0xFF121520;
            graphics.fill(x + 2, itemY, x + w - 2, itemY + itemH - 2, bg);

            String statusDot = t.getStatus() == BackgroundTask.Status.RUNNING ? "§a● RUNNING§r" :
                    (t.getStatus() == BackgroundTask.Status.SUCCESS ? "§a✓ SUCCESS§r" :
                            (t.getStatus() == BackgroundTask.Status.FAILED ? "§c✗ FAILED§r" : "§7■ STOPPED§r"));

            graphics.text(font, Component.literal("§f" + t.getName() + "  " + statusDot + " §7(" + t.getFormattedElapsed() + ")§r"), x + 8, itemY + 4, 0xFFFFFFFF);
            graphics.text(font, Component.literal("§8Type: " + t.getType() + " · ID: " + t.getId()), x + 8, itemY + 15, 0xFF888888);

            // Action Buttons: [Output] [Stop]
            int outBtnW = 46;
            int outBtnH = 16;
            int outBtnX = x + w - (outBtnW * 2) - 14;
            drawButton(graphics, outBtnX, itemY + 6, outBtnW, outBtnH, "Output", false, isHovered(mouseX, mouseY, outBtnX, itemY + 6, outBtnW, outBtnH), 0xFF00AAFF);

            int stopBtnX = outBtnX + outBtnW + 4;
            if (t.getStatus() == BackgroundTask.Status.RUNNING) {
                drawButton(graphics, stopBtnX, itemY + 6, outBtnW, outBtnH, "Stop", false, isHovered(mouseX, mouseY, stopBtnX, itemY + 6, outBtnW, outBtnH), 0xFFAA2222);
            }

            itemY += itemH;
        }
    }

    private void renderTaskOutput(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.text(font, Component.literal("§eOutput log for: §f" + selectedTaskForOutput.getName() + " §7(" + selectedTaskForOutput.getStatus() + ")§r"), x + 6, y + 4, 0xFFFFFFFF);

        List<String> lines = selectedTaskForOutput.getOutputLines();
        if (lines.isEmpty()) {
            graphics.text(font, Component.literal("§7(No output lines recorded yet)§r"), x + 6, y + 18, 0xFF888888);
            return;
        }

        int lineH = 10;
        int maxLines = (h - 22) / lineH;
        int start = Math.max(0, lines.size() - maxLines);

        int curY = y + 18;
        for (int i = start; i < lines.size(); i++) {
            graphics.text(font, Component.literal("§7" + lines.get(i)), x + 6, curY, 0xFFCCCCCC);
            curY += lineH;
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean active, boolean hovered, int accent) {
        int bg = active ? 0xFF354060 : (hovered ? 0xFF242838 : 0xFF181b26);
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y + h - 2, x + w, y + h, accent);
        int textW = font.width(Component.literal(label));
        graphics.text(font, Component.literal(label), x + (w - textW) / 2, y + (h - 8) / 2, hovered || active ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (selectedTaskForOutput != null) {
                selectedTaskForOutput = null;
                return true;
            }
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        int mx = (int) event.x();
        int my = (int) event.y();

        int boxX = Math.max(20, width / 2 - 250);
        int boxY = Math.max(20, height / 2 - 145);
        int boxWidth = Math.min(width - 40, 500);
        int boxHeight = Math.min(height - 40, 290);

        int listX = boxX + 12;
        int listY = boxY + 28;
        int listW = boxWidth - 24;
        int listH = boxHeight - 64;

        if (selectedTaskForOutput == null) {
            List<BackgroundTask> tasks = TaskManager.getInstance().getAllTasks();
            int itemH = 28;
            int maxVis = listH / itemH;
            int startIdx = Math.max(0, scrollOffset);
            int endIdx = Math.min(tasks.size(), startIdx + maxVis);

            int itemY = listY + 4;
            for (int i = startIdx; i < endIdx; i++) {
                BackgroundTask t = tasks.get(i);
                int outBtnW = 46;
                int outBtnH = 16;
                int outBtnX = listX + listW - (outBtnW * 2) - 14;
                int stopBtnX = outBtnX + outBtnW + 4;

                if (isHovered(mx, my, outBtnX, itemY + 6, outBtnW, outBtnH)) {
                    selectedTaskForOutput = t;
                    return true;
                }
                if (t.getStatus() == BackgroundTask.Status.RUNNING && isHovered(mx, my, stopBtnX, itemY + 6, outBtnW, outBtnH)) {
                    TaskManager.getInstance().stopTask(t.getId());
                    return true;
                }
                itemY += itemH;
            }
        }

        int footerY = boxY + boxHeight - 28;
        int btnW = 80;
        int btnH = 18;

        int b1X = boxX + 12;
        int b2X = boxX + boxWidth - btnW - 12;

        if (isHovered(mx, my, b1X, footerY, btnW, btnH)) {
            if (selectedTaskForOutput != null) {
                selectedTaskForOutput = null;
            } else {
                TaskManager.getInstance().stopAll();
            }
            return true;
        }

        if (isHovered(mx, my, b2X, footerY, btnW, btnH)) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        return super.mouseClicked(event, isDouble);
    }
}
