package com.zonefall.loot.table;

import com.zonefall.loot.LootType;

/**
 * One weighted outcome in a loot table.
 */
public record WeightedLootEntry(LootType type, int minAmount, int maxAmount, int weight) {
    public WeightedLootEntry {
        if (minAmount < 0 || maxAmount < minAmount || weight < 0) {
            throw new IllegalArgumentException("Invalid weighted loot entry values.");
        }
    }
}

