package com.zonefall.profile;

import java.util.UUID;

/**
 * Minimal profile record. Future versions will hold progression, unlocks, and statistics.
 */
public record PlayerProfile(UUID playerId) {
}

