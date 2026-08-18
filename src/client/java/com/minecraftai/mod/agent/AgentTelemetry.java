package com.minecraftai.mod.agent;

import java.util.OptionalDouble;

public class AgentTelemetry {
    private int contextTokens = 0;
    private int maxContextTokens = 200000;
    private double contextRemainingPercent = Double.NaN;
    private boolean contextDataAvailable = false;

    private double primaryRemainingPercent = Double.NaN;
    private String primaryWindowLabel = "5H";
    private long primaryResetsAtEpochSeconds = 0;

    private double secondaryRemainingPercent = Double.NaN;
    private String secondaryWindowLabel = "Weekly";
    private long secondaryResetsAtEpochSeconds = 0;

    private int resetCredits = 0;
    private double totalCostUsd = 0.0;
    private int sessionTokens = 0;
    private boolean rateLimitDataAvailable = false;

    public synchronized void updateContext(int usedTokens, int maxTokens) {
        this.contextTokens = usedTokens;
        if (maxTokens > 0) {
            this.maxContextTokens = maxTokens;
            this.contextRemainingPercent = Math.max(0.0, Math.min(100.0, 100.0 - ((double) usedTokens / maxTokens * 100.0)));
            this.contextDataAvailable = true;
        }
    }

    public synchronized void updateFiveHourLimit(double remainingPercent, long resetsAtEpochSeconds) {
        updatePrimaryLimit(remainingPercent, "5H", resetsAtEpochSeconds);
    }

    public synchronized void updatePrimaryLimit(double remainingPercent, String windowLabel, long resetsAtEpochSeconds) {
        this.primaryRemainingPercent = Math.max(0.0, Math.min(100.0, remainingPercent));
        if (windowLabel != null && !windowLabel.isBlank()) {
            this.primaryWindowLabel = windowLabel;
        }
        this.primaryResetsAtEpochSeconds = resetsAtEpochSeconds;
        this.rateLimitDataAvailable = true;
    }

    public synchronized void updateWeeklyLimit(double remainingPercent, long resetsAtEpochSeconds) {
        updateSecondaryLimit(remainingPercent, "Weekly", resetsAtEpochSeconds);
    }

    public synchronized void updateSecondaryLimit(double remainingPercent, String windowLabel, long resetsAtEpochSeconds) {
        this.secondaryRemainingPercent = Math.max(0.0, Math.min(100.0, remainingPercent));
        if (windowLabel != null && !windowLabel.isBlank()) {
            this.secondaryWindowLabel = windowLabel;
        }
        this.secondaryResetsAtEpochSeconds = resetsAtEpochSeconds;
        this.rateLimitDataAvailable = true;
    }

    public synchronized void updateSnapshot(ProviderTelemetrySnapshot snapshot) {
        if (snapshot == null || !snapshot.hasData()) return;
        if (snapshot.primaryRemainingPercent().isPresent()) {
            this.primaryRemainingPercent = snapshot.primaryRemainingPercent().getAsDouble();
            this.primaryWindowLabel = snapshot.primaryWindowLabel();
            this.primaryResetsAtEpochSeconds = snapshot.primaryResetsAtEpochSeconds();
            this.rateLimitDataAvailable = true;
        }
        if (snapshot.secondaryRemainingPercent().isPresent()) {
            this.secondaryRemainingPercent = snapshot.secondaryRemainingPercent().getAsDouble();
            this.secondaryWindowLabel = snapshot.secondaryWindowLabel();
            this.secondaryResetsAtEpochSeconds = snapshot.secondaryResetsAtEpochSeconds();
            this.rateLimitDataAvailable = true;
        }
        this.resetCredits = snapshot.resetCredits();
    }

    public synchronized void addCost(double costUsd) {
        this.totalCostUsd += costUsd;
    }

    public synchronized void addTokens(int tokens) {
        this.sessionTokens += tokens;
    }

    public synchronized int getContextTokens() {
        return contextTokens;
    }

    public synchronized int getMaxContextTokens() {
        return maxContextTokens;
    }

    public synchronized double getContextRemainingPercent() {
        return contextRemainingPercent;
    }

    public synchronized OptionalDouble getContextRemainingOptional() {
        return contextDataAvailable && !Double.isNaN(contextRemainingPercent) ?
                OptionalDouble.of(contextRemainingPercent) : OptionalDouble.empty();
    }

    public synchronized double getFiveHourRemainingPercent() {
        return primaryRemainingPercent;
    }

    public synchronized OptionalDouble getPrimaryRemainingOptional() {
        return !Double.isNaN(primaryRemainingPercent) ?
                OptionalDouble.of(primaryRemainingPercent) : OptionalDouble.empty();
    }

    public synchronized String getPrimaryWindowLabel() {
        return primaryWindowLabel;
    }

    public synchronized long getFiveHourResetsAtEpochSeconds() {
        return primaryResetsAtEpochSeconds;
    }

    public synchronized double getWeeklyRemainingPercent() {
        return secondaryRemainingPercent;
    }

    public synchronized OptionalDouble getSecondaryRemainingOptional() {
        return !Double.isNaN(secondaryRemainingPercent) ?
                OptionalDouble.of(secondaryRemainingPercent) : OptionalDouble.empty();
    }

    public synchronized String getSecondaryWindowLabel() {
        return secondaryWindowLabel;
    }

    public synchronized long getWeeklyResetsAtEpochSeconds() {
        return secondaryResetsAtEpochSeconds;
    }

    public synchronized int getResetCredits() {
        return resetCredits;
    }

    public synchronized double getTotalCostUsd() {
        return totalCostUsd;
    }

    public synchronized int getSessionTokens() {
        return sessionTokens;
    }

    public synchronized boolean isRateLimitDataAvailable() {
        return rateLimitDataAvailable && (!Double.isNaN(primaryRemainingPercent) || !Double.isNaN(secondaryRemainingPercent));
    }

    public synchronized boolean isContextDataAvailable() {
        return contextDataAvailable && !Double.isNaN(contextRemainingPercent);
    }

    /**
     * Formats a clean progress bar like: ███████░░░
     */
    public static String formatBar(double percent, int barLength) {
        if (Double.isNaN(percent) || percent < 0) {
            return "░".repeat(barLength);
        }
        int filled = (int) Math.round((percent / 100.0) * barLength);
        filled = Math.max(0, Math.min(barLength, filled));
        int empty = barLength - filled;
        return "█".repeat(filled) + "░".repeat(empty);
    }

    /**
     * Formats remaining time until reset, e.g. "1h 42m" or "24m"
     */
    public synchronized String getFormattedResetTime() {
        if (primaryResetsAtEpochSeconds <= 0) {
            return "N/A";
        }
        long nowSeconds = System.currentTimeMillis() / 1000;
        long diff = primaryResetsAtEpochSeconds - nowSeconds;
        if (diff <= 0) {
            return "Soon";
        }
        long hours = diff / 3600;
        long minutes = (diff % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
