package com.minecraftai.mod.hud;

import com.minecraftai.mod.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class HudNotificationManager {

    public static void notifyCompletion(String agentName, String summary) {
        HudConfig hudConfig = ConfigManager.getInstance().getConfig().getHud();

        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;

            client.execute(() -> {
                if (hudConfig.isCompletionSound() && client.getSoundManager() != null) {
                    try {
                        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F));
                    } catch (Throwable ignored) {
                    }
                }

                if (hudConfig.isShowActionBar() && client.player != null) {
                    String text = "§a" + agentName + " ✓ " + (summary != null && !summary.isBlank() ? summary : "Done");
                    client.player.sendOverlayMessage(Component.literal(text));
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static void notifyWaitingForInput(String agentName, String action) {
        HudConfig hudConfig = ConfigManager.getInstance().getConfig().getHud();

        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;

            client.execute(() -> {
                if (hudConfig.isWaitingSound() && client.getSoundManager() != null) {
                    try {
                        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F, 1.0F));
                    } catch (Throwable ignored) {
                    }
                }

                if (hudConfig.isShowActionBar() && client.player != null) {
                    String text = "§c⚠ " + agentName + " Input Required: " + (action != null ? action : "Needs Approval");
                    client.player.sendOverlayMessage(Component.literal(text));
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
