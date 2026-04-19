package com.zonefall.objective;

import com.zonefall.arena.LocationSpec;
import com.zonefall.loot.LootBundle;
import com.zonefall.loot.LootType;
import org.bukkit.Material;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Config-authored objective metadata for a single arena.
 */
public record ObjectiveDefinition(
        String id,
        String displayName,
        ObjectiveType type,
        boolean enabled,
        LocationSpec location,
        Material markerMaterial,
        ObjectiveRevealMode revealMode,
        int revealSecondsRemaining,
        String rewardTable,
        LootBundle rewardBundle,
        int progressTarget,
        double extractBonusMultiplier,
        List<ObjectiveCompletionEffect> effects
) {
    public static ObjectiveDefinition fromMap(Map<?, ?> values, String fallbackWorld) {
        String id = stringValue(values, "id", "objective");
        String displayName = stringValue(values, "display-name", id);
        ObjectiveType type = enumValue(ObjectiveType.class, values, "type", ObjectiveType.HIGH_VALUE);
        boolean enabled = booleanValue(values, "enabled", true);
        LocationSpec location = locationValue(values.get("location"), fallbackWorld);
        Material markerMaterial = materialValue(values, "marker-material", Material.LIGHTNING_ROD);
        ObjectiveRevealMode revealMode = enumValue(ObjectiveRevealMode.class, values, "reveal", ObjectiveRevealMode.ROUND_START);
        int revealSeconds = intValue(values, "reveal-seconds-remaining", 60);
        String rewardTable = stringValue(values, "reward-table", "");
        LootBundle rewardBundle = readRewardBundle(values.get("reward"));
        int progressTarget = Math.max(1, intValue(values, "progress-target", 3));
        double extractBonusMultiplier = Math.max(1.0, doubleValue(values, "extract-bonus-multiplier", 1.5));
        List<ObjectiveCompletionEffect> effects = readEffects(values.get("effects"));
        return new ObjectiveDefinition(
                id,
                displayName,
                type,
                enabled,
                location,
                markerMaterial,
                revealMode,
                revealSeconds,
                rewardTable,
                rewardBundle,
                progressTarget,
                extractBonusMultiplier,
                effects
        );
    }

    private static List<ObjectiveCompletionEffect> readEffects(Object rawEffects) {
        List<ObjectiveCompletionEffect> effects = new ArrayList<>();
        if (rawEffects instanceof List<?> list) {
            for (Object rawEffect : list) {
                parseEffect(rawEffect).ifPresent(effects::add);
            }
        } else {
            parseEffect(rawEffects).ifPresent(effects::add);
        }
        return List.copyOf(effects);
    }

    private static java.util.Optional<ObjectiveCompletionEffect> parseEffect(Object rawEffect) {
        if (rawEffect == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(ObjectiveCompletionEffect.valueOf(rawEffect.toString().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private static LootBundle readRewardBundle(Object rawReward) {
        LootBundle bundle = LootBundle.empty();
        if (!(rawReward instanceof Map<?, ?> values)) {
            return bundle;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            try {
                LootType type = LootType.valueOf(entry.getKey().toString().toUpperCase(Locale.ROOT));
                bundle.add(type, intValue(entry.getValue(), 0));
            } catch (IllegalArgumentException ignored) {
                // Unknown reward keys are ignored for local config resilience.
            }
        }
        return bundle;
    }

    private static LocationSpec locationValue(Object rawLocation, String fallbackWorld) {
        if (!(rawLocation instanceof Map<?, ?> values)) {
            return new LocationSpec(fallbackWorld, 0.5, 80.0, 0.5, 0, 0);
        }
        return new LocationSpec(
                stringValue(values, "world", fallbackWorld),
                doubleValue(values, "x", 0.5),
                doubleValue(values, "y", 80.0),
                doubleValue(values, "z", 0.5),
                (float) doubleValue(values, "yaw", 0.0),
                (float) doubleValue(values, "pitch", 0.0)
        );
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Map<?, ?> values, String key, T fallback) {
        try {
            return Enum.valueOf(type, stringValue(values, key, fallback.name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static Material materialValue(Map<?, ?> values, String key, Material fallback) {
        try {
            return Material.valueOf(stringValue(values, key, fallback.name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static boolean booleanValue(Map<?, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    private static int intValue(Map<?, ?> values, String key, int fallback) {
        return intValue(values.get(key), fallback);
    }

    private static int intValue(Object value, int fallback) {
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

    private static double doubleValue(Map<?, ?> values, String key, double fallback) {
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
