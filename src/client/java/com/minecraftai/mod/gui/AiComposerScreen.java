package com.minecraftai.mod.gui;

import com.minecraftai.mod.agent.AgentAdapter;
import com.minecraftai.mod.agent.AgentInstance;
import com.minecraftai.mod.agent.AgentManager;
import com.minecraftai.mod.input.PromptHistory;
import com.minecraftai.mod.input.PromptStash;
import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.shell.ShellExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AiComposerScreen extends Screen {

    private final StringBuilder textBuffer = new StringBuilder();
    private int cursorPosition = 0;

    private boolean searchMode = false;
    private String searchQuery = "";
    private final List<String> searchResults = new ArrayList<>();
    private int searchSelectedIndex = 0;

    private final AutocompletePopup autocomplete = new AutocompletePopup();
    private String ghostSuggestion = "Run the full test suite and inspect failures";

    public AiComposerScreen() {
        super(Component.literal("AI Composer"));
    }

    @Override
    protected void init() {
        super.init();
        PromptHistory.getInstance().resetPointer();
        loadDraftForFocusedAgent();
    }

    private void loadDraftForFocusedAgent() {
        ProjectProfile project = ProjectManager.getInstance().getActiveProject();
        String projName = project != null ? project.getName() : "default";
        AgentInstance agent = AgentManager.getInstance().getFocusedInstance();
        String sessKey = agent != null ? (agent.getAgentName() + ":" + agent.getSessionId()) : "new";

        String draft = PromptStash.getInstance().getDraft(projName, sessKey);
        textBuffer.setLength(0);
        if (!draft.isEmpty()) {
            textBuffer.append(draft);
        }
        cursorPosition = textBuffer.length();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public AgentInstance getSelectedAgent() {
        return AgentManager.getInstance().getFocusedInstance();
    }

    @Override
    public void onClose() {
        saveCurrentDraft();
        super.onClose();
    }

    private void saveCurrentDraft() {
        ProjectProfile project = ProjectManager.getInstance().getActiveProject();
        String projName = project != null ? project.getName() : "default";
        AgentInstance agent = getSelectedAgent();
        if (agent != null) {
            String sessKey = agent.getAgentName() + ":" + agent.getSessionId();
            PromptStash.getInstance().saveDraft(projName, sessKey, textBuffer.toString());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xAA0a0c14);

        int boxX = Math.max(10, width / 2 - 270);
        int boxY = Math.max(10, height / 2 - 150);
        int boxWidth = Math.min(width - 20, 540);
        int boxHeight = Math.min(height - 20, 300);

        AgentInstance agent = getSelectedAgent();
        boolean isClaude = (agent != null && "Claude".equalsIgnoreCase(agent.getProviderType()));
        boolean isShellMode = textBuffer.toString().startsWith("!");
        int topAccent = isShellMode ? 0xFFFF8800 : (isClaude ? 0xFF00D4FF : 0xFF00E676);

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF141724);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, topAccent);

        ProjectProfile project = ProjectManager.getInstance().getActiveProject();

        // 1. Top Bar Information
        int headerY = boxY + 8;
        String instTag = (agent != null) ?
                (isClaude ? "§b[" + agent.getAgentName() + "]§r" : "§2[" + agent.getAgentName() + "]§r") : "§7[AI]§r";
        String wsTag = (agent != null && agent.isIsolated()) ? "§e(isolated)§r" : "§8(shared)§r";
        String projTag = "§7Proj: §f" + (project != null ? project.getName() : "default");
        String modelTag = (agent != null) ? "§e" + agent.getModel() + " (" + agent.getEffort().toUpperCase() + ")" : "";
        String permTag = (agent != null) ? "§c[" + agent.getPermissionMode().toUpperCase() + "]§r" : "";

        graphics.text(font, Component.literal(instTag + " " + wsTag + "  " + projTag + "  " + modelTag + "  " + permTag), boxX + 12, headerY, 0xFFFFFFFF);

        // Subtitle instructions
        int subY = headerY + 12;
        String steerOrSend = (agent != null && agent.getStatus().isRunning()) ? "§e[Enter] Steer Run§r" : "§a[Enter] Send§r";
        List<AgentInstance> allInst = AgentManager.getInstance().getAllInstances();
        int curIdx = allInst.indexOf(agent) + 1;
        String switchTip = "  §7[Ctrl+Tab] Switch Target (" + curIdx + "/" + allInst.size() + ")  [Ctrl+S] Stash  [Ctrl+R] Search§r";
        graphics.text(font, Component.literal(steerOrSend + switchTip), boxX + 12, subY, 0xFF888888);

        int currentY = subY + 14;

        // 2. Away Recap (if available)
        String awayRecap = (agent != null) ? agent.getAwayRecap() : null;
        if (awayRecap != null) {
            int recapH = 22;
            graphics.fill(boxX + 12, currentY, boxX + boxWidth - 12, currentY + recapH, 0xFF1f2d40);
            graphics.fill(boxX + 12, currentY, boxX + 14, currentY + recapH, 0xFF00AAFF);
            String recapShort = "§bWHILE YOU WERE AWAY: §f" + (awayRecap.length() > 56 ? awayRecap.substring(0, 53) + "..." : awayRecap);
            graphics.text(font, Component.literal(recapShort), boxX + 18, currentY + 6, 0xFFFFFFFF);

            int disW = 44;
            int disX = boxX + boxWidth - disW - 16;
            drawSimpleButton(graphics, disX, currentY + 3, disW, 16, "Dismiss", isHovered(mouseX, mouseY, disX, currentY + 3, disW, 16), 0xFF445070);
            currentY += recapH + 4;
        }

        // 3. Queue Drawer Panel
        List<String> queue = (agent != null) ? agent.getPromptQueue() : List.of();
        if (!queue.isEmpty()) {
            int qHeight = 20;
            graphics.fill(boxX + 12, currentY, boxX + boxWidth - 12, currentY + qHeight, 0xFF181b26);
            String qHeader = "§6QUEUED (" + queue.size() + "): §f" + (queue.get(0).length() > 28 ? queue.get(0).substring(0, 25) + "..." : queue.get(0));
            graphics.text(font, Component.literal(qHeader), boxX + 16, currentY + 5, 0xFFFFFFFF);

            int editW = 34;
            int editX = boxX + boxWidth - (editW * 2) - 20;
            drawSimpleButton(graphics, editX, currentY + 2, editW, 16, "Edit", isHovered(mouseX, mouseY, editX, currentY + 2, editW, 16), 0xFF00AA77);

            int dropX = editX + editW + 4;
            drawSimpleButton(graphics, dropX, currentY + 2, 20, 16, "×", isHovered(mouseX, mouseY, dropX, currentY + 2, 20, 16), 0xFFAA2222);

            currentY += qHeight + 4;
        }

        // 4. Editor Text Area Box
        int textX = boxX + 12;
        int textY = currentY;
        int textW = boxWidth - 24;
        int textH = boxY + boxHeight - 34 - textY;
        graphics.fill(textX, textY, textX + textW, textY + textH, 0xFF0d0f17);

        if (searchMode) {
            renderSearchOverlay(graphics, textX, textY, textW, textH);
        } else {
            renderEditorText(graphics, textX + 6, textY + 6, textW - 12, textH - 12, isClaude);
        }

        // Autocomplete
        if (autocomplete.isActive()) {
            autocomplete.render(graphics, font, textX + 6, textY + textH, textW - 12);
        }

        // 5. Bottom Buttons
        int footerY = boxY + boxHeight - 28;
        int btnW = 56;
        int btnH = 18;

        int b1X = boxX + 12;
        if (agent != null && agent.getStatus().isRunning()) {
            drawSimpleButton(graphics, b1X, footerY, 70, btnH, "Steer Now", isHovered(mouseX, mouseY, b1X, footerY, 70, btnH), 0xFFFFAA00);
        } else {
            drawSimpleButton(graphics, b1X, footerY, btnW, btnH, "Send", isHovered(mouseX, mouseY, b1X, footerY, btnW, btnH), 0xFF00AA00);
        }

        int b2X = b1X + (agent != null && agent.getStatus().isRunning() ? 74 : btnW + 4);
        drawSimpleButton(graphics, b2X, footerY, 66, btnH, "Queue After", isHovered(mouseX, mouseY, b2X, footerY, 66, btnH), 0xFF0077AA);

        int b3X = b2X + 70;
        drawSimpleButton(graphics, b3X, footerY, btnW, btnH, "Model", isHovered(mouseX, mouseY, b3X, footerY, btnW, btnH), 0xFF6644AA);

        int b4X = b3X + btnW + 4;
        drawSimpleButton(graphics, b4X, footerY, btnW, btnH, "Tasks", isHovered(mouseX, mouseY, b4X, footerY, btnW, btnH), 0xFF00AAFF);

        int b5X = b4X + btnW + 4;
        drawSimpleButton(graphics, b5X, footerY, btnW, btnH, "Sessions", isHovered(mouseX, mouseY, b5X, footerY, btnW, btnH), 0xFF444455);

        int b6X = b5X + btnW + 4;
        drawSimpleButton(graphics, b6X, footerY, btnW, btnH, "Projects", isHovered(mouseX, mouseY, b6X, footerY, btnW, btnH), 0xFF444455);

        int b7X = b6X + btnW + 4;
        drawSimpleButton(graphics, b7X, footerY, btnW, btnH, "Mode", isHovered(mouseX, mouseY, b7X, footerY, btnW, btnH), 0xFF664422);

        int b8X = boxX + boxWidth - btnW - 12;
        drawSimpleButton(graphics, b8X, footerY, btnW, btnH, "Close", isHovered(mouseX, mouseY, b8X, footerY, btnW, btnH), 0xFFAA2222);
    }

    private void renderEditorText(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean isClaude) {
        String text = textBuffer.toString();
        if (text.isEmpty()) {
            graphics.text(font, Component.literal("§7Type coding prompt, !command, /cmd, @file, $skill...§r"), x, y, 0xFF888888);
            if (ghostSuggestion != null) {
                graphics.text(font, Component.literal("§8> " + ghostSuggestion + " [Tab]"), x, y + 14, 0xFF555566);
            }
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                graphics.fill(x, y, x + 2, y + 10, isClaude ? 0xFF00D4FF : 0xFF00E676);
            }
            return;
        }

        String[] lines = text.split("\n", -1);
        int curLine = 0;
        int curCol = 0;
        for (int i = 0; i < textBuffer.length(); i++) {
            if (i == cursorPosition) break;
            if (textBuffer.charAt(i) == '\n') {
                curLine++;
                curCol = 0;
            } else {
                curCol++;
            }
        }

        int renderY = y;
        for (int l = 0; l < lines.length; l++) {
            if (renderY > y + h - 10) break;
            String lineStr = lines[l];
            graphics.text(font, Component.literal(lineStr), x, renderY, 0xFFFFFFFF);

            if (l == curLine && (System.currentTimeMillis() / 500) % 2 == 0) {
                String sub = lineStr.substring(0, Math.min(curCol, lineStr.length()));
                int cursorX = x + font.width(Component.literal(sub));
                graphics.fill(cursorX, renderY, cursorX + 2, renderY + 10, isClaude ? 0xFF00D4FF : 0xFF00E676);
            }
            renderY += 12;
        }
    }

    private void renderSearchOverlay(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF181b26);
        graphics.text(font, Component.literal("§eReverse History Search (Ctrl+R): §f" + searchQuery + "§r_"), x + 8, y + 8, 0xFFFFFFFF);

        int resY = y + 26;
        if (searchResults.isEmpty()) {
            graphics.text(font, Component.literal("§7(No matching prompts found)§r"), x + 8, resY, 0xFF888888);
        } else {
            for (int i = 0; i < Math.min(8, searchResults.size()); i++) {
                String res = searchResults.get(i);
                boolean selected = (i == searchSelectedIndex);
                if (selected) {
                    graphics.fill(x + 4, resY - 2, x + w - 4, resY + 11, 0xFF283450);
                    graphics.text(font, Component.literal("§b> §f" + res), x + 8, resY, 0xFFFFFFFF);
                } else {
                    graphics.text(font, Component.literal("  §7" + res), x + 8, resY, 0xFFAAAAAA);
                }
                resY += 14;
            }
        }
        graphics.text(font, Component.literal("§7[Enter] Select   [Up/Down] Navigate   [Esc] Cancel§r"), x + 8, y + h - 14, 0xFF777777);
    }

    private void drawSimpleButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean hovered, int accent) {
        int bg = hovered ? 0xFF282c3c : 0xFF1b1e2a;
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y + h - 2, x + w, y + h, accent);
        int textW = font.width(Component.literal(label));
        graphics.text(font, Component.literal(label), x + (w - textW) / 2, y + (h - 8) / 2, hovered ? 0xFFFFFFFF : 0xFFDDDDDD);
    }

    private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchMode) {
            searchQuery += event.codepointAsString();
            updateSearchResults();
            return true;
        }

        if (event.isAllowedChatCharacter() || event.codepoint() == ' ') {
            textBuffer.insert(cursorPosition, event.codepointAsString());
            cursorPosition++;
            autocomplete.update(textBuffer.toString(), cursorPosition);
            saveCurrentDraft();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;

        // Ctrl+Tab -> Cycle active agent instances
        if (ctrl && key == GLFW.GLFW_KEY_TAB) {
            saveCurrentDraft();
            AgentManager.getInstance().cycleNextInstance();
            loadDraftForFocusedAgent();
            return true;
        }

        if (autocomplete.isActive()) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                if (autocomplete.handleNavigation(key)) return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_TAB) {
                String applied = autocomplete.applySelection(textBuffer, cursorPosition);
                if (applied != null) {
                    cursorPosition = autocomplete.getTriggerIndex() + applied.length();
                    saveCurrentDraft();
                    return true;
                }
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                autocomplete.update("", 0);
                return true;
            }
        }

        if (searchMode) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchMode = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!searchResults.isEmpty() && searchSelectedIndex < searchResults.size()) {
                    textBuffer.setLength(0);
                    textBuffer.append(searchResults.get(searchSelectedIndex));
                    cursorPosition = textBuffer.length();
                }
                searchMode = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                if (searchSelectedIndex > 0) searchSelectedIndex--;
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                if (searchSelectedIndex < searchResults.size() - 1) searchSelectedIndex++;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    updateSearchResults();
                }
                return true;
            }
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_R) {
            searchMode = true;
            searchQuery = "";
            searchSelectedIndex = 0;
            updateSearchResults();
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_S) {
            ProjectProfile proj = ProjectManager.getInstance().getActiveProject();
            String pName = proj != null ? proj.getName() : "default";
            if (textBuffer.length() > 0) {
                PromptStash.getInstance().pushNamedStash(pName, textBuffer.toString());
                textBuffer.setLength(0);
                cursorPosition = 0;
                saveCurrentDraft();
            } else {
                String popped = PromptStash.getInstance().popNamedStash(pName);
                if (popped != null) {
                    textBuffer.append(popped);
                    cursorPosition = textBuffer.length();
                }
            }
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_M) {
            AgentInstance inst = getSelectedAgent();
            Minecraft.getInstance().setScreen(new ModelPickerScreen(this, inst == null || "Claude".equalsIgnoreCase(inst.getProviderType())));
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_V) {
            try {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard != null && !clipboard.isBlank()) {
                    textBuffer.insert(cursorPosition, clipboard);
                    cursorPosition += clipboard.length();
                    saveCurrentDraft();
                }
            } catch (Exception ignored) {
            }
            return true;
        }

        // Tab -> Accept suggestion if empty
        if (key == GLFW.GLFW_KEY_TAB) {
            if (textBuffer.length() == 0 && ghostSuggestion != null) {
                textBuffer.append(ghostSuggestion);
                cursorPosition = textBuffer.length();
                return true;
            }
        }

        // Enter -> Send or Steer or Shell
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (ctrl) {
                textBuffer.insert(cursorPosition, "\n");
                cursorPosition++;
                saveCurrentDraft();
                return true;
            }
            sendCurrentPrompt(false);
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPosition > 0 && textBuffer.length() > 0) {
                textBuffer.deleteCharAt(cursorPosition - 1);
                cursorPosition--;
                autocomplete.update(textBuffer.toString(), cursorPosition);
                saveCurrentDraft();
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_DELETE) {
            if (cursorPosition < textBuffer.length()) {
                textBuffer.deleteCharAt(cursorPosition);
                autocomplete.update(textBuffer.toString(), cursorPosition);
                saveCurrentDraft();
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT) {
            if (cursorPosition > 0) cursorPosition--;
            autocomplete.update(textBuffer.toString(), cursorPosition);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (cursorPosition < textBuffer.length()) cursorPosition++;
            autocomplete.update(textBuffer.toString(), cursorPosition);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            String prev = PromptHistory.getInstance().getPrevious(ProjectManager.getInstance().getActiveProject().getName());
            if (prev != null) {
                textBuffer.setLength(0);
                textBuffer.append(prev);
                cursorPosition = textBuffer.length();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            String next = PromptHistory.getInstance().getNext(ProjectManager.getInstance().getActiveProject().getName());
            if (next != null) {
                textBuffer.setLength(0);
                textBuffer.append(next);
                cursorPosition = textBuffer.length();
            }
            return true;
        }

        return super.keyPressed(event);
    }

    private void updateSearchResults() {
        searchResults.clear();
        String proj = ProjectManager.getInstance().getActiveProject().getName();
        searchResults.addAll(PromptHistory.getInstance().search(proj, searchQuery));
        searchSelectedIndex = 0;
    }

    private void sendCurrentPrompt(boolean isQueue) {
        String prompt = textBuffer.toString().trim();
        if (prompt.isEmpty()) return;

        if (prompt.startsWith("!")) {
            ShellExecutor.executeCommand(prompt);
            textBuffer.setLength(0);
            cursorPosition = 0;
            saveCurrentDraft();
            onClose();
            return;
        }

        AgentInstance agent = getSelectedAgent();
        if (agent != null) {
            String proj = ProjectManager.getInstance().getActiveProject().getName();
            PromptHistory.getInstance().addPrompt(proj, prompt);

            boolean steer = agent.getStatus().isRunning() && !isQueue;
            agent.sendPrompt(prompt, steer, isQueue);
        }

        textBuffer.setLength(0);
        cursorPosition = 0;
        saveCurrentDraft();
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        int mx = (int) event.x();
        int my = (int) event.y();

        int boxX = Math.max(10, width / 2 - 270);
        int boxY = Math.max(10, height / 2 - 150);
        int boxWidth = Math.min(width - 20, 540);
        int boxHeight = Math.min(height - 20, 300);

        AgentInstance agent = getSelectedAgent();

        if (agent != null && agent.getAwayRecap() != null) {
            int currentY = boxY + 34;
            int disW = 44;
            int disX = boxX + boxWidth - disW - 16;
            if (isHovered(mx, my, disX, currentY + 3, disW, 16)) {
                agent.clearAwayRecap();
                return true;
            }
        }

        List<String> queue = (agent != null) ? agent.getPromptQueue() : List.of();
        if (!queue.isEmpty()) {
            int qY = boxY + 34 + (agent.getAwayRecap() != null ? 26 : 0);
            int editW = 34;
            int editX = boxX + boxWidth - (editW * 2) - 20;
            int dropX = editX + editW + 4;

            if (isHovered(mx, my, editX, qY + 2, editW, 16)) {
                String taken = agent.takeBackQueuedPrompt(0);
                if (taken != null) {
                    textBuffer.setLength(0);
                    textBuffer.append(taken);
                    cursorPosition = textBuffer.length();
                    saveCurrentDraft();
                }
                return true;
            }
            if (isHovered(mx, my, dropX, qY + 2, 20, 16)) {
                agent.removeQueuedPrompt(0);
                return true;
            }
        }

        int footerY = boxY + boxHeight - 28;
        int btnW = 56;
        int btnH = 18;

        int b1X = boxX + 12;
        int b2X = b1X + (agent != null && agent.getStatus().isRunning() ? 74 : btnW + 4);
        int b3X = b2X + 70;
        int b4X = b3X + btnW + 4;
        int b5X = b4X + btnW + 4;
        int b6X = b5X + btnW + 4;
        int b7X = b6X + btnW + 4;
        int b8X = boxX + boxWidth - btnW - 12;

        if (isHovered(mx, my, b1X, footerY, agent != null && agent.getStatus().isRunning() ? 70 : btnW, btnH)) {
            sendCurrentPrompt(false);
            return true;
        }
        if (isHovered(mx, my, b2X, footerY, 66, btnH)) {
            sendCurrentPrompt(true);
            return true;
        }
        if (isHovered(mx, my, b3X, footerY, btnW, btnH)) {
            boolean isClaude = (agent == null || "Claude".equalsIgnoreCase(agent.getProviderType()));
            Minecraft.getInstance().setScreen(new ModelPickerScreen(this, isClaude));
            return true;
        }
        if (isHovered(mx, my, b4X, footerY, btnW, btnH)) {
            Minecraft.getInstance().setScreen(new TasksScreen(this));
            return true;
        }
        if (isHovered(mx, my, b5X, footerY, btnW, btnH)) {
            Minecraft.getInstance().setScreen(new SessionBrowserScreen(this));
            return true;
        }
        if (isHovered(mx, my, b6X, footerY, btnW, btnH)) {
            Minecraft.getInstance().setScreen(new ProjectPickerScreen(this));
            return true;
        }
        if (isHovered(mx, my, b7X, footerY, btnW, btnH)) {
            if (agent != null) agent.cyclePermissionMode();
            return true;
        }
        if (isHovered(mx, my, b8X, footerY, btnW, btnH)) {
            onClose();
            return true;
        }

        // Top bar instance switch click
        if (isHovered(mx, my, boxX + 12, boxY + 6, 120, 16)) {
            saveCurrentDraft();
            AgentManager.getInstance().cycleNextInstance();
            loadDraftForFocusedAgent();
            return true;
        }

        return super.mouseClicked(event, isDouble);
    }
}
