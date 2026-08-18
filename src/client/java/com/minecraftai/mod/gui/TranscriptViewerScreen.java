package com.minecraftai.mod.gui;

import com.minecraftai.mod.agent.AgentInstance;
import com.minecraftai.mod.agent.AgentManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TranscriptViewerScreen extends Screen {

    private final Screen parent;
    private final AgentInstance targetAgent;
    private int scrollOffset = 0;
    private final List<String> logLines = new ArrayList<>();

    public TranscriptViewerScreen(Screen parent) {
        this(parent, AgentManager.getInstance().getFocusedInstance());
    }

    public TranscriptViewerScreen(Screen parent, AgentInstance targetAgent) {
        super(Component.literal("AI Transcript Viewer"));
        this.parent = parent;
        this.targetAgent = targetAgent != null ? targetAgent : AgentManager.getInstance().getFocusedInstance();
    }

    @Override
    protected void init() {
        super.init();
        logLines.clear();
        if (targetAgent != null) {
            logLines.addAll(targetAgent.getTranscript());
        } else {
            for (AgentInstance inst : AgentManager.getInstance().getAllInstances()) {
                logLines.addAll(inst.getTranscript());
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
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFFFFAA00);

        int headerY = boxY + 8;
        String agentTag = (targetAgent != null) ? " (" + targetAgent.getAgentName() + ")" : "";
        graphics.text(font, Component.literal("§6=== AI Activity Transcript" + agentTag + " ==="), boxX + 12, headerY, 0xFFFFFFFF);

        int logX = boxX + 12;
        int logY = boxY + 26;
        int logW = boxWidth - 24;
        int logH = boxHeight - 60;
        graphics.fill(logX, logY, logX + logW, logY + logH, 0xFF0d0f17);

        if (logLines.isEmpty()) {
            graphics.text(font, Component.literal("§7No activity transcript recorded for this agent yet.§r"), logX + 8, logY + 8, 0xFF888888);
        } else {
            int lineH = 10;
            int maxVisible = logH / lineH;
            int startIdx = Math.max(0, scrollOffset);
            int endIdx = Math.min(logLines.size(), startIdx + maxVisible);

            int curY = logY + 4;
            for (int i = startIdx; i < endIdx; i++) {
                String line = logLines.get(i);
                graphics.text(font, Component.literal(line), logX + 6, curY, 0xFFEEEEEE);
                curY += lineH;
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
        if (event.key() == GLFW.GLFW_KEY_UP) {
            if (scrollOffset > 0) scrollOffset--;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DOWN) {
            if (scrollOffset < logLines.size() - 5) scrollOffset++;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 3);
        } else if (vAmount < 0) {
            scrollOffset = Math.min(Math.max(0, logLines.size() - 5), scrollOffset + 3);
        }
        return true;
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
