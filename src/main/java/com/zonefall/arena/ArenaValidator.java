package com.zonefall.arena;

import com.zonefall.core.ZonefallConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup validation for authored arenas. Warnings are non-fatal for local iteration.
 */
public final class ArenaValidator {
    private ArenaValidator() {
    }

    public static void validate(Plugin plugin, ZonefallConfig config, ArenaDefinition arena) {
        ArenaValidationReport report = report(config, arena);
        for (ValidationEntry entry : report.entries()) {
            if (entry.status() != ValidationStatus.PASS) {
                plugin.getLogger().warning("Arena " + arena.id() + " validation: " + entry.format(true));
            }
        }
    }

    public static ArenaValidationReport report(ZonefallConfig config, ArenaDefinition arena) {
        List<ValidationEntry> entries = new ArrayList<>();
        World arenaWorld = Bukkit.getWorld(arena.worldName());
        if (arenaWorld == null) {
            entries.add(new ValidationEntry(ValidationStatus.FAIL, "world", null, "world is not loaded: " + arena.worldName(), ""));
            return new ArenaValidationReport(arena.id(), List.copyOf(entries));
        }
        entries.add(new ValidationEntry(ValidationStatus.PASS, "world", arena.center().toLocation(),
                "world loaded as " + arenaWorld.getEnvironment(), "name=" + arena.worldName()));
        entries.add(dimensionEntry(arena));
        entries.add(hubDistanceEntry(config, arena));
        entries.add(regionEntry("playable-region", arena.center().toLocation(), arena.playableRegion()));
        entries.add(regionEntry("protected-region", arena.center().toLocation(), arena.protectedRegion()));
        entries.add(pointEntry(config, arena, "center", arena.center().toLocation()));
        entries.add(pointEntry(config, arena, "join-spawn", arena.joinSpawn().toLocation()));
        int index = 1;
        for (LocationSpec point : arena.extractions()) {
            entries.add(pointEntry(config, arena, "extraction-" + index, point.toLocation()));
            index++;
        }
        arena.lootSources().forEach(source -> entries.add(pointEntry(config, arena, "loot-" + source.id(), source.location().toLocation())));
        arena.objectives().forEach(objective -> entries.add(pointEntry(config, arena, "objective-" + objective.id(), objective.location().toLocation())));
        return new ArenaValidationReport(arena.id(), List.copyOf(entries));
    }

    private static ValidationEntry hubDistanceEntry(ZonefallConfig config, ArenaDefinition arena) {
        Location hub = config.hubSpawn().toLocation();
        if (!hub.getWorld().getName().equals(arena.worldName())) {
            return new ValidationEntry(ValidationStatus.PASS, "hub-exclusion", arena.center().toLocation(),
                    "hub is in a different world", "hubWorld=" + hub.getWorld().getName());
        }
        double distance = distanceFromPointToRegion2d(hub, arena.playableRegion());
        if (distance < config.hubExclusionRadius()) {
            return new ValidationEntry(ValidationStatus.FAIL, "hub-exclusion", arena.center().toLocation(),
                    "inside hub exclusion radius", "distance=" + Math.round(distance) + " required=" + config.hubExclusionRadius());
        }
        return new ValidationEntry(ValidationStatus.PASS, "hub-exclusion", arena.center().toLocation(),
                "outside hub exclusion radius", "distance=" + Math.round(distance));
    }

    private static double distanceFromPointToRegion2d(Location point, CuboidRegion region) {
        int x = point.getBlockX();
        int z = point.getBlockZ();
        int dx = Math.max(Math.max(region.minX() - x, 0), x - region.maxX());
        int dz = Math.max(Math.max(region.minZ() - z, 0), z - region.maxZ());
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static ValidationEntry pointEntry(ZonefallConfig config, ArenaDefinition arena, String label, Location location) {
        SafePlacementValidator.SafetyResult result = SafePlacementValidator.validate(location);
        if (!result.safe()) {
            return new ValidationEntry(ValidationStatus.FAIL, label, location, result.reason(), detail(config, arena, location));
        }
        if (isInsideHubRadius(config, arena, location)) {
            return new ValidationEntry(ValidationStatus.FAIL, label, location,
                    "inside hub exclusion radius", detail(config, arena, location));
        }
        ValidationStatus status = arena.playableRegion().contains(location)
                ? ValidationStatus.PASS
                : ValidationStatus.WARN;
        String reason = status == ValidationStatus.PASS ? "solid ground / clear space" : "safe but outside playable region";
        return new ValidationEntry(status, label, location, reason, detail(config, arena, location));
    }

    private static ValidationEntry regionEntry(String name, Location location, CuboidRegion region) {
        ValidationStatus status = region.width() >= 32 && region.depth() >= 32 ? ValidationStatus.PASS : ValidationStatus.WARN;
        return new ValidationEntry(status, name, location, "size " + region.describeSize(),
                "min=" + region.minX() + "," + region.minY() + "," + region.minZ()
                        + " max=" + region.maxX() + "," + region.maxY() + "," + region.maxZ());
    }

    private static ValidationEntry dimensionEntry(ArenaDefinition arena) {
        World world = Bukkit.getWorld(arena.worldName());
        if (world == null) {
            return new ValidationEntry(ValidationStatus.FAIL, "dimension", null, "world not loaded", "");
        }
        World.Environment environment = world.getEnvironment();
        String id = arena.id().toLowerCase(java.util.Locale.ROOT);
        boolean matches = (id.contains("hollow") && environment == World.Environment.NETHER)
                || (id.contains("glasswatch") && environment == World.Environment.THE_END)
                || (id.contains("yard") && environment == World.Environment.NORMAL)
                || (!id.contains("hollow") && !id.contains("glasswatch") && !id.contains("yard"));
        return new ValidationEntry(matches ? ValidationStatus.PASS : ValidationStatus.WARN,
                "dimension", arena.center().toLocation(),
                matches ? "matches arena theme" : "world environment may not match arena theme",
                "environment=" + environment);
    }

    private static String detail(ZonefallConfig config, ArenaDefinition arena, Location location) {
        return "under=" + location.getBlock().getRelative(0, -1, 0).getType()
                + " feet=" + location.getBlock().getType()
                + " head=" + location.getBlock().getRelative(0, 1, 0).getType()
                + " inPlayable=" + arena.playableRegion().contains(location)
                + " inProtected=" + arena.protectedRegion().contains(location)
                + " inHubRadius=" + isInsideHubRadius(config, arena, location);
    }

    private static boolean isInsideHubRadius(ZonefallConfig config, ArenaDefinition arena, Location location) {
        Location hub = config.hubSpawn().toLocation();
        if (!hub.getWorld().getName().equals(arena.worldName())) {
            return false;
        }
        return hub.distance(location) <= config.hubExclusionRadius();
    }
}
