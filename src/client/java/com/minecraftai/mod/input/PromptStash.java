package com.minecraftai.mod.input;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PromptStash {

    private static PromptStash instance;

    // session/project key -> draft text
    private final Map<String, String> autoDrafts = new ConcurrentHashMap<>();

    // named stashes (e.g. from Ctrl+S or /ai stash)
    private final Map<String, List<String>> namedStashes = new ConcurrentHashMap<>();

    public static synchronized PromptStash getInstance() {
        if (instance == null) {
            instance = new PromptStash();
        }
        return instance;
    }

    public void saveDraft(String project, String sessionId, String draftText) {
        String key = (project != null ? project : "default") + ":" + (sessionId != null ? sessionId : "new");
        if (draftText == null || draftText.isBlank()) {
            autoDrafts.remove(key);
        } else {
            autoDrafts.put(key, draftText);
        }
    }

    public String getDraft(String project, String sessionId) {
        String key = (project != null ? project : "default") + ":" + (sessionId != null ? sessionId : "new");
        return autoDrafts.getOrDefault(key, "");
    }

    public void clearDraft(String project, String sessionId) {
        String key = (project != null ? project : "default") + ":" + (sessionId != null ? sessionId : "new");
        autoDrafts.remove(key);
    }

    public void pushNamedStash(String project, String text) {
        if (text == null || text.isBlank()) return;
        String key = project != null ? project : "default";
        namedStashes.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(0, text);
    }

    public String popNamedStash(String project) {
        String key = project != null ? project : "default";
        List<String> list = namedStashes.get(key);
        if (list != null && !list.isEmpty()) {
            return list.remove(0);
        }
        return null;
    }

    public List<String> getNamedStashes(String project) {
        String key = project != null ? project : "default";
        List<String> list = namedStashes.get(key);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }
}
