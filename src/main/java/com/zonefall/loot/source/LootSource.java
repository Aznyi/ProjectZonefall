package com.zonefall.loot.source;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

/**
 * One interactable world loot source in a match.
 */
public final class LootSource {
    private final UUID id;
    private final LootSourceType type;
    private final Location location;
    private final Material originalMaterial;
    private boolean consumed;

    public LootSource(UUID id, LootSourceType type, Location location, Material originalMaterial) {
        this.id = id;
        this.type = type;
        this.location = location;
        this.originalMaterial = originalMaterial;
    }

    public UUID id() {
        return id;
    }

    public LootSourceType type() {
        return type;
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

    public void markConsumed() {
        consumed = true;
    }

    public String describe() {
        return type.displayName() + " at "
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ()
                + (consumed ? " consumed" : " available");
    }
}

