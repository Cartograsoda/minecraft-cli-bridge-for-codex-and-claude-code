package com.minecraftai.mod.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecraftai.mod.agent.ApprovalRequest;
import com.minecraftai.mod.agent.ProviderTelemetrySnapshot;
import com.minecraftai.mod.agent.SessionInfo;
import com.minecraftai.mod.agent.TurnInfo;
import com.minecraftai.mod.chat.ChatColorUtil;
import com.minecraftai.mod.chat.ChatNotifier;
import com.minecraftai.mod.hud.HudNotificationManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Robust JSON-RPC transport client for the Codex App Server.
 * Implements full connection state machine, initialization handshake,
 * three-way RPC/notification/server-request routing, cursor-paginated thread listings,
 * scheduled RPC timeouts, and immutable telemetry snapshots.
 */
public class CodexAppServerClient {

    public enum ConnectionState {
        STOPPED,
        STARTING,
        INITIALIZING,
        READY,
        FAILED
    }

    private static CodexAppServerClient instance;

    private volatile ConnectionState connectionState = ConnectionState.STOPPED;
    private Process appServerProcess;
    private BufferedWriter writer;
    private BufferedReader reader;
    private BufferedReader errorReader;

    private Thread listenerThread;
    private Thread stderrThread;

    private final AtomicInteger requestId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, JsonElement> activeServerRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CodexAppServerTimeout");
        t.setDaemon(true);
        return t;
    });

    private final Set<String> pinnedThreadIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> archivedThreadIds = Collections.synchronizedSet(new HashSet<>());

    // Telemetry and Observability State
    private final AtomicReference<ProviderTelemetrySnapshot> telemetrySnapshot =
            new AtomicReference<>(ProviderTelemetrySnapshot.unknown());
    private final List<String> recentStderr = Collections.synchronizedList(new LinkedList<>());

    private volatile String lastError = null;
    private volatile long lastErrorTime = 0;
    private volatile long lastSuccessfulMessageTime = 0;
    private volatile long lastHeartbeat = 0;

    public static synchronized CodexAppServerClient getInstance() {
        if (instance == null) {
            instance = new CodexAppServerClient();
        }
        return instance;
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.READY &&
                appServerProcess != null &&
                appServerProcess.isAlive();
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public String getLastError() {
        return lastError;
    }

    public long getLastErrorTime() {
        return lastErrorTime;
    }

    public long getLastSuccessfulMessageTime() {
        return lastSuccessfulMessageTime;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public ProviderTelemetrySnapshot getTelemetrySnapshot() {
        return telemetrySnapshot.get();
    }

    public double getFiveHourRemainingPercent() {
        return telemetrySnapshot.get().primaryRemainingPercent().orElse(Double.NaN);
    }

    public double getWeeklyRemainingPercent() {
        return telemetrySnapshot.get().secondaryRemainingPercent().orElse(Double.NaN);
    }

    public int getResetCredits() {
        return telemetrySnapshot.get().resetCredits();
    }

    public synchronized boolean startAppServer(File workingDir) {
        if (isConnected()) {
            return true;
        }

        // Clean up any stale state first
        shutdown();

        this.connectionState = ConnectionState.STARTING;
        this.lastError = null;

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb = isWindows ?
                    new ProcessBuilder("cmd.exe", "/c", "codex app-server") :
                    new ProcessBuilder("codex", "app-server");

            if (workingDir != null && workingDir.exists()) {
                pb.directory(workingDir);
            }

            appServerProcess = pb.start();
            writer = new BufferedWriter(new OutputStreamWriter(appServerProcess.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(appServerProcess.getInputStream(), StandardCharsets.UTF_8));
            errorReader = new BufferedReader(new InputStreamReader(appServerProcess.getErrorStream(), StandardCharsets.UTF_8));

            // Start stderr drainer to prevent pipe deadlocks
            stderrThread = new Thread(this::stderrDrainLoop, "CodexAppServerStderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Start stdout reader
            listenerThread = new Thread(this::listenLoop, "CodexAppServerListener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Perform Codex App Server handshake:
            // spawn -> initialize request -> initialize response -> initialized notification -> READY
            this.connectionState = ConnectionState.INITIALIZING;

            JsonObject initParams = new JsonObject();
            JsonObject clientInfo = new JsonObject();
            clientInfo.addProperty("name", "minecraft-mod-client");
            clientInfo.addProperty("version", "1.0.0");
            initParams.add("clientInfo", clientInfo);

            JsonObject capabilities = new JsonObject();
            capabilities.addProperty("approvals", true);
            initParams.add("capabilities", capabilities);

            CompletableFuture<JsonObject> initFuture = sendRpc("initialize", initParams, 5000);
            JsonObject initResult = initFuture.get(5, TimeUnit.SECONDS);

            if (initResult == null || initResult.has("error")) {
                String errMsg = initResult != null && initResult.has("error") ?
                        initResult.get("error").toString() : "No initialize response received";
                recordError("App Server initialize handshake failed: " + errMsg);
                this.connectionState = ConnectionState.FAILED;
                return false;
            }

            // Send initialized notification
            sendNotification("initialized", new JsonObject());

            this.connectionState = ConnectionState.READY;
            this.lastHeartbeat = System.currentTimeMillis();
            System.out.println("[CodexAppServerClient] Handshake complete. Connection state: READY");

            // Query initial rate limits asynchronously
            refreshRateLimits();
            return true;
        } catch (Exception e) {
            recordError("Failed to initialize Codex App Server: " + e.getMessage());
            this.connectionState = ConnectionState.FAILED;
            shutdown();
            return false;
        }
    }

    private void recordError(String error) {
        this.lastError = error;
        this.lastErrorTime = System.currentTimeMillis();
        System.err.println("[CodexAppServerClient] " + error);
    }

    private void stderrDrainLoop() {
        try {
            String line;
            while (errorReader != null && (line = errorReader.readLine()) != null) {
                if (line.isBlank()) continue;
                recentStderr.add(line);
                if (recentStderr.size() > 200) {
                    recentStderr.remove(0);
                }
                if (line.contains("ERROR") || line.contains("Error") || line.contains("panic")) {
                    recordError("AppServer stderr: " + line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void listenLoop() {
        try {
            String line;
            while (reader != null && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                lastSuccessfulMessageTime = System.currentTimeMillis();
                try {
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                    boolean hasId = obj.has("id") && !obj.get("id").isJsonNull();
                    boolean hasMethod = obj.has("method") && !obj.get("method").isJsonNull();

                    if (hasMethod && hasId) {
                        // 1. Server-initiated request (e.g. approval / tool permission)
                        handleServerRequest(obj.get("id"), obj.get("method").getAsString(),
                                obj.has("params") && obj.get("params").isJsonObject() ? obj.getAsJsonObject("params") : null);
                    } else if (hasMethod) {
                        // 2. Notification (no id)
                        handleNotification(obj.get("method").getAsString(),
                                obj.has("params") && obj.get("params").isJsonObject() ? obj.getAsJsonObject("params") : null);
                    } else if (hasId) {
                        // 3. Response to client RPC
                        handleResponse(obj);
                    }
                } catch (Exception e) {
                    recordError("Failed to parse App Server message: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            if (connectionState != ConnectionState.STOPPED) {
                recordError("Listener disconnected: " + e.getMessage());
                this.connectionState = ConnectionState.FAILED;
            }
        }
    }

    private void handleResponse(JsonObject obj) {
        try {
            int id = obj.get("id").getAsInt();
            CompletableFuture<JsonObject> future = pendingRequests.remove(id);
            if (future != null) {
                if (obj.has("error") && !obj.get("error").isJsonNull()) {
                    JsonObject err = obj.getAsJsonObject("error");
                    String msg = err.has("message") ? err.get("message").getAsString() : err.toString();
                    future.completeExceptionally(new IOException("RPC Error (" + id + "): " + msg));
                } else {
                    future.complete(obj);
                }
            }
        } catch (Exception e) {
            recordError("Error handling RPC response: " + e.getMessage());
        }
    }

    private void handleNotification(String method, JsonObject params) {
        lastHeartbeat = System.currentTimeMillis();
        if ("account/rateLimits/updated".equalsIgnoreCase(method) && params != null) {
            parseRateLimits(params);
        }
    }

    private void handleServerRequest(JsonElement id, String method, JsonObject params) {
        String reqKey = id.toString();
        activeServerRequests.put(reqKey, id);

        if ("approval/request".equalsIgnoreCase(method) ||
                "item/approval".equalsIgnoreCase(method) ||
                "command/approval".equalsIgnoreCase(method) ||
                "exec/approval".equalsIgnoreCase(method)) {

            String toolName = "Command";
            String details = "";
            if (params != null) {
                if (params.has("tool")) toolName = params.get("tool").getAsString();
                else if (params.has("command")) toolName = "Shell";

                if (params.has("command")) details = params.get("command").getAsString();
                else if (params.has("details")) details = params.get("details").getAsString();
                else if (params.has("description")) details = params.get("description").getAsString();
            }

            ApprovalRequest req = new ApprovalRequest(reqKey, "Codex", toolName, details);
            HudNotificationManager.notifyWaitingForInput("Codex", toolName + " " + details);
            ChatNotifier.sendMessage(ChatColorUtil.CODEX_PREFIX + "§c⚠ SERVER APPROVAL REQUIRED: §f" + toolName + " " + details);
            ChatNotifier.sendMessage("  §a[ALLOW ONCE: /ai approve " + reqKey + "] §c[DENY: /ai deny " + reqKey + "]");
        }
    }

    public void respondToServerRequest(String reqKey, boolean approved, String reason) {
        JsonElement id = activeServerRequests.remove(reqKey);
        if (id == null) return;

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonObject result = new JsonObject();
        result.addProperty("decision", approved ? "approve" : "deny");
        if (reason != null && !reason.isBlank()) {
            result.addProperty("reason", reason);
        }
        response.add("result", result);

        sendRawJson(response);
    }

    public CompletableFuture<JsonObject> sendRpc(String method, JsonObject params) {
        return sendRpc(method, params, 5000);
    }

    public synchronized CompletableFuture<JsonObject> sendRpc(String method, JsonObject params, long timeoutMillis) {
        int id = requestId.getAndIncrement();
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", method);
        if (params != null) {
            request.add("params", params);
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        // Schedule timeout removal to avoid leaking pending requests
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            CompletableFuture<JsonObject> removed = pendingRequests.remove(id);
            if (removed != null && !removed.isDone()) {
                removed.completeExceptionally(new TimeoutException("RPC request '" + method + "' (id=" + id + ") timed out after " + timeoutMillis + "ms"));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        future.whenComplete((res, ex) -> timeoutTask.cancel(false));

        try {
            if (writer != null) {
                writer.write(request.toString());
                writer.newLine();
                writer.flush();
            } else {
                pendingRequests.remove(id);
                future.completeExceptionally(new IOException("App Server stream writer not available"));
            }
        } catch (Exception e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    public synchronized void sendNotification(String method, JsonObject params) {
        JsonObject notification = new JsonObject();
        notification.addProperty("jsonrpc", "2.0");
        notification.addProperty("method", method);
        if (params != null) {
            notification.add("params", params);
        }
        sendRawJson(notification);
    }

    private synchronized void sendRawJson(JsonObject json) {
        try {
            if (writer != null) {
                writer.write(json.toString());
                writer.newLine();
                writer.flush();
            }
        } catch (Exception e) {
            recordError("Failed to write JSON message: " + e.getMessage());
        }
    }

    public List<SessionInfo> listThreads(String workingDirFilter) {
        List<SessionInfo> list = new ArrayList<>();
        if (!isConnected()) {
            return list;
        }

        try {
            String cursor = null;
            int maxPages = 5;
            int pageCount = 0;

            do {
                JsonObject params = new JsonObject();
                if (workingDirFilter != null && !workingDirFilter.isBlank()) {
                    params.addProperty("cwd", workingDirFilter);
                }
                if (cursor != null && !cursor.isBlank()) {
                    params.addProperty("cursor", cursor);
                }

                JsonObject res = sendRpc("thread/list", params, 4000).get(4, TimeUnit.SECONDS);
                if (res == null || !res.has("result")) {
                    break;
                }

                JsonObject result = res.getAsJsonObject("result");
                JsonArray items = null;

                if (result.has("data") && result.get("data").isJsonArray()) {
                    items = result.getAsJsonArray("data");
                } else if (result.has("threads") && result.get("threads").isJsonArray()) {
                    items = result.getAsJsonArray("threads");
                }

                if (items != null) {
                    for (JsonElement el : items) {
                        if (!el.isJsonObject()) continue;
                        JsonObject t = el.getAsJsonObject();

                        String id = t.has("id") ? t.get("id").getAsString() : "";
                        String name = t.has("name") ? t.get("name").getAsString() :
                                (t.has("preview") ? t.get("preview").getAsString() : "Codex Session");
                        String proj = t.has("cwd") ? t.get("cwd").getAsString() :
                                (t.has("project") ? t.get("project").getAsString() : "");

                        long ts = System.currentTimeMillis();
                        if (t.has("updatedAt")) {
                            ts = parseTimestamp(t.get("updatedAt").getAsString());
                        } else if (t.has("updated_at")) {
                            ts = parseTimestamp(t.get("updated_at").getAsString());
                        } else if (t.has("createdAt")) {
                            ts = parseTimestamp(t.get("createdAt").getAsString());
                        }

                        boolean isPinned = pinnedThreadIds.contains(id) ||
                                (t.has("isPinned") && t.get("isPinned").getAsBoolean()) ||
                                (t.has("pinned") && t.get("pinned").getAsBoolean());
                        boolean isArchived = archivedThreadIds.contains(id) ||
                                (t.has("isArchived") && t.get("isArchived").getAsBoolean()) ||
                                (t.has("archived") && t.get("archived").getAsBoolean());

                        SessionInfo s = new SessionInfo("Codex", id, name, ts, proj);
                        s.setPinned(isPinned);
                        s.setArchived(isArchived);
                        list.add(s);
                    }
                }

                cursor = (result.has("nextCursor") && !result.get("nextCursor").isJsonNull()) ?
                        result.get("nextCursor").getAsString() : null;
                pageCount++;
            } while (cursor != null && !cursor.isBlank() && pageCount < maxPages);

        } catch (Exception e) {
            recordError("Error listing threads: " + e.getMessage());
        }
        return list;
    }

    public List<TurnInfo> getThreadTurns(String threadId) {
        List<TurnInfo> list = new ArrayList<>();
        if (!isConnected() || threadId == null || threadId.isBlank()) return list;

        try {
            JsonObject params = new JsonObject();
            params.addProperty("threadId", threadId);
            JsonObject res = sendRpc("thread/read", params, 4000).get(4, TimeUnit.SECONDS);

            if (res != null && res.has("result")) {
                JsonObject result = res.getAsJsonObject("result");
                JsonArray arr = result.has("turns") ? result.getAsJsonArray("turns") : null;
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject turn = el.getAsJsonObject();
                        String tid = turn.has("id") ? turn.get("id").getAsString() : "";
                        String prompt = turn.has("prompt") ? turn.get("prompt").getAsString() : "";
                        String summary = turn.has("summary") ? turn.get("summary").getAsString() : prompt;
                        String commit = turn.has("checkpoint_commit") ? turn.get("checkpoint_commit").getAsString() : "";

                        TurnInfo ti = new TurnInfo(tid, prompt, summary, commit);
                        if (turn.has("status")) {
                            String st = turn.get("status").getAsString();
                            ti.setStatus("success".equalsIgnoreCase(st) ? TurnInfo.Status.SUCCESS :
                                    ("failed".equalsIgnoreCase(st) ? TurnInfo.Status.FAILED : TurnInfo.Status.RUNNING));
                        }
                        list.add(ti);
                    }
                }
            }
        } catch (Exception e) {
            recordError("Error reading thread turns: " + e.getMessage());
        }
        return list;
    }

    public String forkThread(String threadId, String lastTurnId) {
        if (!isConnected() || threadId == null || threadId.isBlank()) return null;
        try {
            JsonObject params = new JsonObject();
            params.addProperty("threadId", threadId);
            if (lastTurnId != null && !lastTurnId.isBlank()) {
                params.addProperty("lastTurnId", lastTurnId);
            }
            JsonObject res = sendRpc("thread/fork", params, 5000).get(5, TimeUnit.SECONDS);
            if (res != null && res.has("result")) {
                JsonObject result = res.getAsJsonObject("result");
                if (result.has("thread") && result.get("thread").isJsonObject()) {
                    JsonObject th = result.getAsJsonObject("thread");
                    if (th.has("id")) return th.get("id").getAsString();
                }
                if (result.has("newThreadId")) {
                    return result.get("newThreadId").getAsString();
                }
                if (result.has("id")) {
                    return result.get("id").getAsString();
                }
            }
        } catch (Exception e) {
            recordError("Error forking thread: " + e.getMessage());
        }
        return null;
    }

    public void setPinned(String threadId, boolean pinned) {
        if (pinned) {
            pinnedThreadIds.add(threadId);
        } else {
            pinnedThreadIds.remove(threadId);
        }
        if (isConnected()) {
            JsonObject params = new JsonObject();
            params.addProperty("threadId", threadId);
            params.addProperty("pinned", pinned);
            sendRpc("thread/pin", params, 2000);
        }
    }

    public void setArchived(String threadId, boolean archived) {
        if (archived) {
            archivedThreadIds.add(threadId);
        } else {
            archivedThreadIds.remove(threadId);
        }
        if (isConnected()) {
            JsonObject params = new JsonObject();
            params.addProperty("threadId", threadId);
            sendRpc(archived ? "thread/archive" : "thread/unarchive", params, 2000);
        }
    }

    public void renameThread(String threadId, String newName) {
        if (isConnected()) {
            JsonObject params = new JsonObject();
            params.addProperty("threadId", threadId);
            params.addProperty("name", newName);
            sendRpc("thread/name", params, 2000);
        }
    }

    public void deleteThread(String threadId) {
        pinnedThreadIds.remove(threadId);
        archivedThreadIds.remove(threadId);
        if (isConnected()) {
            JsonObject params = new JsonObject();
            params.addProperty("threadId", threadId);
            sendRpc("thread/delete", params, 2000);
        }
    }

    public void refreshRateLimits() {
        if (!isConnected()) return;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject res = sendRpc("account/rateLimits/read", new JsonObject(), 3000).get(3, TimeUnit.SECONDS);
                if (res != null && res.has("result")) {
                    parseRateLimits(res.getAsJsonObject("result"));
                }
            } catch (Exception e) {
                recordError("Failed to refresh rate limits: " + e.getMessage());
            }
        });
    }

    private void parseRateLimits(JsonObject result) {
        try {
            JsonObject rateLimits = result.has("rateLimits") && result.get("rateLimits").isJsonObject() ?
                    result.getAsJsonObject("rateLimits") : result;

            OptionalDouble primaryPercent = OptionalDouble.empty();
            int primaryMins = 300;
            String primaryLabel = "5H";
            long primaryResetEpoch = 0;

            if (rateLimits.has("primary") && rateLimits.get("primary").isJsonObject()) {
                JsonObject p = rateLimits.getAsJsonObject("primary");
                if (p.has("usedPercent")) {
                    double used = p.get("usedPercent").getAsDouble();
                    primaryPercent = OptionalDouble.of(Math.max(0.0, Math.min(100.0, 100.0 - used)));
                }
                if (p.has("windowDurationMins")) {
                    primaryMins = p.get("windowDurationMins").getAsInt();
                    primaryLabel = ProviderTelemetrySnapshot.deriveWindowLabel(primaryMins, "5H");
                }
                if (p.has("resetsAt")) {
                    primaryResetEpoch = parseEpochSeconds(p.get("resetsAt"));
                }
            }

            OptionalDouble secondaryPercent = OptionalDouble.empty();
            int secondaryMins = 10080;
            String secondaryLabel = "Weekly";
            long secondaryResetEpoch = 0;

            if (rateLimits.has("secondary") && rateLimits.get("secondary").isJsonObject()) {
                JsonObject s = rateLimits.getAsJsonObject("secondary");
                if (s.has("usedPercent")) {
                    double used = s.get("usedPercent").getAsDouble();
                    secondaryPercent = OptionalDouble.of(Math.max(0.0, Math.min(100.0, 100.0 - used)));
                }
                if (s.has("windowDurationMins")) {
                    secondaryMins = s.get("windowDurationMins").getAsInt();
                    secondaryLabel = ProviderTelemetrySnapshot.deriveWindowLabel(secondaryMins, "Weekly");
                }
                if (s.has("resetsAt")) {
                    secondaryResetEpoch = parseEpochSeconds(s.get("resetsAt"));
                }
            }

            int resetCredits = 0;
            if (result.has("rateLimitResetCredits") && result.get("rateLimitResetCredits").isJsonObject()) {
                JsonObject rrc = result.getAsJsonObject("rateLimitResetCredits");
                if (rrc.has("availableCount")) {
                    resetCredits = rrc.get("availableCount").getAsInt();
                }
            } else if (result.has("resetCreditsAvailable")) {
                resetCredits = result.get("resetCreditsAvailable").getAsInt();
            }

            ProviderTelemetrySnapshot snapshot = ProviderTelemetrySnapshot.of(
                    primaryPercent,
                    primaryMins,
                    primaryLabel,
                    primaryResetEpoch,
                    secondaryPercent,
                    secondaryMins,
                    secondaryLabel,
                    secondaryResetEpoch,
                    resetCredits
            );

            telemetrySnapshot.set(snapshot);
        } catch (Exception e) {
            recordError("Error parsing rate limits: " + e.getMessage());
        }
    }

    private long parseEpochSeconds(JsonElement el) {
        try {
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) {
                    long val = el.getAsLong();
                    return val > 1_000_000_000_000L ? val / 1000 : val;
                } else {
                    return Instant.parse(el.getAsString()).getEpochSecond();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private long parseTimestamp(String str) {
        try {
            return Instant.parse(str).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public synchronized void shutdown() {
        this.connectionState = ConnectionState.STOPPED;

        // Complete any pending requests exceptionally
        for (Map.Entry<Integer, CompletableFuture<JsonObject>> entry : pendingRequests.entrySet()) {
            entry.getValue().completeExceptionally(new CancellationException("CodexAppServerClient has been shut down"));
        }
        pendingRequests.clear();
        activeServerRequests.clear();

        if (writer != null) {
            try {
                writer.close();
            } catch (Exception ignored) {
            }
            writer = null;
        }

        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
            reader = null;
        }

        if (errorReader != null) {
            try {
                errorReader.close();
            } catch (Exception ignored) {
            }
            errorReader = null;
        }

        if (appServerProcess != null) {
            try {
                appServerProcess.destroyForcibly();
            } catch (Exception ignored) {
            }
            appServerProcess = null;
        }
    }
}
