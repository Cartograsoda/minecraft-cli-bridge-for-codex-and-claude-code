package com.minecraftai.mod.command;

import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Collection;

public class ProjectCommands {

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        ProjectManager pm = ProjectManager.getInstance();

        dispatcher.register(ClientCommands.literal("project")
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .then(ClientCommands.argument("path", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            String path = StringArgumentType.getString(context, "path");
                                            if (pm.addProject(name, path)) {
                                                ChatNotifier.sendFeedback("§aAdded project profile '§f" + name + "§a' -> §f" + path);
                                                return 1;
                                            } else {
                                                ChatNotifier.sendError("Failed to add project. Ensure directory exists.");
                                                return 0;
                                            }
                                        })
                                )
                        )
                )
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    return SharedSuggestionProvider.suggest(
                                            pm.getAllProjects().stream().map(ProjectProfile::getName),
                                            builder
                                    );
                                })
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    if (pm.removeProject(name)) {
                                        ChatNotifier.sendFeedback("§aRemoved project profile: §f" + name);
                                        return 1;
                                    } else {
                                        ChatNotifier.sendError("Cannot remove project: " + name);
                                        return 0;
                                    }
                                })
                        )
                )
                .then(ClientCommands.literal("list")
                        .executes(context -> listProjects())
                )
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            return SharedSuggestionProvider.suggest(
                                    pm.getAllProjects().stream().map(ProjectProfile::getName),
                                    builder
                                );
                        })
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return pm.switchProject(name) ? 1 : 0;
                        })
                )
                .executes(context -> listProjects())
        );
    }

    private static int listProjects() {
        ProjectManager pm = ProjectManager.getInstance();
        Collection<ProjectProfile> list = pm.getAllProjects();
        ProjectProfile active = pm.getActiveProject();
        String activeName = active != null ? active.getName() : "";

        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§6=== Project Profiles ===");
        for (ProjectProfile p : list) {
            boolean isActive = p.getName().equalsIgnoreCase(activeName);
            String dot = isActive ? "§a● §f" : "§7○ §f";
            ChatNotifier.sendMessage(dot + "§l" + p.getName() + "§r -> §f" + p.getWorkingDirectory() +
                    (isActive ? " §a(active)§r" : " §7(/project " + p.getName() + ")§r"));
        }
        ChatNotifier.sendMessage("§7Switch projects with: §f/project <name>");
        return 1;
    }
}
