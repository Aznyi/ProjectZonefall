package com.zonefall.arena;

import com.zonefall.extract.ExtractionActivationMode;
import com.zonefall.extract.ExtractionRevealMode;
import com.zonefall.loot.source.LootActivationMode;
import com.zonefall.objective.ObjectiveActivationMode;
import com.zonefall.objective.ObjectiveDefinition;
import com.zonefall.pve.ThreatMobEntry;
import com.zonefall.pve.ThreatMobPools;
import com.zonefall.pve.ThreatPhase;
import org.bukkit.entity.EntityType;
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
        LocationSpec extraction,
        List<LocationSpec> extractions,
        ExtractionActivationMode extractionActivationMode,
        int activeExtractionCount,
        ExtractionRevealMode extractionRevealMode,
        int extractionRevealSecondsRemaining,
        CuboidRegion playableRegion,
        CuboidRegion protectedRegion,
        int countdownSeconds,
        int roundDurationSeconds,
        int joinWindowSeconds,
        boolean allowLateJoin,
        double borderStartSize,
        double borderEndSize,
        int shrinkStartDelaySeconds,
        int shrinkDurationSeconds,
        LootActivationMode lootActivationMode,
        int activeLootSourceCount,
        ObjectiveActivationMode objectiveActivationMode,
        int activeHighValueObjectiveCount,
        List<ArenaJoinPoint> joinPoints,
        List<ArenaLootSourcePlacement> lootSources,
        List<ObjectiveDefinition> objectives,
        List<ThreatMobEntry> hostileMobPool
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
                LocationSpec.from(section.getConfigurationSection("extraction"), world),
                readExtractions(section, world),
                readActivationMode(section),
                section.getInt("extraction-active-count", 1),
                readRevealMode(section),
                section.getInt("extraction-reveal-seconds-remaining", 60),
                CuboidRegion.from(section.getConfigurationSection("playable-region"), world, 64),
                CuboidRegion.from(section.isConfigurationSection("protected-region")
                        ? section.getConfigurationSection("protected-region")
                        : null, world, 80),
                section.getInt("countdown-seconds", 10),
                section.getInt("round-duration-seconds", 300),
                section.getInt("join-window-seconds", 45),
                section.getBoolean("allow-active-join-window", true),
                section.getDouble("border-start-size", 140.0),
                section.getDouble("border-end-size", 35.0),
                section.getInt("shrink-start-delay-seconds", 240),
                section.getInt("shrink-duration-seconds", Math.max(60, section.getInt("round-duration-seconds", 300) - 240)),
                readLootActivationMode(section),
                section.getInt("loot-active-count", 1),
                readObjectiveActivationMode(section),
                section.getInt("objective-active-count", 1),
                readJoinPoints(section, hubSpawn),
                readLootSources(section, world),
                readObjectives(section, world),
                readHostileMobPool(section, world)
        );
    }

    private static ExtractionActivationMode readActivationMode(ConfigurationSection section) {
        try {
            return ExtractionActivationMode.valueOf(section.getString("extraction-activation", "ALL_ACTIVE").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ExtractionActivationMode.ALL_ACTIVE;
        }
    }

    private static ExtractionRevealMode readRevealMode(ConfigurationSection section) {
        try {
            return ExtractionRevealMode.valueOf(section.getString("extraction-reveal-mode", "ROUND_START").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ExtractionRevealMode.ROUND_START;
        }
    }

    private static LootActivationMode readLootActivationMode(ConfigurationSection section) {
        try {
            return LootActivationMode.valueOf(section.getString("loot-activation", "ALL_ACTIVE").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LootActivationMode.ALL_ACTIVE;
        }
    }

    private static ObjectiveActivationMode readObjectiveActivationMode(ConfigurationSection section) {
        try {
            return ObjectiveActivationMode.valueOf(section.getString("objective-activation", "ALL_ACTIVE").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ObjectiveActivationMode.ALL_ACTIVE;
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

    private static List<ObjectiveDefinition> readObjectives(ConfigurationSection section, String world) {
        List<ObjectiveDefinition> objectives = new ArrayList<>();
        if (section.isList("objectives")) {
            for (java.util.Map<?, ?> objectiveValues : section.getMapList("objectives")) {
                objectives.add(ObjectiveDefinition.fromMap(objectiveValues, world));
            }
        }
        return List.copyOf(objectives);
    }

    private static List<ThreatMobEntry> readHostileMobPool(ConfigurationSection section, String world) {
        List<ThreatMobEntry> entries = new ArrayList<>();
        if (section.isList("hostile-mob-pool")) {
            for (java.util.Map<?, ?> values : section.getMapList("hostile-mob-pool")) {
                Object typeValue = values.get("type");
                if (typeValue == null) {
                    continue;
                }
                try {
                    EntityType type = EntityType.valueOf(typeValue.toString().toUpperCase(java.util.Locale.ROOT));
                    int weight = Math.max(1, intValue(values, "weight", 1));
                    ThreatPhase minimumPhase = ThreatPhase.fromConfig(stringValue(values, "minimum-phase", "EARLY"), ThreatPhase.EARLY);
                    entries.add(new ThreatMobEntry(type, weight, minimumPhase));
                } catch (IllegalArgumentException ignored) {
                    // Bad mob entries are ignored so a typo does not prevent local startup.
                }
            }
        }
        return entries.isEmpty() ? ThreatMobPools.forWorldName(world) : List.copyOf(entries);
    }

    private static int intValue(java.util.Map<?, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
