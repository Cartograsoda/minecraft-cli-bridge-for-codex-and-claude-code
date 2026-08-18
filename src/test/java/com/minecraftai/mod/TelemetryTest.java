package com.minecraftai.mod;

import com.minecraftai.mod.agent.AgentTelemetry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TelemetryTest {

    @Test
    public void testContextBarFormatting() {
        AgentTelemetry telemetry = new AgentTelemetry();
        telemetry.updateContext(50000, 200000); // 25% used -> 75% left
        assertEquals(75.0, telemetry.getContextRemainingPercent(), 0.1);

        String bar = AgentTelemetry.formatBar(telemetry.getContextRemainingPercent(), 8);
        assertNotNull(bar);
        assertEquals(8, bar.length());
        assertTrue(bar.startsWith("██████"));
    }

    @Test
    public void testFiveHourLimit() {
        AgentTelemetry telemetry = new AgentTelemetry();
        long futureEpoch = (System.currentTimeMillis() / 1000) + 3600; // 1 hour from now
        telemetry.updateFiveHourLimit(50.0, futureEpoch);

        assertEquals(50.0, telemetry.getFiveHourRemainingPercent(), 0.1);
        String resetStr = telemetry.getFormattedResetTime();
        assertNotNull(resetStr);
        assertTrue(resetStr.contains("m") || resetStr.contains("h"));
    }
}
