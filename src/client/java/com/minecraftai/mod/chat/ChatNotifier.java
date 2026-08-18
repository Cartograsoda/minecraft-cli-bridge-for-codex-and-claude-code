package com.minecraftai.mod.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ChatNotifier {

    public static void sendMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (client.gui != null && client.gui.getChat() != null) {
                        client.gui.getChat().addClientSystemMessage(Component.literal(message));
                    } else if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal(message));
                    } else {
                        System.out.println("[ChatHUD] " + message);
                    }
                });
                return;
            }
        } catch (Throwable ignored) {
            // Running in head-less or test environment
        }

        System.out.println("[ChatHUD] " + message);
    }

    public static void sendMessages(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (String msg : messages) {
            sendMessage(msg);
        }
    }

    public static void sendFeedback(String text) {
        sendMessage(ChatColorUtil.AI_PREFIX + text);
    }

    public static void sendError(String error) {
        sendMessage(ChatColorUtil.AI_PREFIX + ChatColorUtil.RED + error + ChatColorUtil.RESET);
    }

    public static void clearChat() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (client.gui != null && client.gui.getChat() != null) {
                        client.gui.getChat().clearMessages(false);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }
}
