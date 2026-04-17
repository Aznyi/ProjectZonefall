package com.zonefall.matchmaking;

import java.util.UUID;

/**
 * Future queueing interface for solos and teams.
 */
public interface MatchmakingService {
    void enqueueSolo(UUID playerId);

    void leaveQueue(UUID playerId);
}

