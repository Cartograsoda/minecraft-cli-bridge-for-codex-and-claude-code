package com.minecraftai.mod;

import com.minecraftai.mod.parser.AgentEvent;
import com.minecraftai.mod.parser.CodexStreamParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CodexStreamParserTest {

    private CodexStreamParser parser;
    private List<AgentEvent> events;

    @BeforeEach
    public void setup() {
        parser = new CodexStreamParser();
        events = new ArrayList<>();
    }

    @Test
    public void testThreadStarted() {
        String line = "{\"type\":\"thread.started\",\"thread_id\":\"01a016d5-9f53-7101-ad68-e6645c253094\"}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.SESSION_ID, events.get(0).getType());
        assertEquals("01a016d5-9f53-7101-ad68-e6645c253094", events.get(0).getContent());
    }

    @Test
    public void testItemStartedFileRead() {
        String line = "{\"type\":\"item.started\",\"item\":{\"type\":\"file_read\",\"path\":\"src/auth.ts\"}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ACTIVITY, events.get(0).getType());
        assertEquals("Reading src/auth.ts", events.get(0).getContent());
    }

    @Test
    public void testItemStartedSearch() {
        String line = "{\"type\":\"item.started\",\"item\":{\"type\":\"search\",\"query\":\"refreshToken\"}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ACTIVITY, events.get(0).getType());
        assertEquals("Searching for refreshToken", events.get(0).getContent());
    }

    @Test
    public void testItemStartedCommandExecution() {
        String line = "{\"type\":\"item.started\",\"item\":{\"type\":\"command_execution\",\"command\":\"npm test\"}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ACTIVITY, events.get(0).getType());
        assertEquals("Running npm test", events.get(0).getContent());
    }

    @Test
    public void testCommandExecutionSuccess() {
        String line = "{\"type\":\"item.completed\",\"item\":{\"type\":\"command_execution\",\"exit_code\":0,\"output\":\"84 tests passed\"}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.SUCCESS, events.get(0).getType());
        assertEquals("84 tests passed", events.get(0).getContent());
    }

    @Test
    public void testAgentMessage() {
        String line = "{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\"Done — fixed refresh-token rotation.\"}}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.RESPONSE, events.get(0).getType());
        assertEquals("Done — fixed refresh-token rotation.", events.get(0).getContent());
    }

    @Test
    public void testTurnCompleted() {
        String line = "{\"type\":\"turn.completed\"}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.DONE, events.get(0).getType());
    }

    @Test
    public void testError() {
        String line = "{\"type\":\"error\",\"message\":\"You've hit your usage limit.\"}";
        parser.parseLine(line, events::add);

        assertEquals(1, events.size());
        assertEquals(AgentEvent.EventType.ERROR, events.get(0).getType());
        assertEquals("You've hit your usage limit.", events.get(0).getContent());
    }
}
