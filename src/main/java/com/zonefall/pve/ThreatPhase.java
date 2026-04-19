package com.zonefall.pve;

import java.util.Locale;

/**
 * Coarse threat phases used for mob eligibility and late-round bias.
 */
public enum ThreatPhase {
    EARLY,
    MID,
    LATE;

    public static ThreatPhase fromConfig(String value, ThreatPhase fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ThreatPhase.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
