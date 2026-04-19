package com.zonefall.arena;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Hub-side physical trigger for joining an arena.
 */
public record ArenaJoinPoint(String id, LocationSpec location, double radius,
                             double labelOffsetX, double labelOffsetY, double labelOffsetZ) {
    public static ArenaJoinPoint from(String id, ConfigurationSection section, LocationSpec hubSpawn) {
        return new ArenaJoinPoint(
                id,
                LocationSpec.from(section, hubSpawn.worldName()),
                section.getDouble("radius", 1.5),
                section.getDouble("label-offset.x", 0.0),
                section.getDouble("label-offset.y", 0.0),
                section.getDouble("label-offset.z", 0.0)
        );
    }

    public boolean contains(Location playerLocation) {
        Location trigger = location.toLocation();
        if (!trigger.getWorld().equals(playerLocation.getWorld())) {
            return false;
        }
        return trigger.distanceSquared(playerLocation) <= radius * radius;
    }

    public String describe() {
        Location loc = location.toLocation();
        return id + "@"
                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + " r=" + radius
                + " labelOffset=" + labelOffsetX + "," + labelOffsetY + "," + labelOffsetZ;
    }
}
