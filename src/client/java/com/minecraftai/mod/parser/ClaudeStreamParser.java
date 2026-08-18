package com.minecraftai.mod.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecraftai.mod.agent.AgentTelemetry;
import com.minecraftai.mod.agent.ApprovalRequest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class ClaudeStreamParser implements StreamParser {

    private final Set<String> processedEvents = new HashSet<>();
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
            if (!line.startsWith("Error:") && !line.startsWith("Warning:")) {
                if (!line.isBlank()) {
                    eventConsumer.accept(AgentEvent.activity(line));
                }
            } else {
                eventConsumer.accept(AgentEvent.error(line));
            }
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            if (!json.has("type")) {
                return;
            }

            String type = json.get("type").getAsString();

            if ("system".equals(type)) {
                if (json.has("session_id")) {
                    String sessionId = json.get("session_id").getAsString();
                    eventConsumer.accept(AgentEvent.sessionId(sessionId));
                }
                if (json.has("subtype") && "init".equals(json.get("subtype").getAsString())) {
                    eventConsumer.accept(AgentEvent.activity("Inspecting project..."));
                }
            } else if ("rate_limit_event".equals(type)) {
                if (json.has("session_id")) {
                    eventConsumer.accept(AgentEvent.sessionId(json.get("session_id").getAsString()));
                }
                if (json.has("rate_limit_info") && json.get("rate_limit_info").isJsonObject()) {
                    JsonObject info = json.getAsJsonObject("rate_limit_info");
                    if (info.has("resetsAt") && telemetry != null) {
                        long resetsAt = info.get("resetsAt").getAsLong();
                        if (info.has("used_percentage")) {
                            double used = info.get("used_percentage").getAsDouble();
                            telemetry.updateFiveHourLimit(Math.max(0.0, 100.0 - used), resetsAt);
                        } else if (info.has("remaining_percentage")) {
                            double rem = info.get("remaining_percentage").getAsDouble();
                            telemetry.updateFiveHourLimit(rem, resetsAt);
                        } else {
                            // Do not estimate quota from time remaining; keep percentage unknown (NaN)
                            telemetry.updateFiveHourLimit(Double.NaN, resetsAt);
                        }
                    }
                }
            } else if ("assistant".equals(type)) {
                if (json.has("session_id")) {
                    eventConsumer.accept(AgentEvent.sessionId(json.get("session_id").getAsString()));
                }
                if (json.has("message") && json.get("message").isJsonObject()) {
                    JsonObject message = json.getAsJsonObject("message");
                    // Extract usage if available
                    if (message.has("usage") && message.get("usage").isJsonObject() && telemetry != null) {
                        JsonObject usage = message.getAsJsonObject("usage");
                        int inputToks = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                        int outputToks = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                        int cacheRead = usage.has("cache_read_input_tokens") ? usage.get("cache_read_input_tokens").getAsInt() : 0;
                        int totalUsed = inputToks + outputToks + cacheRead;
                        telemetry.updateContext(totalUsed, 200000);
                        telemetry.addTokens(outputToks);
                    }
                    if (message.has("content") && message.get("content").isJsonArray()) {
                        JsonArray content = message.getAsJsonArray("content");
                        for (JsonElement elem : content) {
                            if (elem.isJsonObject()) {
                                handleContentObject(elem.getAsJsonObject(), eventConsumer);
                            }
                        }
                    }
                }
            } else if ("content_block_start".equals(type)) {
                if (json.has("content_block") && json.get("content_block").isJsonObject()) {
                    handleContentObject(json.getAsJsonObject("content_block"), eventConsumer);
                }
            } else if ("result".equals(type)) {
                if (json.has("total_cost_usd") && telemetry != null) {
                    telemetry.addCost(json.get("total_cost_usd").getAsDouble());
                }
                String resultText = json.has("result") && !json.get("result").isJsonNull() ? json.get("result").getAsString() : "";
                boolean isError = json.has("is_error") && json.get("is_error").getAsBoolean();
                if (isError) {
                    eventConsumer.accept(AgentEvent.error(resultText.isBlank() ? "Execution failed" : resultText));
                } else {
                    if (!resultText.isBlank() && resultText.length() < 120) {
                        eventConsumer.accept(AgentEvent.done(resultText));
                    } else {
                        if (!resultText.isBlank()) {
                            eventConsumer.accept(AgentEvent.response(resultText));
                        }
                        eventConsumer.accept(AgentEvent.done(null));
                    }
                }
            } else if ("error".equals(type)) {
                String errorMsg = "Error occurred";
                if (json.has("error") && json.get("error").isJsonObject() && json.getAsJsonObject("error").has("message")) {
                    errorMsg = json.getAsJsonObject("error").get("message").getAsString();
                } else if (json.has("message")) {
                    errorMsg = json.get("message").getAsString();
                }
                eventConsumer.accept(AgentEvent.error(errorMsg));
            }
        } catch (Exception ignored) {
        }
    }

    private void handleContentObject(JsonObject obj, Consumer<AgentEvent> eventConsumer) {
        if (!obj.has("type")) {
            return;
        }
        String contentType = obj.get("type").getAsString();

        if ("tool_use".equals(contentType)) {
            String name = obj.has("name") ? obj.get("name").getAsString() : "Tool";
            JsonObject input = obj.has("input") && obj.get("input").isJsonObject() ? obj.getAsJsonObject("input") : null;

            String action = formatToolAction(name, input);
            if (!action.equals(lastAction)) {
                lastAction = action;
                eventConsumer.accept(AgentEvent.activity(action));
            }

            // Check if tool needs approval
            if (name.equals("Bash") && input != null && input.has("command")) {
                String cmd = input.get("command").getAsString();
                if (cmd.startsWith("rm ") || cmd.startsWith("git push") || cmd.startsWith("npm publish")) {
                    ApprovalRequest req = new ApprovalRequest(UUID.randomUUID().toString(), "Claude", "Bash", cmd);
                    eventConsumer.accept(AgentEvent.approvalRequest(req));
                }
            }
        } else if ("text".equals(contentType)) {
            String text = obj.has("text") ? obj.get("text").getAsString() : "";
            if (!text.isBlank()) {
                eventConsumer.accept(AgentEvent.response(text));
            }
        }
    }

    private String formatToolAction(String toolName, JsonObject input) {
        if (input == null) {
            return "Using " + toolName + "...";
        }

        switch (toolName) {
            case "Read":
                if (input.has("file_path")) {
                    return "Reading " + input.get("file_path").getAsString();
                }
                break;
            case "Edit":
            case "Write":
            case "NotebookEdit":
                if (input.has("file_path")) {
                    return "Editing " + input.get("file_path").getAsString();
                }
                break;
            case "Glob":
                if (input.has("pattern")) {
                    return "Finding " + input.get("pattern").getAsString();
                }
                break;
            case "Grep":
                if (input.has("query")) {
                    return "Searching for " + input.get("query").getAsString();
                }
                break;
            case "Bash":
            case "PowerShell":
                if (input.has("command")) {
                    return "Running " + input.get("command").getAsString();
                }
                break;
            case "WebSearch":
                if (input.has("query")) {
                    return "Searching web: " + input.get("query").getAsString();
                }
                break;
            case "WebFetch":
                if (input.has("url")) {
                    return "Fetching URL: " + input.get("url").getAsString();
                }
                break;
            case "Task":
                if (input.has("description")) {
                    return "Starting subagent: " + input.get("description").getAsString();
                }
                break;
        }

        return "Using " + toolName + "...";
    }

    @Override
    public void reset() {
        processedEvents.clear();
        lastAction = "";
    }
}
