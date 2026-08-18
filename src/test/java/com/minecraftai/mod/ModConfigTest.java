package com.minecraftai.mod;

import com.minecraftai.mod.config.ModConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ModConfigTest {

    @Test
    public void testDefaults() {
        ModConfig config = new ModConfig();
        assertNotNull(config.getWorkingDirectory());
        assertEquals("claude", config.getClaudeExecutable());
        assertEquals("codex", config.getCodexExecutable());
        assertTrue(config.isShowActivityEvents());
        assertTrue(config.isShowToolCommands());
        assertEquals(120, config.getMaxChatLineLength());
    }

    @Test
    public void testDirectoryValidation() {
        ModConfig config = new ModConfig();
        assertTrue(config.isValidDirectory(System.getProperty("user.dir")));
        assertFalse(config.isValidDirectory("non_existent_folder_xyz_123"));
    }

    @Test
    public void testAliases() {
        ModConfig config = new ModConfig();
        config.addAlias("app", "/projects/demo-app");
        config.addAlias("web", "/projects/web-frontend");

        assertEquals("/projects/demo-app", config.getAlias("app"));
        assertEquals("/projects/web-frontend", config.getAlias("web"));
        assertEquals("/projects/demo-app", config.resolveDirectory("app"));
        assertEquals("/projects/web-frontend", config.resolveDirectory("web"));
        assertEquals("/other/path", config.resolveDirectory("/other/path"));

        assertTrue(config.removeAlias("app"));
        assertNull(config.getAlias("app"));
    }
}
