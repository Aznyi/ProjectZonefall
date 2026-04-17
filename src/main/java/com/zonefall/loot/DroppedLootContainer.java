package com.zonefall.loot;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Plugin-managed death loot container. Later this can become a chest, hologram, or custom entity.
 */
public record DroppedLootContainer(UUID id, UUID ownerId, Location location, LootBundle contents) {
    public boolean isWithinPickupRange(Location playerLocation, double radius) {
        if (!location.getWorld().equals(playerLocation.getWorld())) {
            return false;
        }
        return location.distanceSquared(playerLocation) <= radius * radius;
    }
}

