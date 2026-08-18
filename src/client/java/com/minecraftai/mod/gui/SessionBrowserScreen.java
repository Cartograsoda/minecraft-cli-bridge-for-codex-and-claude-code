package com.minecraftai.mod.gui;

import com.minecraftai.mod.agent.AgentManager;
import com.minecraftai.mod.agent.SessionHistoryManager;
import com.minecraftai.mod.agent.SessionInfo;
import com.minecraftai.mod.agent.TurnInfo;
import com.minecraftai.mod.codex.CodexAppServerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SessionBrowserScreen extends Screen {

    private final Screen parent;
    private boolean showClaude = true;
    private String filterQuery = "";
    private final List<SessionInfo> displayedSessions = new ArrayList<>();
    private int scrollOffset = 0;
    private SessionInfo selectedSessionForTimeline = null;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd HH:mm");

    public SessionBrowserScreen(Screen parent) {
        super(Component.literal("AI Session Browser"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        refreshSessions();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshSessions() {
        displayedSessions.clear();
        List<SessionInfo> source = showClaude ?
                SessionHistoryManager.getClaudeSessions() :
                SessionHistoryManager.getCodexSessions();

        for (SessionInfo s : source) {
            if (filterQuery.isBlank() ||
                    s.getTitle().toLowerCase().contains(filterQuery.toLowerCase()) ||
                    s.getId().toLowerCase().contains(filterQuery.toLowerCase()) ||
                    s.getProject().toLowerCase().contains(filterQuery.toLowerCase())) {
                displayedSessions.add(s);
            }
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, displayedSessions.size() - 6)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA0a0c14);

        int boxX = Math.max(10, width / 2 - 270);
        int boxY = Math.max(10, height / 2 - 150);
        int boxWidth = Math.min(width - 20, 540);
        int boxHeight = Math.min(height - 20, 300);

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF141724);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, showClaude ? 0xFF00D4FF : 0xFF00E676);

        // Header
        int headerY = boxY + 8;
        if (selectedSessionForTimeline != null) {
            graphics.text(font, Component.literal("§6=== Turn History & Checkpoint Timeline (" + selectedSessionForTimeline.getShortId() + ") ==="), boxX + 12, headerY, 0xFFFFFFFF);
        } else {
            String countStr = " (" + displayedSessions.size() + " total)";
            graphics.text(font, Component.literal("§6=== AI Session Browser" + countStr + " ==="), boxX + 12, headerY, 0xFFFFFFFF);

            // Filter / Search Input
            int filterY = headerY + 14;
            String filterText = "§7Search: §f" + (filterQuery.isEmpty() ? "§8(Type to filter)..." : filterQuery) + "§r";
            graphics.text(font, Component.literal(filterText), boxX + 12, filterY, 0xFFFFFFFF);

            // Agent Tabs
            int tabW = 70;
            int tabH = 16;
            int t1X = boxX + boxWidth - (tabW * 2) - 16;
            int t2X = t1X + tabW + 4;
            drawButton(graphics, t1X, headerY - 2, tabW, tabH, "Claude", showClaude, isHovered(mouseX, mouseY, t1X, headerY - 2, tabW, tabH), 0xFF00D4FF);
            drawButton(graphics, t2X, headerY - 2, tabW, tabH, "Codex", !showClaude, isHovered(mouseX, mouseY, t2X, headerY - 2, tabW, tabH), 0xFF00E676);
        }

        // List Area
        int listX = boxX + 12;
        int listY = boxY + 38;
        int listW = boxWidth - 24;
        int listH = boxHeight - 72;
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0d0f17);

        if (selectedSessionForTimeline != null) {
            renderTimelineView(graphics, mouseX, mouseY, listX, listY, listW, listH);
        } else {
            renderSessionListView(graphics, mouseX, mouseY, listX, listY, listW, listH);
        }

        // Bottom Bar
        int footerY = boxY + boxHeight - 28;
        int btnW = 84;
        int btnH = 18;

        if (selectedSessionForTimeline != null) {
            int b1X = boxX + 12;
            drawButton(graphics, b1X, footerY, btnW, btnH, "Back to List", false, isHovered(mouseX, mouseY, b1X, footerY, btnW, btnH), 0xFF445070);
        } else {
            int b1X = boxX + 12;
            drawButton(graphics, b1X, footerY, btnW, btnH, "New Session", false, isHovered(mouseX, mouseY, b1X, footerY, btnW, btnH), 0xFF00AA77);
        }

        int b2X = boxX + boxWidth - btnW - 12;
        drawButton(graphics, b2X, footerY, btnW, btnH, "Close", false, isHovered(mouseX, mouseY, b2X, footerY, btnW, btnH), 0xFFAA2222);
    }

    private void renderSessionListView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int listX, int listY, int listW, int listH) {
        if (displayedSessions.isEmpty()) {
            graphics.text(font, Component.literal("§7No matching sessions found in local history.§r"), listX + 12, listY + 12, 0xFF888888);
            return;
        }

        int itemH = 28;
        int maxVisible = listH / itemH;
        int startIdx = Math.max(0, scrollOffset);
        int endIdx = Math.min(displayedSessions.size(), startIdx + maxVisible);

        int itemY = listY + 4;
        for (int i = startIdx; i < endIdx; i++) {
            SessionInfo s = displayedSessions.get(i);
            boolean hovered = isHovered(mouseX, mouseY, listX + 2, itemY, listW - 12, itemH - 2);

            int itemBg = hovered ? 0xFF1b2030 : 0xFF121520;
            graphics.fill(listX + 2, itemY, listX + listW - 12, itemY + itemH - 2, itemBg);

            // Pin icon (★ / ☆)
            String star = s.isPinned() ? "§e★ §r" : "§8☆ §r";

            // Title + Short ID
            String title = s.getTitle();
            if (title.length() > 42) title = title.substring(0, 39) + "...";
            graphics.text(font, Component.literal(star + "§f" + title + " §7(§e" + s.getShortId() + "§7)§r"), listX + 8, itemY + 4, 0xFFFFFFFF);

            // Subtitle: Project & Timestamp
            String timeStr = (s.getLastModified() > 0) ? DATE_FORMAT.format(new Date(s.getLastModified())) : "recent";
            String projStr = s.getProject().isEmpty() ? "" : "§7in §b" + s.getProject() + " §7· ";
            graphics.text(font, Component.literal(projStr + "§8" + timeStr + " · " + s.getId()), listX + 8, itemY + 15, 0xFF888888);

            // Action Buttons: [Timeline] [Resume]
            int tlBtnW = 46;
            int rBtnW = 46;
            int rBtnX = listX + listW - rBtnW - 16;
            int tlBtnX = rBtnX - tlBtnW - 4;

            drawButton(graphics, tlBtnX, itemY + 6, tlBtnW, 16, "Turns", false, isHovered(mouseX, mouseY, tlBtnX, itemY + 6, tlBtnW, 16), 0xFF00AAFF);
            drawButton(graphics, rBtnX, itemY + 6, rBtnW, 16, "Resume", false, isHovered(mouseX, mouseY, rBtnX, itemY + 6, rBtnW, 16), 0xFF00AA00);

            itemY += itemH;
        }

        // Scrollbar
        int scrollbarX = listX + listW - 8;
        int scrollbarW = 6;
        graphics.fill(scrollbarX, listY + 2, scrollbarX + scrollbarW, listY + listH - 2, 0xFF1a1c28);

        if (displayedSessions.size() > maxVisible) {
            float thumbRatio = (float) maxVisible / displayedSessions.size();
            int thumbH = Math.max(16, (int) ((listH - 4) * thumbRatio));
            int maxScroll = displayedSessions.size() - maxVisible;
            int thumbY = listY + 2 + (int) (((listH - 4 - thumbH) * ((float) scrollOffset / maxScroll)));
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF445070);
        }
    }

    private void renderTimelineView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<TurnInfo> turns = selectedSessionForTimeline.getTurns();
        if (turns.isEmpty()) {
            graphics.text(font, Component.literal("§7(No turn checkpoint history available for this session)§r"), x + 8, y + 8, 0xFF888888);
            return;
        }

        int itemH = 32;
        int curY = y + 4;
        for (int i = 0; i < Math.min(6, turns.size()); i++) {
            TurnInfo t = turns.get(i);
            boolean hov = isHovered(mouseX, mouseY, x + 2, curY, w - 4, itemH - 2);
            graphics.fill(x + 2, curY, x + w - 2, curY + itemH - 2, hov ? 0xFF1e2840 : 0xFF131826);

            String prompt = t.getPrompt().length() > 40 ? t.getPrompt().substring(0, 37) + "..." : t.getPrompt();
            graphics.text(font, Component.literal("§a✓ §f" + t.getTurnId() + ": §e" + prompt), x + 8, curY + 4, 0xFFFFFFFF);
            graphics.text(font, Component.literal("§8Checkpoint: " + (t.getGitCheckpointCommit().isEmpty() ? "auto" : t.getGitCheckpointCommit())), x + 8, curY + 16, 0xFF888888);

            // [REWIND HERE] [FORK HERE]
            int rBtnW = 64;
            int fBtnW = 54;
            int fBtnX = x + w - fBtnW - 8;
            int rBtnX = fBtnX - rBtnW - 4;

            drawButton(graphics, rBtnX, curY + 6, rBtnW, 16, "Rewind", false, isHovered(mouseX, mouseY, rBtnX, curY + 6, rBtnW, 16), 0xFFFFAA00);
            drawButton(graphics, fBtnX, curY + 6, fBtnW, 16, "Fork", false, isHovered(mouseX, mouseY, fBtnX, curY + 6, fBtnW, 16), 0xFF00AAFF);

            curY += itemH;
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
    public boolean charTyped(CharacterEvent event) {
        if (selectedSessionForTimeline == null && event.isAllowedChatCharacter()) {
            filterQuery += event.codepointAsString();
            scrollOffset = 0;
            refreshSessions();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (selectedSessionForTimeline != null) {
                selectedSessionForTimeline = null;
                return true;
            }
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && selectedSessionForTimeline == null) {
            if (!filterQuery.isEmpty()) {
                filterQuery = filterQuery.substring(0, filterQuery.length() - 1);
                scrollOffset = 0;
                refreshSessions();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            scrollOffset = Math.min(Math.max(0, displayedSessions.size() - 6), scrollOffset + 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 2);
        } else if (vAmount < 0) {
            scrollOffset = Math.min(Math.max(0, displayedSessions.size() - 6), scrollOffset + 2);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        int mx = (int) event.x();
        int my = (int) event.y();

        int boxX = Math.max(10, width / 2 - 270);
        int boxY = Math.max(10, height / 2 - 150);
        int boxWidth = Math.min(width - 20, 540);
        int boxHeight = Math.min(height - 20, 300);

        int listX = boxX + 12;
        int listY = boxY + 38;
        int listW = boxWidth - 24;
        int listH = boxHeight - 72;

        if (selectedSessionForTimeline != null) {
            List<TurnInfo> turns = selectedSessionForTimeline.getTurns();
            int itemH = 32;
            int curY = listY + 4;
            for (int i = 0; i < Math.min(6, turns.size()); i++) {
                TurnInfo t = turns.get(i);
                int rBtnW = 64;
                int fBtnW = 54;
                int fBtnX = listX + listW - fBtnW - 8;
                int rBtnX = fBtnX - rBtnW - 4;

                if (isHovered(mx, my, rBtnX, curY + 6, rBtnW, 16)) {
                    if (showClaude) {
                        AgentManager.getInstance().getClaudeAdapter().rewindToCheckpoint(t.getTurnId());
                    } else {
                        AgentManager.getInstance().getCodexAdapter().rewindToCheckpoint(t.getTurnId());
                    }
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }

                if (isHovered(mx, my, fBtnX, curY + 6, fBtnW, 16)) {
                    if (showClaude) {
                        AgentManager.getInstance().getClaudeAdapter().forkFromTurn(selectedSessionForTimeline.getId(), t.getTurnId());
                    } else {
                        AgentManager.getInstance().getCodexAdapter().forkFromTurn(selectedSessionForTimeline.getId(), t.getTurnId());
                    }
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }
                curY += itemH;
            }
        } else {
            // Header tabs
            int headerY = boxY + 8;
            int tabW = 70;
            int tabH = 16;
            int t1X = boxX + boxWidth - (tabW * 2) - 16;
            int t2X = t1X + tabW + 4;

            if (isHovered(mx, my, t1X, headerY - 2, tabW, tabH)) {
                showClaude = true;
                scrollOffset = 0;
                refreshSessions();
                return true;
            }
            if (isHovered(mx, my, t2X, headerY - 2, tabW, tabH)) {
                showClaude = false;
                scrollOffset = 0;
                refreshSessions();
                return true;
            }

            int itemH = 28;
            int maxVisible = listH / itemH;
            int startIdx = Math.max(0, scrollOffset);
            int endIdx = Math.min(displayedSessions.size(), startIdx + maxVisible);

            int itemY = listY + 4;
            for (int i = startIdx; i < endIdx; i++) {
                SessionInfo s = displayedSessions.get(i);

                // Pin click (left star)
                if (isHovered(mx, my, listX + 4, itemY + 4, 18, 18)) {
                    s.setPinned(!s.isPinned());
                    if (!showClaude) {
                        CodexAppServerClient.getInstance().setPinned(s.getId(), s.isPinned());
                    }
                    return true;
                }

                int tlBtnW = 46;
                int rBtnW = 46;
                int rBtnX = listX + listW - rBtnW - 16;
                int tlBtnX = rBtnX - tlBtnW - 4;

                if (isHovered(mx, my, tlBtnX, itemY + 6, tlBtnW, 16)) {
                    selectedSessionForTimeline = s;
                    return true;
                }

                if (isHovered(mx, my, rBtnX, itemY + 6, rBtnW, 16) || (isHovered(mx, my, listX + 2, itemY, listW - 12, itemH - 2) && isDouble)) {
                    if (showClaude) {
                        AgentManager.getInstance().getClaudeAdapter().resumeSession(s.getId(), s.getTitle());
                    } else {
                        AgentManager.getInstance().getCodexAdapter().resumeSession(s.getId(), s.getTitle());
                    }
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }
                itemY += itemH;
            }
        }

        int footerY = boxY + boxHeight - 28;
        int btnW = 84;
        int btnH = 18;
        int b1X = boxX + 12;
        int b2X = boxX + boxWidth - btnW - 12;

        if (isHovered(mx, my, b1X, footerY, btnW, btnH)) {
            if (selectedSessionForTimeline != null) {
                selectedSessionForTimeline = null;
            } else {
                if (showClaude) {
                    AgentManager.getInstance().getClaudeAdapter().newSession();
                } else {
                    AgentManager.getInstance().getCodexAdapter().newSession();
                }
                Minecraft.getInstance().setScreen(parent);
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
