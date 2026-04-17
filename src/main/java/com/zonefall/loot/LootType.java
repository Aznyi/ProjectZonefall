package com.zonefall.loot;

/**
 * First set of extraction resources tracked by the plugin economy.
 */
public enum LootType {
    SCRAP("Scrap", LootRarity.COMMON, 1),
    CLOTH("Cloth", LootRarity.COMMON, 1),
    STONE("Stone", LootRarity.COMMON, 1),
    IRON_FRAGMENTS("Iron Fragments", LootRarity.UNCOMMON, 3),
    GEMS("Gems", LootRarity.RARE, 8);

    private final String displayName;
    private final LootRarity rarity;
    private final int value;

    LootType(String displayName, LootRarity rarity, int value) {
        this.displayName = displayName;
        this.rarity = rarity;
        this.value = value;
    }

    public String displayName() {
        return displayName;
    }

    public LootRarity rarity() {
        return rarity;
    }

    public int value() {
        return value;
    }
}

