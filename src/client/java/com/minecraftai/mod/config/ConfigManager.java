package com.minecraftai.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class ConfigManager {
    private static final String CONFIG_FILE_NAME = "claude_codex_chat.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigManager instance;

    private ModConfig config;
    private final Path configPath;

    public ConfigManager() {
        Path configDir;
        try {
            configDir = FabricLoader.getInstance().getConfigDir();
        } catch (Exception | NoClassDefFoundError e) {
            configDir = Path.of("config");
        }
        this.configPath = configDir.resolve(CONFIG_FILE_NAME);
        load();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public synchronized ModConfig getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    public synchronized void load() {
        File file = configPath.toFile();
        if (!file.exists()) {
            this.config = new ModConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            this.config = loaded != null ? loaded : new ModConfig();
        } catch (Exception e) {
            System.err.println("[ClaudeCodexChat] Failed to load config, using defaults: " + e.getMessage());
            this.config = new ModConfig();
        }
    }

    public synchronized void save() {
        File file = configPath.toFile();
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(config != null ? config : new ModConfig(), writer);
            }
        } catch (IOException e) {
            System.err.println("[ClaudeCodexChat] Failed to save config: " + e.getMessage());
        }
    }
}
