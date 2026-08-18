package com.minecraftai.mod;

import com.minecraftai.mod.agent.SessionHistoryManager;
import com.minecraftai.mod.agent.SessionInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionHistoryTest {

    @Test
    public void testGetClaudeSessions() {
        List<SessionInfo> claudeSessions = SessionHistoryManager.getClaudeSessions();
        assertNotNull(claudeSessions);
        // It shouldn't crash regardless of whether history files exist
    }

    @Test
    public void testGetCodexSessions() {
        List<SessionInfo> codexSessions = SessionHistoryManager.getCodexSessions();
        assertNotNull(codexSessions);
    }

    @Test
    public void testFindSession() {
        SessionInfo info = SessionHistoryManager.findSession("non-existent-session-id-12345");
        assertNull(info);
    }
}
