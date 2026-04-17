package com.zonefall.party;

import java.util.UUID;

/**
 * Future party contract for grouped queue entry.
 */
public interface PartyService {
    boolean areInSameParty(UUID firstPlayerId, UUID secondPlayerId);
}

