package com.minecraftai.mod.gui;

import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ProjectPickerScreen extends Screen {

    private final Screen parent;
    private final List<ProjectProfile> projects = new ArrayList<>();

    public ProjectPickerScreen(Screen parent) {
        super(Component.literal("Project Profile Picker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        projects.clear();
        projects.addAll(ProjectManager.getInstance().getAllProjects());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA0a0c14);

        int boxX = Math.max(20, width / 2 - 240);
        int boxY = Math.max(20, height / 2 - 140);
        int boxWidth = Math.min(width - 40, 480);
        int boxHeight = Math.min(height - 40, 280);

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF141724);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF00AAFF);

        int headerY = boxY + 8;
        graphics.text(font, Component.literal("§6=== Project Profiles (/project <name>) ==="), boxX + 12, headerY, 0xFFFFFFFF);

        int listX = boxX + 12;
        int listY = boxY + 28;
        int listW = boxWidth - 24;
        int listH = boxHeight - 64;
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0d0f17);

        ProjectProfile active = ProjectManager.getInstance().getActiveProject();
        String activeName = active != null ? active.getName() : "";

        int itemH = 32;
        int itemY = listY + 4;
        for (int i = 0; i < projects.size(); i++) {
            ProjectProfile p = projects.get(i);
            boolean isActive = p.getName().equalsIgnoreCase(activeName);
            boolean hovered = isHovered(mouseX, mouseY, listX + 2, itemY, listW - 4, itemH - 2);

            int bg = isActive ? 0xFF1e3550 : (hovered ? 0xFF1b2030 : 0xFF121520);
            graphics.fill(listX + 2, itemY, listX + listW - 2, itemY + itemH - 2, bg);

            String activeMarker = isActive ? "§a● §f" : "§7○ §f";
            graphics.text(font, Component.literal(activeMarker + "§l" + p.getName() + "§r §7(" + p.getWorkingDirectory() + ")"), listX + 8, itemY + 4, 0xFFFFFFFF);

            String modelDetails = "§8Claude: " + p.getDefaultClaudeModel() + " (" + p.getDefaultClaudePermissionMode() + ") · Codex: " + p.getDefaultCodexModel();
            graphics.text(font, Component.literal(modelDetails), listX + 8, itemY + 16, 0xFF888888);

            int btnW = 54;
            int btnH = 16;
            int sBtnX = listX + listW - btnW - 6;
            int sBtnY = itemY + 8;
            drawButton(graphics, sBtnX, sBtnY, btnW, btnH, isActive ? "Active" : "Switch", isActive, isHovered(mouseX, mouseY, sBtnX, sBtnY, btnW, btnH), 0xFF00AA00);

            itemY += itemH;
        }

        int footerY = boxY + boxHeight - 28;
        int btnW = 80;
        int btnH = 18;
        int b2X = boxX + boxWidth - btnW - 12;
        drawButton(graphics, b2X, footerY, btnW, btnH, "Back", false, isHovered(mouseX, mouseY, b2X, footerY, btnW, btnH), 0xFFAA2222);
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
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        int mx = (int) event.x();
        int my = (int) event.y();

        int boxX = Math.max(20, width / 2 - 240);
        int boxY = Math.max(20, height / 2 - 140);
        int boxWidth = Math.min(width - 40, 480);
        int boxHeight = Math.min(height - 40, 280);

        int listX = boxX + 12;
        int listY = boxY + 28;
        int listW = boxWidth - 24;
        int itemH = 32;

        int itemY = listY + 4;
        for (int i = 0; i < projects.size(); i++) {
            ProjectProfile p = projects.get(i);
            int btnW = 54;
            int btnH = 16;
            int sBtnX = listX + listW - btnW - 6;
            int sBtnY = itemY + 8;

            if (isHovered(mx, my, sBtnX, sBtnY, btnW, btnH) || (isHovered(mx, my, listX + 2, itemY, listW - 4, itemH - 2) && isDouble)) {
                ProjectManager.getInstance().switchProject(p.getName());
                Minecraft.getInstance().setScreen(parent);
                return true;
            }
            itemY += itemH;
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
