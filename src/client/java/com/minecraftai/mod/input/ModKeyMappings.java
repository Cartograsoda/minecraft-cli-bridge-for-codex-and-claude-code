package com.minecraftai.mod.input;

import com.minecraftai.mod.agent.AgentInstance;
import com.minecraftai.mod.agent.AgentManager;
import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.gui.AiComposerScreen;
import com.minecraftai.mod.gui.TranscriptViewerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {

    public static KeyMapping keyComposer;
    public static KeyMapping keyInterrupt;
    public static KeyMapping keyPermission;
    public static KeyMapping keyTranscript;
    public static KeyMapping keyHud;

    private static boolean wasCtrlTabDown = false;

    public static void register() {
        keyComposer = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.claude_codex_chat.composer",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                KeyMapping.Category.MISC
        ));

        keyInterrupt = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.claude_codex_chat.interrupt",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyMapping.Category.MISC
        ));

        keyPermission = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.claude_codex_chat.permission",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                KeyMapping.Category.MISC
        ));

        keyTranscript = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.claude_codex_chat.transcript",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_T,
                KeyMapping.Category.MISC
        ));

        keyHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.claude_codex_chat.hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Handle in-game Ctrl+Tab agent instance cycling
            if (client.getWindow() != null) {
                long window = client.getWindow().handle();
                boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                        GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
                boolean tab = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;

                if (ctrl && tab) {
                    if (!wasCtrlTabDown && client.screen == null) {
                        AgentManager.getInstance().cycleNextInstance();
                        wasCtrlTabDown = true;
                    }
                } else {
                    wasCtrlTabDown = false;
                }
            }

            while (keyComposer.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new AiComposerScreen());
                }
            }

            while (keyInterrupt.consumeClick()) {
                // K key interrupts only the currently focused agent instance
                AgentInstance focused = AgentManager.getInstance().getFocusedInstance();
                if (focused != null) {
                    focused.interrupt();
                }
            }

            while (keyPermission.consumeClick()) {
                AgentInstance focused = AgentManager.getInstance().getFocusedInstance();
                if (focused != null) {
                    focused.cyclePermissionMode();
                }
            }

            while (keyTranscript.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new TranscriptViewerScreen(null, AgentManager.getInstance().getFocusedInstance()));
                }
            }

            while (keyHud.consumeClick()) {
                ConfigManager.getInstance().getConfig().getHud().cycleMode();
                ConfigManager.getInstance().save();
                String modeName = ConfigManager.getInstance().getConfig().getHud().getMode().name();
                ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§7HUD mode set to: §e" + modeName);
            }
        });
    }

    public static boolean isAltModifierHeld() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        long window = client.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }
}
