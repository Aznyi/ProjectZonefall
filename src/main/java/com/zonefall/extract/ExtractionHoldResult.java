package com.zonefall.extract;

/**
 * Result of one extraction hold tick.
 */
public record ExtractionHoldResult(boolean complete, boolean active, int progressSeconds, int requiredSeconds) {
    public static ExtractionHoldResult idle() {
        return new ExtractionHoldResult(false, false, 0, 0);
    }

    public static ExtractionHoldResult completed() {
        return new ExtractionHoldResult(true, false, 0, 0);
    }

    public static ExtractionHoldResult inProgress(int progressSeconds, int requiredSeconds) {
        return new ExtractionHoldResult(false, true, progressSeconds, requiredSeconds);
    }
}
