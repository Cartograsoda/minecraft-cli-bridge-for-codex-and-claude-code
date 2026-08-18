package com.minecraftai.mod;

import com.minecraftai.mod.agent.*;
import com.minecraftai.mod.project.ProjectProfile;
import com.minecraftai.mod.workspace.WorkspaceMode;
import com.minecraftai.mod.workspace.WorktreeManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

public class AgentInstanceTest {

    @Test
    public void testFailClosedWorktreeHandling() {
        ProjectProfile proj = new ProjectProfile("demo", new File("build").getAbsolutePath());
        // build directory is not a git repo, so worktree creation will fail
        AgentInstance instance = new AgentInstance("claude/test-fail", "test-fail", "Claude", proj, WorkspaceMode.ISOLATED_WORKTREE);

        // Verify fail-closed invariant: mode must revert to SHARED_WORKING_TREE and isIsolated() == false
        Assertions.assertFalse(instance.isIsolated(), "AgentInstance must fail closed when worktree creation fails");
        Assertions.assertEquals(WorkspaceMode.SHARED_WORKING_TREE, instance.getWorkspaceMode());
    }

    @Test
    public void testFocusRoutingNoProxySplitBrain() {
        AgentManager am = AgentManager.getInstance();
        AgentInstance claudeInst = am.spawnInstance("Claude", "feature-x", WorkspaceMode.SHARED_WORKING_TREE);
        am.setFocusedInstance("claude/feature-x");

        // getClaudeAdapter should return the authoritative focused Claude instance
        AgentInstance adapter = am.getClaudeAdapter();
        Assertions.assertNotNull(adapter);
        Assertions.assertEquals("Claude/feature-x", adapter.getAgentName());

        // Verify state changes on adapter directly affect the AgentInstance
        adapter.setModel("claude-sonnet-4-5");
        Assertions.assertEquals("claude-sonnet-4-5", claudeInst.getModel());
    }

    @Test
    public void testTelemetryUnknownByDefault() {
        AgentTelemetry tel = new AgentTelemetry();
        // Telemetry must be unknown by default, not optimistic 100%
        Assertions.assertFalse(tel.isContextDataAvailable());
        Assertions.assertFalse(tel.isRateLimitDataAvailable());
        Assertions.assertTrue(Double.isNaN(tel.getContextRemainingPercent()));
        Assertions.assertTrue(Double.isNaN(tel.getFiveHourRemainingPercent()));
        Assertions.assertTrue(Double.isNaN(tel.getWeeklyRemainingPercent()));
    }

    @Test
    public void testWorktreeBranchNaming() {
        String branchClaude = WorktreeManager.getBranchName("Claude", "auth-refactor");
        String branchCodex = WorktreeManager.getBranchName("Codex", "auth-refactor");

        // Verify provider prefixes eliminate branch collisions
        Assertions.assertEquals("ai/claude-auth-refactor", branchClaude);
        Assertions.assertEquals("ai/codex-auth-refactor", branchCodex);
        Assertions.assertNotEquals(branchClaude, branchCodex);
    }
}
