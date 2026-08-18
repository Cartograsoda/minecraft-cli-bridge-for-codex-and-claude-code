package com.minecraftai.mod.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecraftai.mod.codex.CodexAppServerClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class SessionHistoryManager {

    private static final String USER_HOME = System.getProperty("user.home", "");

    public static List<SessionInfo> getClaudeSessions() {
        Map<String, SessionInfo> sessionMap = new LinkedHashMap<>();

        // 1. Read from ~/.claude/history.jsonl
        File historyFile = new File(USER_HOME, ".claude/history.jsonl");
        if (historyFile.exists() && historyFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(historyFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                        String sid = obj.has("sessionId") ? obj.get("sessionId").getAsString() : "";
                        String display = obj.has("display") ? obj.get("display").getAsString() : "";
                        String proj = obj.has("project") ? obj.get("project").getAsString() : "";
                        long ts = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : historyFile.lastModified();

                        if (!sid.isBlank() && !display.isBlank() && !display.startsWith("/")) {
                            String shortTitle = display.length() > 60 ? display.substring(0, 57) + "..." : display;
                            String projName = proj.contains(File.separator) ? proj.substring(proj.lastIndexOf(File.separator) + 1) : proj;
                            sessionMap.put(sid, new SessionInfo("Claude", sid, shortTitle, ts, projName));
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Scan ~/.claude/projects/
        File projectsDir = new File(USER_HOME, ".claude/projects");
        if (projectsDir.exists() && projectsDir.isDirectory()) {
            File[] projectDirs = projectsDir.listFiles(File::isDirectory);
            if (projectDirs != null) {
                for (File pDir : projectDirs) {
                    File[] sessionFiles = pDir.listFiles((dir, name) -> name.endsWith(".jsonl"));
                    if (sessionFiles == null) continue;

                    for (File sFile : sessionFiles) {
                        String filename = sFile.getName();
                        String sessionId = filename.substring(0, filename.length() - 6);

                        String title = extractClaudeTitle(sFile);
                        long lastMod = sFile.lastModified();
                        String projName = pDir.getName().replace("C--", "C:\\").replace("-", "\\");
                        if (projName.contains("\\")) {
                            projName = projName.substring(projName.lastIndexOf("\\") + 1);
                        }

                        SessionInfo info;
                        if (sessionMap.containsKey(sessionId)) {
                            info = sessionMap.get(sessionId);
                            long maxTs = Math.max(info.getLastModified(), lastMod);
                            String finalTitle = (!title.equals("Claude Session")) ? title : info.getTitle();
                            info = new SessionInfo("Claude", sessionId, finalTitle, maxTs, projName);
                        } else {
                            info = new SessionInfo("Claude", sessionId, title, lastMod, projName);
                        }
                        populateClaudeTurns(sFile, info);
                        sessionMap.put(sessionId, info);
                    }
                }
            }
        }

        List<SessionInfo> list = new ArrayList<>(sessionMap.values());
        list.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
            return Long.compare(b.getLastModified(), a.getLastModified());
        });
        return list;
    }

    private static String extractClaudeTitle(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 30) {
                linesRead++;
                if (line.isBlank()) continue;
                try {
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                    if (obj.has("aiTitle") && !obj.get("aiTitle").getAsString().isBlank()) {
                        return obj.get("aiTitle").getAsString();
                    }
                    if (obj.has("content") && !obj.get("content").getAsString().isBlank()) {
                        String c = obj.get("content").getAsString();
                        if (!c.startsWith("{") && !c.startsWith("/")) {
                            return c.length() > 60 ? c.substring(0, 57) + "..." : c;
                        }
                    }
                    if (obj.has("type") && "user".equals(obj.get("type").getAsString()) && obj.has("message")) {
                        String msg = obj.get("message").getAsString();
                        return msg.length() > 60 ? msg.substring(0, 57) + "..." : msg;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return "Claude Session";
    }

    private static void populateClaudeTurns(File file, SessionInfo info) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            int turnIdx = 1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                    if (obj.has("type") && "user".equals(obj.get("type").getAsString())) {
                        String msg = obj.has("message") ? obj.get("message").getAsString() : "Prompt " + turnIdx;
                        String commit = obj.has("gitCommit") ? obj.get("gitCommit").getAsString() : "";
                        TurnInfo turn = new TurnInfo("turn-" + turnIdx, msg, msg, commit);
                        turn.setStatus(TurnInfo.Status.SUCCESS);
                        info.addTurn(turn);
                        turnIdx++;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static List<SessionInfo> getCodexSessions() {
        // 1. Check Codex App Server first!
        CodexAppServerClient appServer = CodexAppServerClient.getInstance();
        if (appServer.isConnected()) {
            com.minecraftai.mod.project.ProjectProfile activeProj = com.minecraftai.mod.project.ProjectManager.getInstance().getActiveProject();
            String cwdFilter = activeProj != null ? activeProj.getWorkingDirectory() : null;
            List<SessionInfo> serverThreads = appServer.listThreads(cwdFilter);
            if (!serverThreads.isEmpty()) {
                return serverThreads;
            }
        }

        // 2. Fallback to ~/.codex/session_index.jsonl
        Map<String, SessionInfo> sessionMap = new LinkedHashMap<>();
        File indexFile = new File(USER_HOME, ".codex/session_index.jsonl");
        if (indexFile.exists() && indexFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(indexFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                        String id = obj.has("id") ? obj.get("id").getAsString() : "";
                        String name = obj.has("thread_name") ? obj.get("thread_name").getAsString() : "Codex Session";
                        long timestamp = 0;
                        if (obj.has("updated_at")) {
                            try {
                                timestamp = Instant.parse(obj.get("updated_at").getAsString()).toEpochMilli();
                            } catch (Exception ignored) {
                                timestamp = indexFile.lastModified();
                            }
                        } else {
                            timestamp = indexFile.lastModified();
                        }

                        if (!id.isBlank()) {
                            sessionMap.put(id, new SessionInfo("Codex", id, name, timestamp, ""));
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Fallback/merge ~/.codex/history.jsonl
        File historyFile = new File(USER_HOME, ".codex/history.jsonl");
        if (historyFile.exists() && historyFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(historyFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                        String id = obj.has("thread_id") ? obj.get("thread_id").getAsString() :
                                (obj.has("sessionId") ? obj.get("sessionId").getAsString() : "");
                        String text = obj.has("prompt") ? obj.get("prompt").getAsString() :
                                (obj.has("display") ? obj.get("display").getAsString() : "");
                        long ts = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : historyFile.lastModified();

                        if (!id.isBlank() && !text.isBlank()) {
                            String shortTitle = text.length() > 60 ? text.substring(0, 57) + "..." : text;
                            if (!sessionMap.containsKey(id)) {
                                sessionMap.put(id, new SessionInfo("Codex", id, shortTitle, ts, ""));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        List<SessionInfo> list = new ArrayList<>(sessionMap.values());
        list.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
            return Long.compare(b.getLastModified(), a.getLastModified());
        });
        return list;
    }

    public static SessionInfo findClaudeSession(String idOrPrefix) {
        if (idOrPrefix == null || idOrPrefix.isBlank()) return null;
        String search = idOrPrefix.trim().toLowerCase();

        List<SessionInfo> list = getClaudeSessions();
        for (SessionInfo s : list) {
            if (s.getId().equalsIgnoreCase(search)) {
                return s;
            }
        }
        for (SessionInfo s : list) {
            if (s.getId().toLowerCase().startsWith(search) || s.getShortId().toLowerCase().startsWith(search)) {
                return s;
            }
        }
        return null;
    }

    public static SessionInfo findCodexSession(String idOrPrefix) {
        if (idOrPrefix == null || idOrPrefix.isBlank()) return null;
        String search = idOrPrefix.trim().toLowerCase();

        List<SessionInfo> list = getCodexSessions();
        for (SessionInfo s : list) {
            if (s.getId().equalsIgnoreCase(search)) {
                return s;
            }
        }
        for (SessionInfo s : list) {
            if (s.getId().toLowerCase().startsWith(search) || s.getShortId().toLowerCase().startsWith(search)) {
                return s;
            }
        }
        return null;
    }

    public static SessionInfo findSession(String idOrPrefix) {
        SessionInfo c = findClaudeSession(idOrPrefix);
        if (c != null) return c;
        return findCodexSession(idOrPrefix);
    }
}
