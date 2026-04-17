package com.zonefall.extract;

import org.bukkit.Location;

/**
 * Circular extraction area in one world.
 */
public record ExtractionZone(String id, Location center, double radius, double verticalTolerance) {
    public boolean contains(Location location) {
        if (!center.getWorld().equals(location.getWorld())) {
            return false;
        }
        if (Math.abs(location.getY() - center.getY()) > verticalTolerance) {
            return false;
        }
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return (dx * dx + dz * dz) <= radius * radius;
    }

    public String describe() {
        return id + "@"
                + center.getBlockX() + ","
                + center.getBlockY() + ","
                + center.getBlockZ()
                + " r=" + radius;
    }
}

