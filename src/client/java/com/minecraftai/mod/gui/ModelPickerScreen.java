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

import java.util.List;

public class ModelPickerScreen extends Screen {

    private final Screen parent;
    private final AgentInstance targetAgent;

    private static final List<String> CLAUDE_MODELS = List.of("claude-opus-5", "claude-sonnet-4-5", "claude-haiku-4-5");
    private static final List<String> CODEX_MODELS = List.of("gpt-5.x", "o3", "gpt-4o");
    private static final List<String> EFFORT_LEVELS = List.of("low", "medium", "high", "xhigh");

    public ModelPickerScreen(Screen parent, boolean isClaude) {
        this(parent, isClaude ? AgentManager.getInstance().getFocusedClaudeInstance() : AgentManager.getInstance().getFocusedCodexInstance());
    }

    public ModelPickerScreen(Screen parent, AgentInstance targetAgent) {
        super(Component.literal("AI Model & Effort Selector"));
        this.parent = parent;
        this.targetAgent = targetAgent != null ? targetAgent : AgentManager.getInstance().getFocusedInstance();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isClaude() {
        return targetAgent == null || "Claude".equalsIgnoreCase(targetAgent.getProviderType());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA0a0c14);

        int boxX = Math.max(20, width / 2 - 200);
        int boxY = Math.max(20, height / 2 - 120);
        int boxWidth = Math.min(width - 40, 400);
        int boxHeight = Math.min(height - 40, 240);

        boolean claude = isClaude();
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF141724);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, claude ? 0xFF00D4FF : 0xFF00E676);

        String agentName = targetAgent != null ? targetAgent.getAgentName() : (claude ? "Claude" : "Codex");

        int headerY = boxY + 8;
        graphics.text(font, Component.literal("§6=== Select Model for " + agentName + " ==="), boxX + 12, headerY, 0xFFFFFFFF);

        int mY = boxY + 30;
        graphics.text(font, Component.literal("§eModel:§r"), boxX + 12, mY, 0xFFFFFFFF);
        List<String> models = claude ? CLAUDE_MODELS : CODEX_MODELS;

        String currentModel = targetAgent != null ? targetAgent.getModel() : "";

        int curMY = mY + 14;
        for (String m : models) {
            boolean active = m.equalsIgnoreCase(currentModel);
            boolean hov = isHovered(mouseX, mouseY, boxX + 12, curMY, boxWidth - 24, 16);
            int bg = active ? 0xFF1e3550 : (hov ? 0xFF242838 : 0xFF181b26);
            graphics.fill(boxX + 12, curMY, boxX + boxWidth - 12, curMY + 16, bg);
            String dot = active ? "§a● §f" : "§7○ §f";
            graphics.text(font, Component.literal(dot + m), boxX + 18, curMY + 4, 0xFFFFFFFF);
            curMY += 18;
        }

        int eY = curMY + 6;
        graphics.text(font, Component.literal("§eReasoning Effort:§r"), boxX + 12, eY, 0xFFFFFFFF);
        int curEY = eY + 14;
        int efW = (boxWidth - 24 - 18) / 4;
        String currentEffort = targetAgent != null ? targetAgent.getEffort() : "xhigh";
        for (int i = 0; i < EFFORT_LEVELS.size(); i++) {
            String ef = EFFORT_LEVELS.get(i);
            boolean active = ef.equalsIgnoreCase(currentEffort);
            int efX = boxX + 12 + (i * (efW + 6));
            boolean hov = isHovered(mouseX, mouseY, efX, curEY, efW, 16);
            int bg = active ? 0xFF1e3550 : (hov ? 0xFF242838 : 0xFF181b26);
            graphics.fill(efX, curEY, efX + efW, curEY + 16, bg);
            graphics.fill(efX, curEY + 14, efX + efW, curEY + 16, active ? 0xFF00AA00 : 0xFF444455);
            graphics.text(font, Component.literal((active ? "§a" : "§7") + ef.toUpperCase()), efX + 6, curEY + 4, 0xFFFFFFFF);
        }

        int footerY = boxY + boxHeight - 28;
        int btnW = 80;
        int btnH = 18;
        int b2X = boxX + boxWidth - btnW - 12;
        drawButton(graphics, b2X, footerY, btnW, btnH, "Done", isHovered(mouseX, mouseY, b2X, footerY, btnW, btnH), 0xFF00AA00);
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

        int boxX = Math.max(20, width / 2 - 200);
        int boxY = Math.max(20, height / 2 - 120);
        int boxWidth = Math.min(width - 40, 400);
        int boxHeight = Math.min(height - 40, 240);

        List<String> models = isClaude() ? CLAUDE_MODELS : CODEX_MODELS;

        int mY = boxY + 30;
        int curMY = mY + 14;
        for (String m : models) {
            if (isHovered(mx, my, boxX + 12, curMY, boxWidth - 24, 16)) {
                if (targetAgent != null) {
                    targetAgent.setModel(m);
                }
                return true;
            }
            curMY += 18;
        }

        int eY = curMY + 6;
        int curEY = eY + 14;
        int efW = (boxWidth - 24 - 18) / 4;
        for (int i = 0; i < EFFORT_LEVELS.size(); i++) {
            String ef = EFFORT_LEVELS.get(i);
            int efX = boxX + 12 + (i * (efW + 6));
            if (isHovered(mx, my, efX, curEY, efW, 16)) {
                if (targetAgent != null) {
                    targetAgent.setEffort(ef);
                }
                return true;
            }
        }

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
