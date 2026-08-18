package com.minecraftai.mod.agent;

import java.util.OptionalDouble;

/**
 * Immutable snapshot of provider rate limit and quota telemetry.
 * All rate limit values default to unknown (empty OptionalDouble) rather than optimistic fake defaults.
 */
public record ProviderTelemetrySnapshot(
        OptionalDouble primaryRemainingPercent,
        int primaryWindowDurationMins,
        String primaryWindowLabel,
        long primaryResetsAtEpochSeconds,
        OptionalDouble secondaryRemainingPercent,
        int secondaryWindowDurationMins,
        String secondaryWindowLabel,
        long secondaryResetsAtEpochSeconds,
        int resetCredits,
        boolean hasData,
        long timestamp
) {

    public static ProviderTelemetrySnapshot unknown() {
        return new ProviderTelemetrySnapshot(
                OptionalDouble.empty(),
                0,
                "5H",
                0,
                OptionalDouble.empty(),
                0,
                "Weekly",
                0,
                0,
                false,
                System.currentTimeMillis()
        );
    }

    public static ProviderTelemetrySnapshot of(
            OptionalDouble primaryPercent,
            int primaryMins,
            String primaryLabel,
            long primaryResetEpoch,
            OptionalDouble secondaryPercent,
            int secondaryMins,
            String secondaryLabel,
            long secondaryResetEpoch,
            int credits
    ) {
        return new ProviderTelemetrySnapshot(
                primaryPercent,
                primaryMins,
                primaryLabel != null ? primaryLabel : deriveWindowLabel(primaryMins, "5H"),
                primaryResetEpoch,
                secondaryPercent,
                secondaryMins,
                secondaryLabel != null ? secondaryLabel : deriveWindowLabel(secondaryMins, "Weekly"),
                secondaryResetEpoch,
                credits,
                primaryPercent.isPresent() || secondaryPercent.isPresent() || credits > 0,
                System.currentTimeMillis()
        );
    }

    public static String deriveWindowLabel(int windowMins, String fallback) {
        if (windowMins <= 0) return fallback;
        if (windowMins % 1440 == 0) {
            int days = windowMins / 1440;
            return (days == 7) ? "Weekly" : (days + "D");
        }
        if (windowMins % 60 == 0) {
            return (windowMins / 60) + "H";
        }
        return windowMins + "m";
    }

    public String formatPrimaryCountdown() {
        return formatCountdown(primaryResetsAtEpochSeconds);
    }

    public String formatSecondaryCountdown() {
        return formatCountdown(secondaryResetsAtEpochSeconds);
    }

    private static String formatCountdown(long resetEpochSeconds) {
        if (resetEpochSeconds <= 0) return "N/A";
        long nowSec = System.currentTimeMillis() / 1000;
        long diff = resetEpochSeconds - nowSec;
        if (diff <= 0) return "Soon";
        long hours = diff / 3600;
        long minutes = (diff % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
