package com.minecraftai.mod;

import com.minecraftai.mod.agent.TurnInfo;
import com.minecraftai.mod.codex.CodexAppServerClient;
import com.minecraftai.mod.gui.AutocompletePopup;
import com.minecraftai.mod.input.PromptStash;
import com.minecraftai.mod.task.BackgroundTask;
import com.minecraftai.mod.task.TaskManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

public class CliParityTest {

    @Test
    public void testPromptStashDrafts() {
        PromptStash stash = PromptStash.getInstance();
        stash.saveDraft("testProject", "session-1", "fix the failing auth tests");

        String draft = stash.getDraft("testProject", "session-1");
        Assertions.assertEquals("fix the failing auth tests", draft);

        stash.saveDraft("testProject", "session-1", "");
        Assertions.assertEquals("", stash.getDraft("testProject", "session-1"));
    }

    @Test
    public void testPromptStashNamedPushPop() {
        PromptStash stash = PromptStash.getInstance();
        stash.pushNamedStash("projX", "stash item 1");
        stash.pushNamedStash("projX", "stash item 2");

        List<String> list = stash.getNamedStashes("projX");
        Assertions.assertTrue(list.size() >= 2);
        Assertions.assertEquals("stash item 2", list.get(0));

        String popped = stash.popNamedStash("projX");
        Assertions.assertEquals("stash item 2", popped);
    }

    @Test
    public void testTaskManager() {
        BackgroundTask task = new BackgroundTask("task-test", "testCmd", "echo test", BackgroundTask.Type.BASH);
        Assertions.assertNotNull(task);
        Assertions.assertEquals(BackgroundTask.Status.RUNNING, task.getStatus());
        Assertions.assertTrue(task.getFormattedElapsed().contains("s") || task.getFormattedElapsed().contains("m"));

        task.stop();
        Assertions.assertEquals(BackgroundTask.Status.STOPPED, task.getStatus());
    }

    @Test
    public void testTurnInfo() {
        TurnInfo turn = new TurnInfo("turn-1", "refactor auth token rotation", "token rotation refactored", "git-commit-1234");
        Assertions.assertEquals("turn-1", turn.getTurnId());
        Assertions.assertEquals("refactor auth token rotation", turn.getPrompt());
        Assertions.assertEquals("git-commit-1234", turn.getGitCheckpointCommit());
        Assertions.assertEquals(TurnInfo.Status.RUNNING, turn.getStatus());

        turn.setStatus(TurnInfo.Status.SUCCESS);
        Assertions.assertEquals(TurnInfo.Status.SUCCESS, turn.getStatus());
    }

    @Test
    public void testAutocompletePopupCommands() {
        AutocompletePopup popup = new AutocompletePopup();
        popup.update("/cla", 4);

        Assertions.assertTrue(popup.isActive());
        StringBuilder sb = new StringBuilder("/cla");
        String chosen = popup.applySelection(sb, 4);

        Assertions.assertNotNull(chosen);
        Assertions.assertTrue(chosen.startsWith("/claude"));
    }

    @Test
    public void testCodexAppServerClientPins() {
        CodexAppServerClient client = CodexAppServerClient.getInstance();
        client.setPinned("thread-abc", true);
        client.setArchived("thread-xyz", true);

        client.setPinned("thread-abc", false);
        client.setArchived("thread-xyz", false);
    }
}
