package com.minecraftai.mod.command;

import com.minecraftai.mod.agent.*;
import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.config.ConfigManager;
import com.minecraftai.mod.hud.HudConfig;
import com.minecraftai.mod.input.PromptStash;
import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.shell.ShellExecutor;
import com.minecraftai.mod.workspace.WorkspaceMode;
import com.minecraftai.mod.workspace.WorktreeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AiCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerCommands(dispatcher);
            ProjectCommands.registerCommands(dispatcher);
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        AgentManager agentManager = AgentManager.getInstance();

        // 1. /claude <prompt> & /c <prompt>
        dispatcher.register(ClientCommands.literal("claude")
                .then(ClientCommands.argument("prompt", StringArgumentType.greedyString())
                        .executes(context -> {
                            String prompt = StringArgumentType.getString(context, "prompt");
                            agentManager.executeClaude(prompt);
                            return 1;
                        })
                )
                .executes(context -> {
                    agentManager.openComposer();
                    return 1;
                })
        );

        dispatcher.register(ClientCommands.literal("c")
                .then(ClientCommands.argument("prompt", StringArgumentType.greedyString())
                        .executes(context -> {
                            String prompt = StringArgumentType.getString(context, "prompt");
                            agentManager.executeClaude(prompt);
                            return 1;
                        })
                )
                .executes(context -> {
                    agentManager.openComposer();
                    return 1;
                })
        );

        // 2. /codex <prompt> & /x <prompt>
        dispatcher.register(ClientCommands.literal("codex")
                .then(ClientCommands.argument("prompt", StringArgumentType.greedyString())
                        .executes(context -> {
                            String prompt = StringArgumentType.getString(context, "prompt");
                            agentManager.executeCodex(prompt);
                            return 1;
                        })
                )
                .executes(context -> {
                    agentManager.openComposer();
                    return 1;
                })
        );

        dispatcher.register(ClientCommands.literal("x")
                .then(ClientCommands.argument("prompt", StringArgumentType.greedyString())
                        .executes(context -> {
                            String prompt = StringArgumentType.getString(context, "prompt");
                            agentManager.executeCodex(prompt);
                            return 1;
                        })
                )
                .executes(context -> {
                    agentManager.openComposer();
                    return 1;
                })
        );

        // 3. /ai ...
        dispatcher.register(ClientCommands.literal("ai")
                // /ai spawn [claude|codex] <label> [worktree|shared]
                .then(ClientCommands.literal("spawn")
                        .then(ClientCommands.literal("claude")
                                .then(ClientCommands.argument("label", StringArgumentType.word())
                                        .executes(context -> {
                                            String label = StringArgumentType.getString(context, "label");
                                            agentManager.spawnInstance("Claude", label, WorkspaceMode.ISOLATED_WORKTREE);
                                            return 1;
                                        })
                                )
                        )
                        .then(ClientCommands.literal("codex")
                                .then(ClientCommands.argument("label", StringArgumentType.word())
                                        .executes(context -> {
                                            String label = StringArgumentType.getString(context, "label");
                                            agentManager.spawnInstance("Codex", label, WorkspaceMode.ISOLATED_WORKTREE);
                                            return 1;
                                        })
                                )
                        )
                )
                // /ai focus <label>
                .then(ClientCommands.literal("focus")
                        .then(ClientCommands.argument("label", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    List<String> list = new ArrayList<>();
                                    for (AgentInstance i : agentManager.getAllInstances()) list.add(i.getLabel());
                                    return SharedSuggestionProvider.suggest(list, b);
                                })
                                .executes(context -> {
                                    String label = StringArgumentType.getString(context, "label");
                                    agentManager.setFocusedInstance(label);
                                    return 1;
                                })
                        )
                )
                // /ai instances
                .then(ClientCommands.literal("instances")
                        .executes(context -> {
                            ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§6=== Active Agent Instances ===");
                            for (AgentInstance i : agentManager.getAllInstances()) {
                                boolean isFoc = (i == agentManager.getFocusedInstance());
                                String ptr = isFoc ? "§b[FOCUSED] " : "  ";
                                String dot = i.getStatus().isRunning() ? "§a●" : "§7○";
                                String ws = i.isIsolated() ? "§e(isolated: " + i.getWorktreeBranch() + ")" : "§8(shared)";
                                ChatNotifier.sendMessage(ptr + dot + " §f" + i.getAgentName() + " §7· §e" + i.getModel() + " " + ws);
                            }
                            return 1;
                        })
                )
                // /ai merge [<label>]
                .then(ClientCommands.literal("merge")
                        .then(ClientCommands.argument("label", StringArgumentType.word())
                                .executes(context -> {
                                    String label = StringArgumentType.getString(context, "label");
                                    ProjectProfile p = ProjectManager.getInstance().getActiveProject();
                                    File baseRepo = new File(p != null ? p.getWorkingDirectory() : ConfigManager.getInstance().getConfig().getWorkingDirectory());
                                    String prov = "agent";
                                    for (AgentInstance inst : agentManager.getAllInstances()) {
                                        if (inst.getLabel().equalsIgnoreCase(label) || inst.getInstanceId().equalsIgnoreCase(label)) {
                                            prov = inst.getProviderType();
                                            break;
                                        }
                                    }
                                    WorktreeManager.getInstance().applyToMain(baseRepo, prov, label);
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            AgentInstance foc = agentManager.getFocusedInstance();
                            if (foc != null && foc.isIsolated()) {
                                ProjectProfile p = ProjectManager.getInstance().getActiveProject();
                                File baseRepo = new File(p != null ? p.getWorkingDirectory() : ConfigManager.getInstance().getConfig().getWorkingDirectory());
                                WorktreeManager.getInstance().applyToMain(baseRepo, foc.getProviderType(), foc.getLabel());
                            } else {
                                ChatNotifier.sendError("Focused agent is not in an isolated worktree. Specify /ai merge <label>.");
                            }
                            return 1;
                        })
                )
                // /ai composer
                .then(ClientCommands.literal("composer")
                        .executes(context -> {
                            agentManager.openComposer();
                            return 1;
                        })
                )
                // /ai tasks
                .then(ClientCommands.literal("tasks")
                        .executes(context -> {
                            agentManager.openTasks();
                            return 1;
                        })
                )
                // /ai rewind [<turnId>]
                .then(ClientCommands.literal("rewind")
                        .then(ClientCommands.argument("turnId", StringArgumentType.word())
                                .executes(context -> {
                                    String tid = StringArgumentType.getString(context, "turnId");
                                    AgentInstance foc = agentManager.getFocusedInstance();
                                    if (foc != null) foc.rewindToCheckpoint(tid);
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            AgentInstance foc = agentManager.getFocusedInstance();
                            if (foc != null) foc.rewindToCheckpoint("1");
                            return 1;
                        })
                )
                // /ai stash [save|pop|list]
                .then(ClientCommands.literal("stash")
                        .then(ClientCommands.literal("pop")
                                .executes(context -> {
                                    ProjectProfile p = ProjectManager.getInstance().getActiveProject();
                                    String popped = PromptStash.getInstance().popNamedStash(p != null ? p.getName() : "default");
                                    if (popped != null) {
                                        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§aPopped stash: §f" + popped);
                                    } else {
                                        ChatNotifier.sendFeedback("§7No stashed prompts.");
                                    }
                                    return 1;
                                })
                        )
                        .then(ClientCommands.literal("list")
                                .executes(context -> {
                                    ProjectProfile p = ProjectManager.getInstance().getActiveProject();
                                    List<String> list = PromptStash.getInstance().getNamedStashes(p != null ? p.getName() : "default");
                                    ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§6Stashed prompts (" + list.size() + "):");
                                    for (int i = 0; i < list.size(); i++) {
                                        ChatNotifier.sendMessage("  " + (i + 1) + ". §f" + list.get(i));
                                    }
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            agentManager.openComposer();
                            return 1;
                        })
                )
                // /ai shell <command>
                .then(ClientCommands.literal("shell")
                        .then(ClientCommands.argument("cmd", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String cmd = StringArgumentType.getString(context, "cmd");
                                    ShellExecutor.executeCommand(cmd);
                                    return 1;
                                })
                        )
                )
                // /ai model [claude|codex] [<model>]
                .then(ClientCommands.literal("model")
                        .then(ClientCommands.literal("claude")
                                .then(ClientCommands.argument("modelName", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("claude-opus-5", "claude-sonnet-4-5", "claude-haiku-4-5"), b))
                                        .executes(context -> {
                                            String m = StringArgumentType.getString(context, "modelName");
                                            AgentInstance foc = agentManager.getFocusedInstance();
                                            if (foc != null) foc.setModel(m);
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    agentManager.openModelPicker(true);
                                    return 1;
                                })
                        )
                        .then(ClientCommands.literal("codex")
                                .then(ClientCommands.argument("modelName", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("gpt-5.x", "o3", "gpt-4o"), b))
                                        .executes(context -> {
                                            String m = StringArgumentType.getString(context, "modelName");
                                            AgentInstance foc = agentManager.getFocusedInstance();
                                            if (foc != null) foc.setModel(m);
                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    agentManager.openModelPicker(false);
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            agentManager.openModelPicker(true);
                            return 1;
                        })
                )
                // /ai transcript
                .then(ClientCommands.literal("transcript")
                        .executes(context -> {
                            agentManager.openTranscript();
                            return 1;
                        })
                )
                // /ai diff & /ai files
                .then(ClientCommands.literal("diff")
                        .executes(context -> {
                            agentManager.openDiff();
                            return 1;
                        })
                )
                .then(ClientCommands.literal("files")
                        .executes(context -> {
                            agentManager.openDiff();
                            return 1;
                        })
                )
                // /ai hud [off|compact|normal|detailed]
                .then(ClientCommands.literal("hud")
                        .then(ClientCommands.literal("off")
                                .executes(context -> setHudMode(HudConfig.Mode.OFF))
                        )
                        .then(ClientCommands.literal("compact")
                                .executes(context -> setHudMode(HudConfig.Mode.COMPACT))
                        )
                        .then(ClientCommands.literal("normal")
                                .executes(context -> setHudMode(HudConfig.Mode.NORMAL))
                        )
                        .then(ClientCommands.literal("detailed")
                                .executes(context -> setHudMode(HudConfig.Mode.DETAILED))
                        )
                        .executes(context -> {
                            ConfigManager.getInstance().getConfig().getHud().cycleMode();
                            ConfigManager.getInstance().save();
                            ChatNotifier.sendFeedback("HUD mode: §f" + ConfigManager.getInstance().getConfig().getHud().getMode());
                            return 1;
                        })
                )
                // /ai permissions [claude|codex] [<mode>]
                .then(ClientCommands.literal("permissions")
                        .then(ClientCommands.argument("mode", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("plan", "manual", "auto", "bypassPermissions", "yolo"), b))
                                .executes(context -> {
                                    String m = StringArgumentType.getString(context, "mode");
                                    AgentInstance foc = agentManager.getFocusedInstance();
                                    if (foc != null) foc.setPermissionMode(m);
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            AgentInstance foc = agentManager.getFocusedInstance();
                            if (foc != null) foc.cyclePermissionMode();
                            return 1;
                        })
                )
                // /ai stop [all|<label>]
                .then(ClientCommands.literal("stop")
                        .then(ClientCommands.argument("target", StringArgumentType.word())
                                .executes(context -> {
                                    String t = StringArgumentType.getString(context, "target");
                                    agentManager.stop(t);
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            agentManager.stop(null);
                            return 1;
                        })
                )
                // /ai help
                .then(ClientCommands.literal("help")
                        .executes(AiCommands::showHelp)
                )
                .executes(AiCommands::showHelp)
        );
    }

    private static int setHudMode(HudConfig.Mode mode) {
        ConfigManager.getInstance().getConfig().getHud().setMode(mode);
        ConfigManager.getInstance().save();
        ChatNotifier.sendFeedback("§aHUD mode set to: §f" + mode);
        return 1;
    }

    private static int showHelp(CommandContext<FabricClientCommandSource> context) {
        ChatNotifier.sendMessage(ChatColorUtil.AI_PREFIX + "§6=== Multi-Agent Parallel Supervisor ===");
        ChatNotifier.sendMessage("§b[U] Key§r - Open Dedicated AI Composer");
        ChatNotifier.sendMessage("§e[Ctrl+Tab]§r - Cycle target agent instance (terminal-tabs)");
        ChatNotifier.sendMessage("§e/ai spawn [claude|codex] <label>§r - Spawn parallel agent in isolated worktree");
        ChatNotifier.sendMessage("§e/ai focus <label>§r - Switch composer target to named instance");
        ChatNotifier.sendMessage("§e/ai merge <label>§r - Merge isolated worktree changes into main branch");
        ChatNotifier.sendMessage("§e/ai instances§r - List all running agent instances");
        ChatNotifier.sendMessage("§e/ai tasks§r - Open Background Task Manager");
        ChatNotifier.sendMessage("§e/ai rewind§r - Rewind conversation to checkpoint");
        ChatNotifier.sendMessage("§e/ai model [claude|codex]§r - Open model & reasoning picker");
        ChatNotifier.sendMessage("§e/ai permissions [mode]§r - Set instance permission mode (YOLO/Safe)");
        ChatNotifier.sendMessage("§e/ai transcript§r - Open full activity & output viewer");
        ChatNotifier.sendMessage("§e/ai diff§r - Open file changes and diff screen");
        ChatNotifier.sendMessage("§e/ai stop [all|<label>]§r - Quick stop / interrupt running instances");
        return 1;
    }
}
