package com.zonefall.loot.source;

import org.bukkit.Material;

/**
 * Prototype world loot source types.
 */
public enum LootSourceType {
    SUPPLY_CRATE("Supply Crate", Material.BARREL, "supply-crate"),
    SCRAP_CACHE("Scrap Cache", Material.GRAVEL, "scrap-cache"),
    ORE_NODE("Ore Node", Material.IRON_ORE, "ore-node");

    private final String displayName;
    private final Material markerMaterial;
    private final String configKey;

    LootSourceType(String displayName, Material markerMaterial, String configKey) {
        this.displayName = displayName;
        this.markerMaterial = markerMaterial;
        this.configKey = configKey;
    }

    public String displayName() {
        return displayName;
    }

    public Material markerMaterial() {
        return markerMaterial;
    }

    public String configKey() {
        return configKey;
    }
}

