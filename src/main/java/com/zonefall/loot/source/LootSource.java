package com.zonefall.loot.source;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

/**
 * One interactable world loot source in a match.
 */
public final class LootSource {
    private final UUID id;
    private final String debugId;
    private final LootSourceType type;
    private final Location location;
    private final Material originalMaterial;
    private final String tableKey;
    private final String tier;
    private boolean active;
    private boolean consumed;

    public LootSource(UUID id, String debugId, LootSourceType type, Location location, Material originalMaterial,
                      String tableKey, String tier, boolean active) {
        this.id = id;
        this.debugId = debugId;
        this.type = type;
        this.location = location;
        this.originalMaterial = originalMaterial;
        this.tableKey = tableKey;
        this.tier = tier;
        this.active = active;
    }

    public UUID id() {
        return id;
    }

    public LootSourceType type() {
        return type;
    }

    public String debugId() {
        return debugId;
    }

    public String tableKey() {
        return tableKey;
    }

    public String tier() {
        return tier;
    }

    public Location location() {
        return location;
    }

    public Material originalMaterial() {
        return originalMaterial;
    }

    public boolean consumed() {
        return consumed;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void markConsumed() {
        consumed = true;
    }

    public String describe() {
        return type.displayName() + " at "
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ()
                + " id=" + debugId
                + " table=" + tableKey
                + " tier=" + tier
                + (active ? " active" : " inactive")
                + (consumed ? " consumed" : " available");
    }
}
