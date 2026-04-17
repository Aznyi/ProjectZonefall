package com.zonefall.core;

import com.zonefall.arena.ArenaDefinition;
import com.zonefall.arena.LocationSpec;
import com.zonefall.loot.LootBundle;
import com.zonefall.loot.LootType;
import com.zonefall.loot.source.LootSourceType;
import com.zonefall.loot.table.WeightedLootEntry;
import com.zonefall.loot.table.WeightedLootTable;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable runtime view of config.yml values used by the Phase 1 systems.
 */
public record ZonefallConfig(
        boolean debug,
        int countdownSeconds,
        int matchDurationSeconds,
        boolean restoreBorderOnEnd,
        double borderStartSize,
        double borderEndSize,
        int mobSpawnIntervalSeconds,
        int mobsPerPlayer,
        double spawnMinDistance,
        double spawnMaxDistance,
        double extractionRadius,
        double extractionVerticalTolerance,
        int extractionHoldSeconds,
        LootBundle mobKillReward,
        double deathDropPickupRadius,
        boolean stashPersistenceEnabled,
        Map<LootSourceType, WeightedLootTable> lootTables,
        LocationSpec hubSpawn,
        List<ArenaDefinition> arenas,
        int finalExtractionSeconds,
        boolean joinPadParticles,
        boolean spectatorFlightEnabled,
        boolean spectatorPreventDamage,
        boolean spectatorPreventHunger,
        boolean joinPadLabelsEnabled,
        double joinPadLabelHeight
) {
    public static ZonefallConfig from(FileConfiguration config) {
        return new ZonefallConfig(
                config.getBoolean("debug", true),
                config.getInt("match.countdown-seconds", 10),
                config.getInt("match.duration-seconds", 600),
                config.getBoolean("match.restore-border-on-end", true),
                config.getDouble("zone.border-start-size", 500.0),
                config.getDouble("zone.border-end-size", 75.0),
                config.getInt("pve.mob-spawn-interval-seconds", 20),
                config.getInt("pve.mobs-per-player", 2),
                config.getDouble("pve.spawn-min-distance", 12.0),
                config.getDouble("pve.spawn-max-distance", 24.0),
                config.getDouble("extraction.radius", 5.0),
                config.getDouble("extraction.vertical-tolerance", 4.0),
                config.getInt("extraction.hold-seconds", 0),
                readLootBundle(config, "loot.mob-kill-reward"),
                config.getDouble("loot.death-drop-pickup-radius", 2.5),
                config.getBoolean("stash.persistence-enabled", true),
                readLootTables(config),
                LocationSpec.from(config.getConfigurationSection("hub.spawn"), "world"),
                readArenas(config),
                config.getInt("arena-defaults.final-extraction-seconds", 30),
                config.getBoolean("ui.join-pad-particles", true),
                config.getBoolean("spectator.allow-flight", true),
                config.getBoolean("spectator.prevent-damage", true),
                config.getBoolean("spectator.prevent-hunger", true),
                config.getBoolean("ui.join-pad-labels.enabled", true),
                config.getDouble("ui.join-pad-labels.height", 2.2)
        );
    }

    private static LootBundle readLootBundle(FileConfiguration config, String path) {
        LootBundle bundle = LootBundle.empty();
        if (!config.isConfigurationSection(path)) {
            bundle.add(LootType.SCRAP, 1);
            return bundle;
        }
        for (String key : config.getConfigurationSection(path).getKeys(false)) {
            try {
                LootType type = LootType.valueOf(key.toUpperCase(Locale.ROOT));
                bundle.add(type, config.getInt(path + "." + key, 0));
            } catch (IllegalArgumentException ignored) {
                // Unknown config keys are ignored so future loot types do not break startup.
            }
        }
        return bundle;
    }

    private static Map<LootSourceType, WeightedLootTable> readLootTables(FileConfiguration config) {
        Map<LootSourceType, WeightedLootTable> tables = new EnumMap<>(LootSourceType.class);
        for (LootSourceType sourceType : LootSourceType.values()) {
            String path = "loot.tables." + sourceType.configKey();
            int rolls = config.getInt(path + ".rolls", 2);
            List<WeightedLootEntry> entries = new ArrayList<>();
            if (config.isList(path + ".entries")) {
                for (Map<?, ?> rawEntry : config.getMapList(path + ".entries")) {
                    Object typeValue = rawEntry.get("type");
                    if (typeValue == null) {
                        continue;
                    }
                    try {
                        LootType type = LootType.valueOf(typeValue.toString().toUpperCase(Locale.ROOT));
                        int min = readInt(rawEntry, "min", 1);
                        int max = readInt(rawEntry, "max", min);
                        int weight = readInt(rawEntry, "weight", 1);
                        entries.add(new WeightedLootEntry(type, min, max, weight));
                    } catch (IllegalArgumentException ignored) {
                        // Bad entries are ignored so local config mistakes do not prevent startup.
                    }
                }
            }
            tables.put(sourceType, entries.isEmpty()
                    ? WeightedLootTable.fallback()
                    : new WeightedLootTable(entries, rolls));
        }
        return Map.copyOf(tables);
    }

    private static int readInt(Map<?, ?> values, String key, int fallback) {
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

    private static List<ArenaDefinition> readArenas(FileConfiguration config) {
        LocationSpec hubSpawn = LocationSpec.from(config.getConfigurationSection("hub.spawn"), "world");
        List<ArenaDefinition> definitions = new ArrayList<>();
        if (config.isConfigurationSection("arenas")) {
            for (String id : config.getConfigurationSection("arenas").getKeys(false)) {
                definitions.add(ArenaDefinition.from(id, config.getConfigurationSection("arenas." + id), hubSpawn));
            }
        }
        if (!definitions.isEmpty()) {
            return List.copyOf(definitions);
        }
        return List.of(
                fallbackArena("arena-1", "Ruined Yard", hubSpawn, 0),
                fallbackArena("arena-2", "Iron Hollow", hubSpawn, 160),
                fallbackArena("arena-3", "Glasswatch", hubSpawn, -160)
        );
    }

    private static ArenaDefinition fallbackArena(String id, String name, LocationSpec hubSpawn, int offsetX) {
        String world = hubSpawn.worldName();
        LocationSpec center = new LocationSpec(world, offsetX + 0.5, hubSpawn.y(), 100.5, 0, 0);
        return new ArenaDefinition(
                id,
                name,
                world,
                center,
                new LocationSpec(world, offsetX + 0.5, hubSpawn.y(), 132.5, 180, 0),
                hubSpawn,
                new LocationSpec(world, offsetX + 0.5, hubSpawn.y() + 8, 175.5, 180, 20),
                new LocationSpec(world, offsetX + 20.5, hubSpawn.y(), 100.5, 90, 0),
                List.of(new LocationSpec(world, offsetX + 20.5, hubSpawn.y(), 100.5, 90, 0)),
                com.zonefall.extract.ExtractionActivationMode.ALL_ACTIVE,
                1,
                new com.zonefall.arena.CuboidRegion(world, offsetX - 60, 0, 40, offsetX + 60, 320, 160),
                new com.zonefall.arena.CuboidRegion(world, offsetX - 75, 0, 25, offsetX + 75, 320, 175),
                10,
                300,
                45,
                true,
                140.0,
                35.0,
                List.of(new com.zonefall.arena.ArenaJoinPoint(
                        id + "-join",
                        new LocationSpec(world, offsetX + 0.5, hubSpawn.y(), hubSpawn.z() + 6.0, 0, 0),
                        1.5
                )),
                List.of()
        );
    }
}
