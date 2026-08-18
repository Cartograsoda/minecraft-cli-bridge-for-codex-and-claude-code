package com.minecraftai.mod;

import com.minecraftai.mod.input.PromptHistory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PromptHistoryTest {

    @Test
    public void testPromptHistorySearch() {
        PromptHistory history = PromptHistory.getInstance();
        history.addPrompt("test_proj", "investigate oauth redirect issue");
        history.addPrompt("test_proj", "compare Android and iOS OAuth");
        history.addPrompt("test_proj", "fix database schema");

        List<String> results = history.search("test_proj", "oauth");
        assertEquals(2, results.size());
        assertTrue(results.get(0).contains("OAuth") || results.get(0).contains("oauth"));
    }
}
