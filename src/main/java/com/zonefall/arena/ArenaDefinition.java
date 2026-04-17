package com.zonefall.arena;

import com.zonefall.extract.ExtractionActivationMode;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Config-defined prebuilt extraction arena.
 */
public record ArenaDefinition(
        String id,
        String displayName,
        String worldName,
        LocationSpec center,
        LocationSpec joinSpawn,
        LocationSpec hubReturn,
        LocationSpec spectatorPoint,
        LocationSpec extraction,
        List<LocationSpec> extractions,
        ExtractionActivationMode extractionActivationMode,
        int activeExtractionCount,
        CuboidRegion playableRegion,
        CuboidRegion spectatorRegion,
        int countdownSeconds,
        int roundDurationSeconds,
        int joinWindowSeconds,
        boolean allowLateJoin,
        double borderStartSize,
        double borderEndSize,
        List<ArenaJoinPoint> joinPoints,
        List<ArenaLootSourcePlacement> lootSources
) {
    public static ArenaDefinition from(String id, ConfigurationSection section, LocationSpec hubSpawn) {
        String world = section.getString("world", hubSpawn.worldName());
        return new ArenaDefinition(
                id,
                section.getString("name", id),
                world,
                LocationSpec.from(section.getConfigurationSection("center"), world),
                LocationSpec.from(section.getConfigurationSection("join-spawn"), world),
                section.isConfigurationSection("hub-return")
                        ? LocationSpec.from(section.getConfigurationSection("hub-return"), hubSpawn.worldName())
                        : hubSpawn,
                section.isConfigurationSection("spectator-point")
                        ? LocationSpec.from(section.getConfigurationSection("spectator-point"), world)
                        : LocationSpec.from(section.getConfigurationSection("join-spawn"), world),
                LocationSpec.from(section.getConfigurationSection("extraction"), world),
                readExtractions(section, world),
                readActivationMode(section),
                section.getInt("extraction-active-count", 1),
                CuboidRegion.from(section.getConfigurationSection("playable-region"), world, 64),
                CuboidRegion.from(section.getConfigurationSection("spectator-region"), world, 80),
                section.getInt("countdown-seconds", 10),
                section.getInt("round-duration-seconds", 300),
                section.getInt("join-window-seconds", 45),
                section.getBoolean("allow-active-join-window", true),
                section.getDouble("border-start-size", 140.0),
                section.getDouble("border-end-size", 35.0),
                readJoinPoints(section, hubSpawn),
                readLootSources(section, world)
        );
    }

    private static ExtractionActivationMode readActivationMode(ConfigurationSection section) {
        try {
            return ExtractionActivationMode.valueOf(section.getString("extraction-activation", "ALL_ACTIVE").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ExtractionActivationMode.ALL_ACTIVE;
        }
    }

    private static List<LocationSpec> readExtractions(ConfigurationSection section, String world) {
        List<LocationSpec> locations = new ArrayList<>();
        if (section.isList("extractions")) {
            for (java.util.Map<?, ?> values : section.getMapList("extractions")) {
                locations.add(locationFromMap(values, world));
            }
        }
        if (locations.isEmpty()) {
            locations.add(LocationSpec.from(section.getConfigurationSection("extraction"), world));
        }
        return List.copyOf(locations);
    }

    private static LocationSpec locationFromMap(java.util.Map<?, ?> values, String fallbackWorld) {
        return new LocationSpec(
                stringValue(values, "world", fallbackWorld),
                doubleValue(values, "x", 0.5),
                doubleValue(values, "y", 80.0),
                doubleValue(values, "z", 0.5),
                (float) doubleValue(values, "yaw", 0.0),
                (float) doubleValue(values, "pitch", 0.0)
        );
    }

    private static String stringValue(java.util.Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value.toString();
    }

    private static double doubleValue(java.util.Map<?, ?> values, String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<ArenaJoinPoint> readJoinPoints(ConfigurationSection section, LocationSpec hubSpawn) {
        List<ArenaJoinPoint> points = new ArrayList<>();
        if (section.isConfigurationSection("join-points")) {
            for (String id : section.getConfigurationSection("join-points").getKeys(false)) {
                points.add(ArenaJoinPoint.from(id, section.getConfigurationSection("join-points." + id), hubSpawn));
            }
        }
        return List.copyOf(points);
    }

    private static List<ArenaLootSourcePlacement> readLootSources(ConfigurationSection section, String world) {
        List<ArenaLootSourcePlacement> placements = new ArrayList<>();
        if (section.isList("loot-sources")) {
            for (java.util.Map<?, ?> sourceValues : section.getMapList("loot-sources")) {
                placements.add(ArenaLootSourcePlacement.fromMap(sourceValues, world));
            }
        }
        return List.copyOf(placements);
    }
}
