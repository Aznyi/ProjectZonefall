package com.zonefall.arena;

import com.zonefall.loot.source.LootSourceType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;

/**
 * Authored loot source placement loaded from arena config.
 */
public record ArenaLootSourcePlacement(String id, LootSourceType type, LocationSpec location, String tableKey, String tier) {
    public static ArenaLootSourcePlacement from(ConfigurationSection section, String fallbackWorld) {
        LootSourceType type = LootSourceType.valueOf(
                section.getString("type", "SUPPLY_CRATE").toUpperCase(Locale.ROOT)
        );
        return new ArenaLootSourcePlacement(
                section.getName(),
                type,
                LocationSpec.from(section.getConfigurationSection("location"), fallbackWorld),
                section.getString("table", type.configKey()),
                section.getString("tier", "standard")
        );
    }

    @SuppressWarnings("unchecked")
    public static ArenaLootSourcePlacement fromMap(Map<?, ?> values, String fallbackWorld) {
        Object rawType = values.get("type");
        LootSourceType type = LootSourceType.valueOf(
                (rawType == null ? "SUPPLY_CRATE" : rawType.toString()).toUpperCase(Locale.ROOT)
        );
        Object rawLocation = values.get("location");
        Map<?, ?> locationValues = rawLocation instanceof Map<?, ?> map ? map : Map.of();
        return new ArenaLootSourcePlacement(
                stringValue(values, "id", type.name().toLowerCase(Locale.ROOT)),
                type,
                fromLocationMap(locationValues, fallbackWorld),
                stringValue(values, "table", type.configKey()),
                stringValue(values, "tier", "standard")
        );
    }

    private static LocationSpec fromLocationMap(Map<?, ?> values, String fallbackWorld) {
        return new LocationSpec(
                stringValue(values, "world", fallbackWorld),
                readDouble(values, "x", 0.5),
                readDouble(values, "y", 80.0),
                readDouble(values, "z", 0.5),
                (float) readDouble(values, "yaw", 0.0),
                (float) readDouble(values, "pitch", 0.0)
        );
    }

    private static double readDouble(Map<?, ?> values, String key, double fallback) {
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

    private static String stringValue(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value.toString();
    }
}
