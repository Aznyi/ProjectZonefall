package com.zonefall.loot;

import java.util.UUID;

/**
 * Records the stash delta created by a successful extraction.
 */
public record ExtractedLootSummary(UUID playerId, LootBundle loot) {
}

