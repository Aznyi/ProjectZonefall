package com.zonefall.match;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Result payload emitted when a match ends. Future systems can persist or reward from this object.
 */
public record MatchSummary(
        UUID matchId,
        MatchEndReason reason,
        Duration elapsed,
        Set<UUID> participants,
        Set<UUID> extractedPlayers,
        Set<UUID> eliminatedPlayers
) {
}

