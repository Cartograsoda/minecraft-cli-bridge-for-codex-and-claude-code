package com.minecraftai.mod;

import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatFormatter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChatFormatterTest {

    @Test
    public void testMarkdownFormatting() {
        String input = "**bold text** and *italic text* and `inline code`";
        String formatted = ChatFormatter.markdownToMinecraft(input);

        assertTrue(formatted.contains(ChatColorUtil.BOLD + "bold text" + ChatColorUtil.RESET));
        assertTrue(formatted.contains(ChatColorUtil.ITALIC + "italic text" + ChatColorUtil.RESET));
        assertTrue(formatted.contains(ChatColorUtil.YELLOW + "inline code" + ChatColorUtil.RESET));
    }

    @Test
    public void testActivityFormatting() {
        String msg = ChatFormatter.formatActivity("Claude", "Reading src/auth.ts");
        assertEquals(ChatColorUtil.CLAUDE_PREFIX + ChatColorUtil.GRAY + "Reading src/auth.ts" + ChatColorUtil.RESET, msg);
    }

    @Test
    public void testSuccessFormatting() {
        String msg = ChatFormatter.formatSuccess("Codex", "84 tests passed");
        assertEquals(ChatColorUtil.CODEX_PREFIX + ChatColorUtil.GREEN + "✓ 84 tests passed" + ChatColorUtil.RESET, msg);
    }

    @Test
    public void testDoneFormatting() {
        String msg = ChatFormatter.formatDone("Codex", "fixed refresh-token rotation.");
        assertEquals(ChatColorUtil.CODEX_PREFIX + ChatColorUtil.GREEN + "Done — " + ChatColorUtil.WHITE + "fixed refresh-token rotation." + ChatColorUtil.RESET, msg);
    }

    @Test
    public void testMessageSplitting() {
        String text = "Line 1\nLine 2\nLine 3 with very long content that exceeds thirty characters limit";
        List<String> lines = ChatFormatter.splitMessage(ChatColorUtil.CLAUDE_PREFIX, text, 35);

        assertTrue(lines.size() >= 3);
        assertTrue(lines.get(0).startsWith(ChatColorUtil.CLAUDE_PREFIX));
        for (String line : lines) {
            assertTrue(line.length() <= 50); // Allowing for color codes
        }
    }
}
