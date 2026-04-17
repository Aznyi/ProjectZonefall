package com.zonefall.loot.table;

import com.zonefall.loot.LootBundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Small weighted table that rolls one or more resource outcomes.
 */
public final class WeightedLootTable {
    private final List<WeightedLootEntry> entries;
    private final int rolls;
    private final Random random = new Random();

    public WeightedLootTable(List<WeightedLootEntry> entries, int rolls) {
        this.entries = List.copyOf(entries);
        this.rolls = Math.max(1, rolls);
    }

    public LootBundle roll() {
        LootBundle bundle = LootBundle.empty();
        if (entries.isEmpty()) {
            return bundle;
        }
        for (int i = 0; i < rolls; i++) {
            WeightedLootEntry entry = pickEntry();
            if (entry == null) {
                continue;
            }
            int amount = entry.minAmount();
            if (entry.maxAmount() > entry.minAmount()) {
                amount += random.nextInt(entry.maxAmount() - entry.minAmount() + 1);
            }
            bundle.add(entry.type(), amount);
        }
        return bundle;
    }

    private WeightedLootEntry pickEntry() {
        int totalWeight = entries.stream().mapToInt(WeightedLootEntry::weight).sum();
        if (totalWeight <= 0) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (WeightedLootEntry entry : entries) {
            cursor += entry.weight();
            if (roll < cursor) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    public static WeightedLootTable fallback() {
        List<WeightedLootEntry> entries = new ArrayList<>();
        entries.add(new WeightedLootEntry(com.zonefall.loot.LootType.SCRAP, 1, 3, 60));
        entries.add(new WeightedLootEntry(com.zonefall.loot.LootType.CLOTH, 1, 2, 25));
        entries.add(new WeightedLootEntry(com.zonefall.loot.LootType.IRON_FRAGMENTS, 1, 1, 10));
        entries.add(new WeightedLootEntry(com.zonefall.loot.LootType.GEMS, 1, 1, 5));
        return new WeightedLootTable(entries, 2);
    }
}

