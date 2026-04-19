package com.zonefall.loot;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Mutable bundle of plugin-managed extraction loot.
 */
public final class LootBundle {
    private final EnumMap<LootType, Integer> amounts = new EnumMap<>(LootType.class);

    public static LootBundle empty() {
        return new LootBundle();
    }

    public static LootBundle single(LootType type, int amount) {
        LootBundle bundle = new LootBundle();
        bundle.add(type, amount);
        return bundle;
    }

    public void add(LootType type, int amount) {
        if (amount <= 0) {
            return;
        }
        amounts.merge(type, amount, Integer::sum);
    }

    public void addAll(LootBundle other) {
        other.amounts.forEach(this::add);
    }

    public LootBundle copy() {
        LootBundle copy = new LootBundle();
        copy.addAll(this);
        return copy;
    }

    public LootBundle bonusForMultiplier(double multiplier) {
        LootBundle bonus = new LootBundle();
        if (multiplier <= 1.0) {
            return bonus;
        }
        double bonusMultiplier = multiplier - 1.0;
        for (Map.Entry<LootType, Integer> entry : amounts.entrySet()) {
            int bonusAmount = (int) Math.floor(entry.getValue() * bonusMultiplier);
            if (bonusAmount <= 0 && entry.getValue() > 0) {
                bonusAmount = 1;
            }
            bonus.add(entry.getKey(), bonusAmount);
        }
        return bonus;
    }

    public Map<LootType, Integer> asMap() {
        return Map.copyOf(amounts);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public int totalValue() {
        int total = 0;
        for (Map.Entry<LootType, Integer> entry : amounts.entrySet()) {
            total += entry.getKey().value() * entry.getValue();
        }
        return total;
    }

    public String describe() {
        if (amounts.isEmpty()) {
            return "none";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<LootType, Integer> entry : amounts.entrySet()) {
            joiner.add(entry.getKey().displayName() + " x" + entry.getValue());
        }
        return joiner.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
