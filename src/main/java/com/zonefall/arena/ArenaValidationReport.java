package com.zonefall.arena;

import java.util.List;

/**
 * Immutable validation report for one configured arena.
 */
public record ArenaValidationReport(String arenaId, List<ValidationEntry> entries) {
    public long passes() {
        return count(ValidationStatus.PASS);
    }

    public long warnings() {
        return count(ValidationStatus.WARN);
    }

    public long failures() {
        return count(ValidationStatus.FAIL);
    }

    public String summary() {
        return passes() + " pass / " + warnings() + " warn / " + failures() + " fail";
    }

    private long count(ValidationStatus status) {
        return entries.stream().filter(entry -> entry.status() == status).count();
    }
}
