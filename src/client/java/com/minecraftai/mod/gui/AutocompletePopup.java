package com.minecraftai.mod.gui;

import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutocompletePopup {

    public enum Mode {
        NONE,
        COMMAND, // Starts with /
        FILE,    // Starts with @
        SKILL    // Starts with $
    }

    private Mode mode = Mode.NONE;
    private String query = "";
    private int triggerIndex = -1;
    private final List<String> suggestions = new ArrayList<>();
    private int selectedIndex = 0;

    private static final List<String> BUILTIN_COMMANDS = List.of(
            "/claude ", "/codex ", "/project ", "/ai composer", "/ai model",
            "/ai permissions", "/ai tasks", "/ai rewind", "/ai diff",
            "/ai transcript", "/ai compact", "/ai new", "/ai stop", "/ai clear", "/ai stash"
    );

    private static final List<String> COMMON_SKILLS = List.of(
            "$auth-tester", "$refactor", "$linter", "$test-runner", "$deploy-check", "$doc-gen"
    );

    public boolean isActive() {
        return mode != Mode.NONE && !suggestions.isEmpty();
    }

    public void update(String text, int cursor) {
        if (text.isEmpty() || cursor < 0) {
            mode = Mode.NONE;
            suggestions.clear();
            return;
        }

        int subEnd = Math.min(cursor, text.length());
        String currentToken = "";
        int tokenStart = 0;

        for (int i = subEnd - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                tokenStart = i + 1;
                break;
            }
        }
        currentToken = text.substring(tokenStart, subEnd);

        if (currentToken.startsWith("/")) {
            mode = Mode.COMMAND;
            query = currentToken.substring(1).toLowerCase();
            triggerIndex = tokenStart;
            filterCommands();
        } else if (currentToken.startsWith("@")) {
            mode = Mode.FILE;
            query = currentToken.substring(1).toLowerCase();
            triggerIndex = tokenStart;
            filterFiles();
        } else if (currentToken.startsWith("$")) {
            mode = Mode.SKILL;
            query = currentToken.substring(1).toLowerCase();
            triggerIndex = tokenStart;
            filterSkills();
        } else {
            mode = Mode.NONE;
            suggestions.clear();
        }
    }

    private void filterCommands() {
        suggestions.clear();
        for (String cmd : BUILTIN_COMMANDS) {
            if (cmd.substring(1).toLowerCase().contains(query)) {
                suggestions.add(cmd);
            }
        }
        selectedIndex = Math.min(selectedIndex, Math.max(0, suggestions.size() - 1));
    }

    private void filterFiles() {
        suggestions.clear();
        ProjectProfile active = ProjectManager.getInstance().getActiveProject();
        String dirPath = active != null ? active.getWorkingDirectory() : ConfigManager.getInstance().getConfig().getWorkingDirectory();
        File dir = new File(dirPath);

        if (dir.exists() && dir.isDirectory()) {
            scanFiles(dir, dir, 0);
        }
        selectedIndex = Math.min(selectedIndex, Math.max(0, suggestions.size() - 1));
    }

    private void scanFiles(File base, File current, int depth) {
        if (depth > 4 || suggestions.size() >= 12) return;
        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (suggestions.size() >= 12) break;
            if (f.getName().startsWith(".") || f.getName().equals("node_modules") || f.getName().equals("target") || f.getName().equals("build")) {
                continue;
            }
            String rel = f.getAbsolutePath().substring(base.getAbsolutePath().length()).replace("\\", "/");
            if (rel.startsWith("/")) rel = rel.substring(1);

            if (rel.toLowerCase().contains(query)) {
                suggestions.add("@" + rel + (f.isDirectory() ? "/" : ""));
            }
            if (f.isDirectory()) {
                scanFiles(base, f, depth + 1);
            }
        }
    }

    private void filterSkills() {
        suggestions.clear();
        for (String sk : COMMON_SKILLS) {
            if (sk.substring(1).toLowerCase().contains(query)) {
                suggestions.add(sk);
            }
        }
        selectedIndex = Math.min(selectedIndex, Math.max(0, suggestions.size() - 1));
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int w) {
        if (!isActive()) return;

        int h = Math.min(6, suggestions.size()) * 14 + 6;
        graphics.fill(x, y - h, x + w, y, 0xFF141824);
        graphics.fill(x, y - h, x + w, y - h + 2, mode == Mode.COMMAND ? 0xFF00AAFF : (mode == Mode.FILE ? 0xFF00E676 : 0xFFFFAA00));

        int renderY = y - h + 4;
        for (int i = 0; i < Math.min(6, suggestions.size()); i++) {
            String item = suggestions.get(i);
            boolean selected = (i == selectedIndex);
            if (selected) {
                graphics.fill(x + 2, renderY - 1, x + w - 2, renderY + 12, 0xFF283450);
                graphics.text(font, Component.literal("§b> §f" + item), x + 6, renderY, 0xFFFFFFFF);
            } else {
                graphics.text(font, Component.literal("  §7" + item), x + 6, renderY, 0xFFAAAAAA);
            }
            renderY += 14;
        }
    }

    public boolean handleNavigation(int key) {
        if (!isActive()) return false;
        if (key == 265) { // UP
            if (selectedIndex > 0) selectedIndex--;
            return true;
        }
        if (key == 264) { // DOWN
            if (selectedIndex < suggestions.size() - 1) selectedIndex++;
            return true;
        }
        return false;
    }

    public String applySelection(StringBuilder buffer, int cursor) {
        if (!isActive() || selectedIndex >= suggestions.size() || triggerIndex < 0) return null;
        String chosen = suggestions.get(selectedIndex);

        buffer.delete(triggerIndex, cursor);
        buffer.insert(triggerIndex, chosen);
        mode = Mode.NONE;
        suggestions.clear();
        return chosen;
    }

    public int getTriggerIndex() {
        return triggerIndex;
    }
}
