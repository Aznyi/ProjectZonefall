package com.zonefall.arena;

import com.zonefall.core.ZonefallConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Startup validation for authored arenas. Warnings are non-fatal for local iteration.
 */
public final class ArenaValidator {
    private ArenaValidator() {
    }

    public static void validate(Plugin plugin, ZonefallConfig config, ArenaDefinition arena) {
        Location hub = config.hubSpawn().toLocation();
        warnIfRegionInsideHub(plugin, config, arena, hub);
        validateLandPoint(plugin, arena, "center", arena.center().toLocation());
        validateLandPoint(plugin, arena, "join-spawn", arena.joinSpawn().toLocation());
        validateLandPoint(plugin, arena, "spectator-point", arena.spectatorPoint().toLocation());
        arena.extractions().forEach(point -> validateLandPoint(plugin, arena, "extraction", point.toLocation()));
        arena.lootSources().forEach(source -> validateLandPoint(plugin, arena, "loot:" + source.id(), source.location().toLocation()));
        arena.objectives().forEach(objective -> validateLandPoint(plugin, arena, "objective:" + objective.id(), objective.location().toLocation()));
    }

    private static void warnIfRegionInsideHub(Plugin plugin, ZonefallConfig config, ArenaDefinition arena, Location hub) {
        if (!hub.getWorld().getName().equals(arena.worldName())) {
            return;
        }
        double distance = distanceFromPointToRegion2d(hub, arena.playableRegion());
        if (distance < config.hubExclusionRadius()) {
            plugin.getLogger().warning("Arena " + arena.id() + " playable region is within hub exclusion radius. distance="
                    + Math.round(distance) + " required=" + config.hubExclusionRadius());
        }
    }

    private static double distanceFromPointToRegion2d(Location point, CuboidRegion region) {
        int x = point.getBlockX();
        int z = point.getBlockZ();
        int dx = Math.max(Math.max(region.minX() - x, 0), x - region.maxX());
        int dz = Math.max(Math.max(region.minZ() - z, 0), z - region.maxZ());
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void validateLandPoint(Plugin plugin, ArenaDefinition arena, String label, Location location) {
        World world = location.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Arena " + arena.id() + " " + label + " has no loaded world.");
            return;
        }
        Material ground = world.getBlockAt(location.getBlockX(), Math.max(world.getMinHeight(), location.getBlockY() - 1), location.getBlockZ()).getType();
        if (ground == Material.WATER || ground == Material.LAVA || ground.name().contains("WATER")) {
            plugin.getLogger().warning("Arena " + arena.id() + " " + label + " appears to be over liquid at "
                    + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        }
        if (!ground.isSolid()) {
            plugin.getLogger().warning("Arena " + arena.id() + " " + label + " may not be on land. ground=" + ground
                    + " at " + location.getBlockX() + "," + (location.getBlockY() - 1) + "," + location.getBlockZ());
        }
    }
}
