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
    private final int shrinkStartDelaySeconds;
    private final int shrinkSeconds;
    private boolean shrinking;
    private double originalSize;
    private double originalCenterX;
    private double originalCenterZ;
    private boolean started;

    public ZoneController(Plugin plugin, ZonefallConfig config, Arena arena) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.borderStartSize = config.borderStartSize();
        this.borderEndSize = config.borderEndSize();
        this.shrinkStartDelaySeconds = 0;
        this.shrinkSeconds = config.matchDurationSeconds();
    }

    public ZoneController(Plugin plugin, ZonefallConfig config, Arena arena,
                          double borderStartSize, double borderEndSize, int shrinkStartDelaySeconds, int shrinkSeconds) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.borderStartSize = borderStartSize;
        this.borderEndSize = borderEndSize;
        this.shrinkStartDelaySeconds = Math.max(0, shrinkStartDelaySeconds);
        this.shrinkSeconds = shrinkSeconds;
    }

    public void start() {
        WorldBorder border = arena.world().getWorldBorder();
        originalSize = border.getSize();
        originalCenterX = border.getCenter().getX();
        originalCenterZ = border.getCenter().getZ();
        started = true;

        border.setCenter(arena.center().getX(), arena.center().getZ());
        border.setSize(borderStartSize);
        shrinking = false;
        plugin.getLogger().info("Zone border started at " + borderStartSize
                + " and will shrink to " + borderEndSize
                + " after " + shrinkStartDelaySeconds + "s over " + shrinkSeconds + "s.");
    }

    public void tick(int elapsedSeconds) {
        if (!shrinking && elapsedSeconds >= shrinkStartDelaySeconds) {
            arena.world().getWorldBorder().changeSize(borderEndSize, shrinkSeconds);
            shrinking = true;
            plugin.getLogger().info("Zone border shrink started for " + arena.id() + ".");
        }
        if (!config.debug() || elapsedSeconds % 60 != 0) {
            return;
        }
        plugin.getLogger().info("Zone border size now " + Math.round(arena.world().getWorldBorder().getSize()) + ".");
    }

    public void stop() {
        if (!config.restoreBorderOnEnd()) {
            return;
        }
        if (!started) {
            return;
        }
        WorldBorder border = arena.world().getWorldBorder();
        border.setCenter(originalCenterX, originalCenterZ);
        border.setSize(Math.max(1.0, originalSize));
        shrinking = false;
        started = false;
        plugin.getLogger().info("Zone border restored.");
    }

    public String debugSummary() {
        return "borderStart=" + borderStartSize
                + " borderEnd=" + borderEndSize
                + " shrinkDelay=" + shrinkStartDelaySeconds + "s"
                + " shrinkDuration=" + shrinkSeconds + "s"
                + " shrinking=" + shrinking;
    }
}
