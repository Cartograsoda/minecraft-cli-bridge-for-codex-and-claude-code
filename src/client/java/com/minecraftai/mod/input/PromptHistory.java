package com.minecraftai.mod.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PromptHistory {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String HISTORY_FILE = "ai_prompt_history.json";
    private static PromptHistory instance;

    private Map<String, List<String>> projectHistories = new HashMap<>();
    private final Path historyPath;
    private int historyPointer = -1;

    public PromptHistory() {
        Path configDir;
        try {
            configDir = FabricLoader.getInstance().getConfigDir();
        } catch (Exception | NoClassDefFoundError e) {
            configDir = Path.of("config");
        }
        this.historyPath = configDir.resolve(HISTORY_FILE);
        load();
    }

    public static synchronized PromptHistory getInstance() {
        if (instance == null) {
            instance = new PromptHistory();
        }
        return instance;
    }

    public synchronized void addPrompt(String project, String prompt) {
        if (prompt == null || prompt.isBlank()) return;
        String projKey = project != null ? project.toLowerCase() : "default";
        List<String> list = projectHistories.computeIfAbsent(projKey, k -> new ArrayList<>());

        list.remove(prompt); // remove dupes
        list.add(prompt);
        if (list.size() > 200) {
            list.remove(0);
        }
        save();
        resetPointer();
    }

    public synchronized List<String> getHistory(String project) {
        String projKey = project != null ? project.toLowerCase() : "default";
        return new ArrayList<>(projectHistories.computeIfAbsent(projKey, k -> new ArrayList<>()));
    }

    public synchronized String getPrevious(String project) {
        List<String> list = getHistory(project);
        if (list.isEmpty()) return null;
        if (historyPointer < 0) {
            historyPointer = list.size() - 1;
        } else if (historyPointer > 0) {
            historyPointer--;
        }
        return list.get(historyPointer);
    }

    public synchronized String getNext(String project) {
        List<String> list = getHistory(project);
        if (list.isEmpty()) return null;
        if (historyPointer >= 0 && historyPointer < list.size() - 1) {
            historyPointer++;
            return list.get(historyPointer);
        } else {
            historyPointer = -1;
            return "";
        }
    }

    public synchronized void resetPointer() {
        this.historyPointer = -1;
    }

    public synchronized List<String> search(String project, String query) {
        List<String> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;
        String q = query.toLowerCase();

        List<String> list = getHistory(project);
        for (int i = list.size() - 1; i >= 0; i--) {
            String item = list.get(i);
            if (item.toLowerCase().contains(q)) {
                results.add(item);
            }
        }
        return results;
    }

    public synchronized void load() {
        File file = historyPath.toFile();
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                this.projectHistories = loaded;
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        File file = historyPath.toFile();
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(projectHistories, writer);
            }
        } catch (Exception ignored) {
        }
    }
}
