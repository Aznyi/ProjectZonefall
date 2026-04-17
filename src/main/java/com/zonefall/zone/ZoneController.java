package com.zonefall.zone;

import com.zonefall.arena.Arena;
import com.zonefall.core.ZonefallConfig;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.Plugin;

/**
 * Applies match zone pressure through the vanilla world border.
 */
public final class ZoneController {
    private final Plugin plugin;
    private final ZonefallConfig config;
    private final Arena arena;
    private final double borderStartSize;
    private final double borderEndSize;
    private final int shrinkSeconds;
    private double originalSize;
    private double originalCenterX;
    private double originalCenterZ;

    public ZoneController(Plugin plugin, ZonefallConfig config, Arena arena) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.borderStartSize = config.borderStartSize();
        this.borderEndSize = config.borderEndSize();
        this.shrinkSeconds = config.matchDurationSeconds();
    }

    public ZoneController(Plugin plugin, ZonefallConfig config, Arena arena,
                          double borderStartSize, double borderEndSize, int shrinkSeconds) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.borderStartSize = borderStartSize;
        this.borderEndSize = borderEndSize;
        this.shrinkSeconds = shrinkSeconds;
    }

    public void start() {
        WorldBorder border = arena.world().getWorldBorder();
        originalSize = border.getSize();
        originalCenterX = border.getCenter().getX();
        originalCenterZ = border.getCenter().getZ();

        border.setCenter(arena.center().getX(), arena.center().getZ());
        border.setSize(borderStartSize);
        border.setSize(borderEndSize, shrinkSeconds);
        plugin.getLogger().info("Zone border started at " + borderStartSize
                + " and will shrink to " + borderEndSize + ".");
    }

    public void tick(int elapsedSeconds) {
        if (!config.debug() || elapsedSeconds % 60 != 0) {
            return;
        }
        plugin.getLogger().info("Zone border size now " + Math.round(arena.world().getWorldBorder().getSize()) + ".");
    }

    public void stop() {
        if (!config.restoreBorderOnEnd()) {
            return;
        }
        WorldBorder border = arena.world().getWorldBorder();
        border.setCenter(originalCenterX, originalCenterZ);
        border.setSize(originalSize);
        plugin.getLogger().info("Zone border restored.");
    }
}
