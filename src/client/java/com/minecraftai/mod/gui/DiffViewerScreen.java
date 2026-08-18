package com.minecraftai.mod.gui;

import com.minecraftai.mod.agent.AgentInstance;
import com.minecraftai.mod.agent.AgentManager;
import com.minecraftai.mod.agent.ChangedFileInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class DiffViewerScreen extends Screen {

    private final Screen parent;
    private final AgentInstance targetAgent;
    private final List<ChangedFileInfo> changedFiles = new ArrayList<>();
    private final List<String> diffLines = new ArrayList<>();
    private int scrollOffset = 0;

    public DiffViewerScreen(Screen parent) {
        this(parent, AgentManager.getInstance().getFocusedInstance());
    }

    public DiffViewerScreen(Screen parent, AgentInstance targetAgent) {
        super(Component.literal("AI Changed Files & Diff"));
        this.parent = parent;
        this.targetAgent = targetAgent != null ? targetAgent : AgentManager.getInstance().getFocusedInstance();
    }

    @Override
    protected void init() {
        super.init();
        changedFiles.clear();
        diffLines.clear();

        if (targetAgent != null) {
            changedFiles.addAll(targetAgent.getStatus().getChangedFiles());
            String diff = targetAgent.getLatestDiff();
            if (!diff.isBlank()) {
                for (String l : diff.split("\n")) {
                    diffLines.add(l);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA0a0c14);

        int boxX = Math.max(20, width / 2 - 260);
        int boxY = Math.max(20, height / 2 - 150);
        int boxWidth = Math.min(width - 40, 520);
        int boxHeight = Math.min(height - 40, 300);

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF141724);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF00E676);

        int headerY = boxY + 8;
        String tag = (targetAgent != null) ? " (" + targetAgent.getAgentName() + (targetAgent.isIsolated() ? " · isolated" : " · shared") + ")" : "";
        graphics.text(font, Component.literal("§6=== AI Changed Files & Diff" + tag + " ==="), boxX + 12, headerY, 0xFFFFFFFF);

        int logX = boxX + 12;
        int logY = boxY + 26;
        int logW = boxWidth - 24;
        int logH = boxHeight - 60;
        graphics.fill(logX, logY, logX + logW, logY + logH, 0xFF0d0f17);

        if (changedFiles.isEmpty() && diffLines.isEmpty()) {
            graphics.text(font, Component.literal("§7No file modifications recorded for this agent.§r"), logX + 8, logY + 8, 0xFF888888);
        } else {
            int lineH = 10;
            int curY = logY + 4;

            if (!changedFiles.isEmpty()) {
                graphics.text(font, Component.literal("§eChanged Files:§r"), logX + 6, curY, 0xFFFFFFFF);
                curY += lineH;
                for (ChangedFileInfo cf : changedFiles) {
                    String color = cf.getType() == ChangedFileInfo.ChangeType.ADDED ? "§a+ " :
                            (cf.getType() == ChangedFileInfo.ChangeType.DELETED ? "§c- " : "§e~ ");
                    graphics.text(font, Component.literal(color + "§f" + cf.getPath()), logX + 12, curY, 0xFFFFFFFF);
                    curY += lineH;
                }
                curY += 4;
            }

            if (!diffLines.isEmpty()) {
                graphics.text(font, Component.literal("§eDiff View:§r"), logX + 6, curY, 0xFFFFFFFF);
                curY += lineH;

                int maxVisible = (logY + logH - curY) / lineH;
                int startIdx = Math.max(0, scrollOffset);
                int endIdx = Math.min(diffLines.size(), startIdx + maxVisible);

                for (int i = startIdx; i < endIdx; i++) {
                    String line = diffLines.get(i);
                    int col = 0xFFCCCCCC;
                    if (line.startsWith("+")) col = 0xFF55FF55;
                    else if (line.startsWith("-")) col = 0xFFFF5555;
                    else if (line.startsWith("@")) col = 0xFF55FFFF;
                    else if (line.startsWith("===")) col = 0xFFFFCC00;
                    graphics.text(font, Component.literal(line), logX + 6, curY, col);
                    curY += lineH;
                }
            }
        }

        int footerY = boxY + boxHeight - 28;
        int btnW = 80;
        int btnH = 18;
        int b2X = boxX + boxWidth - btnW - 12;
        drawButton(graphics, b2X, footerY, btnW, btnH, "Close", isHovered(mouseX, mouseY, b2X, footerY, btnW, btnH), 0xFFAA2222);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean hovered, int accent) {
        int bg = hovered ? 0xFF242838 : 0xFF181b26;
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y + h - 2, x + w, y + h, accent);
        int textW = font.width(Component.literal(label));
        graphics.text(font, Component.literal(label), x + (w - textW) / 2, y + (h - 8) / 2, hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        int mx = (int) event.x();
        int my = (int) event.y();

        int boxX = Math.max(20, width / 2 - 260);
        int boxY = Math.max(20, height / 2 - 150);
        int boxWidth = Math.min(width - 40, 520);
        int boxHeight = Math.min(height - 40, 300);

        int footerY = boxY + boxHeight - 28;
        int btnW = 80;
        int btnH = 18;
        int b2X = boxX + boxWidth - btnW - 12;

        if (isHovered(mx, my, b2X, footerY, btnW, btnH)) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }
}
