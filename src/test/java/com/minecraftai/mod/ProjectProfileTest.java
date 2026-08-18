package com.minecraftai.mod;

import com.minecraftai.mod.project.ProjectManager;
import com.minecraftai.mod.project.ProjectProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectProfileTest {

    @Test
    public void testProjectProfileDefaults() {
        ProjectProfile profile = new ProjectProfile("demo-project", "/projects/demo-project");
        assertEquals("demo-project", profile.getName());
        assertEquals("/projects/demo-project", profile.getWorkingDirectory());
        assertEquals("claude-opus-5", profile.getDefaultClaudeModel());
        assertEquals("plan", profile.getDefaultClaudePermissionMode());
    }

    @Test
    public void testProjectManagerRegistration() {
        ProjectManager pm = ProjectManager.getInstance();
        assertNotNull(pm.getActiveProject());
    }
}
