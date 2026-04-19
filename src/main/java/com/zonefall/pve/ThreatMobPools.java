package com.zonefall.pve;

import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Sensible arena mob pool defaults by world/theme.
 */
public final class ThreatMobPools {
    private ThreatMobPools() {
    }

    public static List<ThreatMobEntry> forWorldName(String worldName) {
        String normalized = worldName.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("nether")) {
            return nether();
        }
        if (normalized.contains("end")) {
            return end();
        }
        return overworld();
    }

    public static List<ThreatMobEntry> overworld() {
        return List.of(
                new ThreatMobEntry(EntityType.ZOMBIE, 35, ThreatPhase.EARLY),
                new ThreatMobEntry(EntityType.SPIDER, 25, ThreatPhase.EARLY),
                new ThreatMobEntry(EntityType.SKELETON, 25, ThreatPhase.MID),
                new ThreatMobEntry(EntityType.CAVE_SPIDER, 15, ThreatPhase.MID),
                new ThreatMobEntry(EntityType.CREEPER, 18, ThreatPhase.MID),
                new ThreatMobEntry(EntityType.WITCH, 10, ThreatPhase.LATE)
        );
    }

    public static List<ThreatMobEntry> nether() {
        return List.of(
                new ThreatMobEntry(EntityType.ZOMBIFIED_PIGLIN, 35, ThreatPhase.EARLY),
                new ThreatMobEntry(EntityType.SKELETON, 20, ThreatPhase.EARLY),
                new ThreatMobEntry(EntityType.MAGMA_CUBE, 25, ThreatPhase.MID),
                new ThreatMobEntry(EntityType.BLAZE, 18, ThreatPhase.MID),
                new ThreatMobEntry(EntityType.WITHER_SKELETON, 12, ThreatPhase.LATE)
        );
    }

    public static List<ThreatMobEntry> end() {
        return List.of(
                new ThreatMobEntry(EntityType.ENDERMAN, 35, ThreatPhase.EARLY),
                new ThreatMobEntry(EntityType.ENDERMITE, 30, ThreatPhase.EARLY)
        );
    }
}
