package com.minecraftai.mod;

import com.minecraftai.mod.parser.AgentEvent;
import com.minecraftai.mod.parser.ClaudeStreamParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClaudeStreamParserTest {

    private ClaudeStreamParser parser;
    private List<AgentEvent> events;

    @BeforeEach
    public void setup() {
        parser = new ClaudeStreamParser();
        events = new ArrayList<>();
    }

    @Test
    public void testInitEvent() {
        String line = "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"e6aaaf0f-5788-4514-9a9c-b434287d3168\",\"cwd\":\"/workspace/project\"}";
        parser.parseLine(line, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getType() == AgentEvent.EventType.SESSION_ID && "e6aaaf0f-5788-4514-9a9c-b434287d3168".equals(e.getContent())));
        assertTrue(events.stream().anyMatch(e -> e.getType() == AgentEvent.EventType.ACTIVITY && "Inspecting project...".equals(e.getContent())));
    }

    @Test
    public void testToolUseRead() {
        String line = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Read\",\"input\":{\"file_path\":\"src/auth.ts\"}}]}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ACTIVITY, events.get(0).getType());
        assertEquals("Reading src/auth.ts", events.get(0).getContent());
    }

    @Test
    public void testToolUseEdit() {
        String line = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Edit\",\"input\":{\"file_path\":\"src/api/user.ts\"}}]}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ACTIVITY, events.get(0).getType());
        assertEquals("Editing src/api/user.ts", events.get(0).getContent());
    }

    @Test
    public void testToolUseBash() {
        String line = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\",\"input\":{\"command\":\"npm test\"}}]}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ACTIVITY, events.get(0).getType());
        assertEquals("Running npm test", events.get(0).getContent());
    }

    @Test
    public void testResultSuccess() {
        String line = "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"Done — fixed refresh-token rotation.\",\"is_error\":false}";
        parser.parseLine(line, events::add);

        assertTrue(events.stream().anyMatch(e -> e.getType() == AgentEvent.EventType.DONE && "Done — fixed refresh-token rotation.".equals(e.getContent())));
    }

    @Test
    public void testErrorEvent() {
        String line = "{\"type\":\"error\",\"error\":{\"message\":\"Rate limit exceeded\"}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ERROR, events.get(0).getType());
        assertEquals("Rate limit exceeded", events.get(0).getContent());
    }
}
