package com.zonefall.customblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable block coordinate key used by YAML persistence and helper-light ownership.
 */
public record BlockKey(String worldName, int x, int y, int z) {
    public static BlockKey from(Block block) {
        return from(block.getLocation());
    }

    public static BlockKey from(Location location) {
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static Optional<BlockKey> parse(String value) {
        String[] parts = value.split(",", 4);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Optional<BlockKey> parsePathKey(String value) {
        return parse(value.replace("%2E", ".").replace("%25", "%"));
    }

    public String serialize() {
        return worldName + "," + x + "," + y + "," + z;
    }

    public String pathKey() {
        return serialize().replace("%", "%25").replace(".", "%2E");
    }

    public Optional<Block> blockIfLoaded() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return Optional.empty();
        }
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return Optional.empty();
        }
        return Optional.of(world.getBlockAt(x, y, z));
    }

    public boolean isInChunk(World world, int chunkX, int chunkZ) {
        return Objects.equals(worldName, world.getName()) && (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
}
