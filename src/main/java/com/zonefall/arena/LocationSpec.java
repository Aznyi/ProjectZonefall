package com.zonefall.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Config-friendly location value.
 */
public record LocationSpec(String worldName, double x, double y, double z, float yaw, float pitch) {
    public static LocationSpec from(ConfigurationSection section, String fallbackWorld) {
        if (section == null) {
            return new LocationSpec(fallbackWorld, 0.5, 80.0, 0.5, 0.0f, 0.0f);
        }
        return new LocationSpec(
                section.getString("world", fallbackWorld),
                section.getDouble("x", 0.5),
                section.getDouble("y", 80.0),
                section.getDouble("z", 0.5),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World is not loaded: " + worldName);
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}

