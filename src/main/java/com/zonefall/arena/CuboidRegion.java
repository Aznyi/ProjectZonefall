package com.zonefall.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Simple axis-aligned region for arena gameplay and spectator protection.
 */
public record CuboidRegion(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static CuboidRegion from(ConfigurationSection section, String fallbackWorld, int defaultRadius) {
        if (section == null) {
            return new CuboidRegion(fallbackWorld, -defaultRadius, 0, -defaultRadius, defaultRadius, 320, defaultRadius);
        }
        int x1 = section.getInt("min.x", -defaultRadius);
        int y1 = section.getInt("min.y", 0);
        int z1 = section.getInt("min.z", -defaultRadius);
        int x2 = section.getInt("max.x", defaultRadius);
        int y2 = section.getInt("max.y", 320);
        int z2 = section.getInt("max.z", defaultRadius);
        return new CuboidRegion(
                section.getString("world", fallbackWorld),
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        );
    }

    public boolean contains(Location location) {
        World world = location.getWorld();
        if (world == null || !world.getName().equals(worldName)) {
            return false;
        }
        return location.getBlockX() >= minX
                && location.getBlockX() <= maxX
                && location.getBlockY() >= minY
                && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ
                && location.getBlockZ() <= maxZ;
    }
}

