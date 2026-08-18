package com.minecraftai.mod;

import com.minecraftai.mod.agent.AgentManager;
import com.minecraftai.mod.agent.CapabilityDetector;
import com.minecraftai.mod.command.AiCommands;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.hud.HudRenderer;
import com.minecraftai.mod.input.ModKeyMappings;
import com.minecraftai.mod.input.PromptHistory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class ClaudeCodexMod implements ClientModInitializer {

    public static final String MOD_ID = "claude_codex_chat";

    @Override
    public void onInitializeClient() {
        System.out.println("[ClaudeCodexMod] Initializing Claude & Codex AI Frontend for Minecraft 26.1.2...");

        // 1. Load Configurations & History
        ConfigManager.getInstance().load();
        PromptHistory.getInstance().load();

        // 2. Probe Capabilities asynchronously
        CapabilityDetector.getInstance().detectAsync();

        // 3. Register Key Mappings & HUD Renderer
        ModKeyMappings.register();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud_overlay"), new HudRenderer());

        // 4. Register Client Commands
        AiCommands.register();

        // 5. Register JVM shutdown hook to clean up spawned CLI processes
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[ClaudeCodexMod] Shutting down agent processes and background tasks...");
            try {
                AgentManager.getInstance().shutdown();
            } catch (Throwable ignored) {}
            try {
                com.minecraftai.mod.codex.CodexAppServerClient.getInstance().shutdown();
            } catch (Throwable ignored) {}
            try {
                com.minecraftai.mod.task.TaskManager.getInstance().shutdown();
            } catch (Throwable ignored) {}
        }));

        System.out.println("[ClaudeCodexMod] Initialization complete.");
    }
}
