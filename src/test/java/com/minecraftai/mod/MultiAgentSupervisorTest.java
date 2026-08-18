package com.minecraftai.mod;

import com.minecraftai.mod.agent.*;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.workspace.WorkspaceMode;
import com.minecraftai.mod.workspace.WorktreeManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

public class MultiAgentSupervisorTest {

    @Test
    public void testMultiAgentPoolAndFocus() {
        AgentManager am = AgentManager.getInstance();

        AgentInstance inst1 = am.spawnInstance("Claude", "auth-refactor", WorkspaceMode.SHARED_WORKING_TREE);
        AgentInstance inst2 = am.spawnInstance("Codex", "test-review", WorkspaceMode.SHARED_WORKING_TREE);

        Assertions.assertNotNull(inst1);
        Assertions.assertNotNull(inst2);
        Assertions.assertEquals("Claude/auth-refactor", inst1.getAgentName());
        Assertions.assertEquals("Codex/test-review", inst2.getAgentName());

        // Test Focus
        am.setFocusedInstance("claude/auth-refactor");
        Assertions.assertEquals(inst1.getInstanceId(), am.getFocusedInstance().getInstanceId());

        am.setFocusedInstance("codex/test-review");
        Assertions.assertEquals(inst2.getInstanceId(), am.getFocusedInstance().getInstanceId());

        // Test Cycling
        AgentInstance cycled = am.cycleNextInstance();
        Assertions.assertNotNull(cycled);
    }

    @Test
    public void testIndependentPermissionsAndModels() {
        ProjectProfile proj = new ProjectProfile("demo", new File(".").getAbsolutePath());
        ClaudeProvider cp = new ClaudeProvider();

        AgentInstance instA = cp.spawnInstance("auth-refactor", proj, WorkspaceMode.SHARED_WORKING_TREE);
        AgentInstance instB = cp.spawnInstance("frontend-polish", proj, WorkspaceMode.SHARED_WORKING_TREE);

        instA.setModel("claude-opus-5");
        instA.setPermissionMode("bypassPermissions");

        instB.setModel("claude-sonnet-4-5");
        instB.setPermissionMode("plan");

        Assertions.assertEquals("claude-opus-5", instA.getModel());
        Assertions.assertEquals("bypassPermissions", instA.getPermissionMode());

        Assertions.assertEquals("claude-sonnet-4-5", instB.getModel());
        Assertions.assertEquals("plan", instB.getPermissionMode());
    }

    @Test
    public void testWorktreeManagerFailClosed() {
        WorktreeManager wm = WorktreeManager.getInstance();
        File nonGitDir = new File("build");
        // Must fail closed (return null) on non-Git or invalid directories
        File result = wm.createWorktree(nonGitDir, "Claude", "temp-test");
        Assertions.assertNull(result, "WorktreeManager must fail closed and return null on non-Git directories");
    }
}
