package com.minecraftai.mod.parser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecraftai.mod.agent.AgentTelemetry;
import com.minecraftai.mod.agent.ApprovalRequest;

import java.util.UUID;
import java.util.function.Consumer;

public class CodexStreamParser implements StreamParser {

    private String lastAction = "";
    private AgentTelemetry telemetry;

    public void setTelemetry(AgentTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public void parseLine(String line, Consumer<AgentEvent> eventConsumer) {
        if (line == null || line.isBlank()) {
            return;
        }

        line = line.trim();
        if (!line.startsWith("{") || !line.endsWith("}")) {
            if (line.startsWith("Reading additional input")) {
                return;
            }
            if (line.startsWith("Error:") || line.startsWith("Not inside a trusted")) {
                eventConsumer.accept(AgentEvent.error(line));
            } else if (!line.isBlank()) {
                eventConsumer.accept(AgentEvent.activity(line));
            }
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            if (!json.has("type")) {
                return;
            }

            String type = json.get("type").getAsString();

            if ("thread.started".equals(type)) {
                if (json.has("thread_id")) {
                    String threadId = json.get("thread_id").getAsString();
                    eventConsumer.accept(AgentEvent.sessionId(threadId));
                }
            } else if ("item.started".equals(type)) {
                if (json.has("item") && json.get("item").isJsonObject()) {
                    handleItemStarted(json.getAsJsonObject("item"), eventConsumer);
                }
            } else if ("item.completed".equals(type)) {
                if (json.has("item") && json.get("item").isJsonObject()) {
                    handleItemCompleted(json.getAsJsonObject("item"), eventConsumer);
                }
            } else if ("turn.completed".equals(type)) {
                // Real turn completion without fabricated token increment
                eventConsumer.accept(AgentEvent.done(null));
            } else if ("error".equals(type)) {
                String msg = json.has("message") ? json.get("message").getAsString() : "Error";
                eventConsumer.accept(AgentEvent.error(msg));
            } else if ("turn.failed".equals(type)) {
                String errorMsg = "Turn failed";
                if (json.has("error") && json.get("error").isJsonObject() && json.getAsJsonObject("error").has("message")) {
                    errorMsg = json.getAsJsonObject("error").get("message").getAsString();
                }
                eventConsumer.accept(AgentEvent.error(errorMsg));
            }
        } catch (Exception ignored) {
        }
    }

    private void handleItemStarted(JsonObject item, Consumer<AgentEvent> eventConsumer) {
        if (!item.has("type")) {
            return;
        }
        String itemType = item.get("type").getAsString();

        String action = null;
        if ("command_execution".equals(itemType)) {
            String cmd = item.has("command") ? item.get("command").getAsString() : "";
            action = "Running " + cmd;

            if (cmd.startsWith("rm ") || cmd.startsWith("git push")) {
                ApprovalRequest req = new ApprovalRequest(UUID.randomUUID().toString(), "Codex", "Command", cmd);
                eventConsumer.accept(AgentEvent.approvalRequest(req));
            }
        } else if ("file_edit".equals(itemType)) {
            String path = item.has("path") ? item.get("path").getAsString() : "file";
            action = "Editing " + path;
        } else if ("file_read".equals(itemType)) {
            String path = item.has("path") ? item.get("path").getAsString() : "file";
            action = "Reading " + path;
        } else if ("search".equals(itemType)) {
            String query = item.has("query") ? item.get("query").getAsString() : "";
            action = "Searching for " + query;
        }

        if (action != null && !action.equals(lastAction)) {
            lastAction = action;
            eventConsumer.accept(AgentEvent.activity(action));
        }
    }

    private void handleItemCompleted(JsonObject item, Consumer<AgentEvent> eventConsumer) {
        if (!item.has("type")) {
            return;
        }
        String itemType = item.get("type").getAsString();

        if ("agent_message".equals(itemType)) {
            String text = item.has("text") ? item.get("text").getAsString() : "";
            if (!text.isBlank()) {
                eventConsumer.accept(AgentEvent.response(text));
            }
        } else if ("command_execution".equals(itemType)) {
            if (item.has("exit_code")) {
                int code = item.get("exit_code").getAsInt();
                if (code == 0) {
                    if (item.has("output")) {
                        String out = item.get("output").getAsString();
                        if (out.contains("passed") || out.contains("PASS") || out.contains("SUCCESS")) {
                            for (String l : out.split("\n")) {
                                if (l.contains("passed") || l.contains("tests")) {
                                    eventConsumer.accept(AgentEvent.success(l.trim()));
                                    return;
                                }
                            }
                        }
                    }
                } else {
                    eventConsumer.accept(AgentEvent.error("Command exited with code " + code));
                }
            }
        }
    }

    @Override
    public void reset() {
        lastAction = "";
    }
}
