package com.zonefall.core;

import org.bukkit.configuration.file.FileConfiguration;

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
        int extractionHoldSeconds
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
                config.getInt("extraction.hold-seconds", 0)
        );
    }
}

