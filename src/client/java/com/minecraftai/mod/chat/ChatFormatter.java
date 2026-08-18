package com.minecraftai.mod.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFormatter {

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)|_(.*?)_");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+)$", Pattern.MULTILINE);

    /**
     * Translates standard Markdown styling into Minecraft chat formatting codes.
     */
    public static String markdownToMinecraft(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // Headers -> Bold Yellow
        Matcher headerMatcher = HEADER_PATTERN.matcher(text);
        text = headerMatcher.replaceAll(ChatColorUtil.YELLOW + ChatColorUtil.BOLD + "$1" + ChatColorUtil.RESET);

        // Bullets -> Dot bullet
        Matcher bulletMatcher = BULLET_PATTERN.matcher(text);
        text = bulletMatcher.replaceAll("  §7•§r $1");

        // Bold
        Matcher boldMatcher = BOLD_PATTERN.matcher(text);
        text = boldMatcher.replaceAll(ChatColorUtil.BOLD + "$1" + ChatColorUtil.RESET);

        // Inline code -> Yellow
        Matcher codeMatcher = INLINE_CODE_PATTERN.matcher(text);
        text = codeMatcher.replaceAll(ChatColorUtil.YELLOW + "$1" + ChatColorUtil.RESET);

        // Italic
        Matcher italicMatcher = ITALIC_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (italicMatcher.find()) {
            String match = italicMatcher.group(1) != null ? italicMatcher.group(1) : italicMatcher.group(2);
            italicMatcher.appendReplacement(sb, Matcher.quoteReplacement(ChatColorUtil.ITALIC + match + ChatColorUtil.RESET));
        }
        italicMatcher.appendTail(sb);
        text = sb.toString();

        return text;
    }

    public static String formatActivity(String agentName, String action) {
        return ChatColorUtil.getPrefixForAgent(agentName) + ChatColorUtil.GRAY + action + ChatColorUtil.RESET;
    }

    public static String formatCommand(String agentName, String command) {
        return ChatColorUtil.getPrefixForAgent(agentName) + ChatColorUtil.YELLOW + "Running " + ChatColorUtil.GRAY + command + ChatColorUtil.RESET;
    }

    public static String formatSuccess(String agentName, String message) {
        return ChatColorUtil.getPrefixForAgent(agentName) + ChatColorUtil.GREEN + "✓ " + message + ChatColorUtil.RESET;
    }

    public static String formatError(String agentName, String error) {
        return ChatColorUtil.getPrefixForAgent(agentName) + ChatColorUtil.RED + "✗ " + error + ChatColorUtil.RESET;
    }

    public static String formatDone(String agentName, String summary) {
        if (summary != null && !summary.isBlank()) {
            return ChatColorUtil.getPrefixForAgent(agentName) + ChatColorUtil.GREEN + "Done — " + ChatColorUtil.WHITE + summary + ChatColorUtil.RESET;
        }
        return ChatColorUtil.getPrefixForAgent(agentName) + ChatColorUtil.GREEN + "Done." + ChatColorUtil.RESET;
    }

    /**
     * Splits a long multiline or long paragraph message into lines suitable for Minecraft chat.
     */
    public static List<String> splitMessage(String prefix, String text, int maxLineLength) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        int limit = maxLineLength > 20 ? maxLineLength : 120;
        String[] lines = text.split("\r?\n");

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i].trim();
            if (rawLine.isEmpty()) {
                continue;
            }

            String currentPrefix = (i == 0 && prefix != null) ? prefix : (prefix != null ? "  " : "");
            String formattedLine = markdownToMinecraft(rawLine);

            if (currentPrefix.length() + formattedLine.length() <= limit) {
                result.add(currentPrefix + formattedLine);
            } else {
                List<String> wrapped = wrapLine(currentPrefix, formattedLine, limit);
                result.addAll(wrapped);
            }
        }

        return result;
    }

    private static List<String> wrapLine(String firstPrefix, String line, int limit) {
        List<String> lines = new ArrayList<>();
        String[] words = line.split(" ");
        StringBuilder current = new StringBuilder(firstPrefix);

        for (String word : words) {
            if (current.length() + word.length() + 1 > limit) {
                if (current.length() > firstPrefix.length()) {
                    lines.add(current.toString());
                    current = new StringBuilder("  ").append(word);
                } else {
                    // Single word is longer than limit
                    current.append(word);
                    lines.add(current.toString());
                    current = new StringBuilder("  ");
                }
            } else {
                if (current.length() > 0 && !current.toString().endsWith(" ") && !current.toString().endsWith("  ")) {
                    current.append(" ");
                }
                current.append(word);
            }
        }

        if (current.length() > 0 && !current.toString().isBlank() && !current.toString().equals("  ")) {
            lines.add(current.toString());
        }

        return lines;
    }
}
