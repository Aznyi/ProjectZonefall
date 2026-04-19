package com.zonefall.zone;

import com.zonefall.arena.Arena;
import com.zonefall.core.ZonefallConfig;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.Plugin;

/**
 * Applies match zone pressure through the vanilla world border.
 */
public final class ZoneController {
    public enum BarrierStatus {
        INSIDE,
        NEAR_EDGE,
        OUTSIDE
    }

    public enum BarrierAction {
        NONE,
        TELEPORT_PROTECTION,
        SOLID_WALL_PROTECTION,
        UNSAFE_FALLBACK_DEATH,
        INSTANT_DEATH
    }

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
    private double originalDamageAmount;
    private double originalDamageBuffer;
    private int originalWarningDistance;
    private int originalWarningTimeTicks;
    private boolean started;
    private BarrierAction lastAction = BarrierAction.NONE;
    private String lastActionDetail = "none";

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
        originalDamageAmount = border.getDamageAmount();
        originalDamageBuffer = border.getDamageBuffer();
        originalWarningDistance = border.getWarningDistance();
        originalWarningTimeTicks = border.getWarningTimeTicks();
        started = true;

        border.setCenter(arena.center().getX(), arena.center().getZ());
        border.setSize(borderStartSize);
        border.setDamageAmount(0.0);
        border.setDamageBuffer(0.0);
        border.setWarningDistance(0);
        border.setWarningTimeTicks(0);
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

    public BarrierStatus barrierStatus(Location location) {
        if (!started || location.getWorld() == null || !location.getWorld().equals(arena.world())) {
            return BarrierStatus.INSIDE;
        }
        double radius = currentRadius();
        double dx = Math.abs(location.getX() - arena.center().getX());
        double dz = Math.abs(location.getZ() - arena.center().getZ());
        double furthestAxis = Math.max(dx, dz);
        if (furthestAxis <= radius) {
            return BarrierStatus.INSIDE;
        }
        double distancePastEdge = furthestAxis - radius;
        if (distancePastEdge <= config.barrierProtectionDistance()) {
            return BarrierStatus.NEAR_EDGE;
        }
        return BarrierStatus.OUTSIDE;
    }

    public void recordAction(BarrierAction action, Location location) {
        recordAction(action, location, "");
    }

    public void recordAction(BarrierAction action, Location location, String note) {
        lastAction = action;
        lastActionDetail = action.name().toLowerCase(java.util.Locale.ROOT)
                + "@"
                + location.getWorld().getName()
                + " "
                + Math.round(location.getX() * 10.0) / 10.0
                + ","
                + Math.round(location.getY() * 10.0) / 10.0
                + ","
                + Math.round(location.getZ() * 10.0) / 10.0
                + (note == null || note.isBlank() ? "" : " " + note);
    }

    public Location safeInside(Location location) {
        double radius = Math.max(1.0, currentRadius());
        double margin = Math.max(1.0, config.barrierProtectionDistance() + 1.0);
        double minX = arena.center().getX() - radius + margin;
        double maxX = arena.center().getX() + radius - margin;
        double minZ = arena.center().getZ() - radius + margin;
        double maxZ = arena.center().getZ() + radius - margin;
        Location result = location.clone();
        result.setX(clamp(location.getX(), minX, maxX));
        result.setZ(clamp(location.getZ(), minZ, maxZ));
        return result;
    }

    public Location solidWallReturnLocation(Location from, Location attemptedTo) {
        Location result = from.clone();
        if (result.getWorld() == null || !result.getWorld().equals(arena.world())) {
            result = attemptedTo.clone();
        }
        double radius = Math.max(1.0, currentRadius());
        double epsilon = Math.max(0.05, Math.min(0.25, config.barrierProtectionDistance() * 0.1));
        double minX = arena.center().getX() - radius + epsilon;
        double maxX = arena.center().getX() + radius - epsilon;
        double minZ = arena.center().getZ() - radius + epsilon;
        double maxZ = arena.center().getZ() + radius - epsilon;
        result.setX(clamp(result.getX(), minX, maxX));
        result.setZ(clamp(result.getZ(), minZ, maxZ));
        result.setYaw(attemptedTo.getYaw());
        result.setPitch(attemptedTo.getPitch());
        return result;
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
        border.setDamageAmount(originalDamageAmount);
        border.setDamageBuffer(originalDamageBuffer);
        border.setWarningDistance(originalWarningDistance);
        border.setWarningTimeTicks(originalWarningTimeTicks);
        shrinking = false;
        started = false;
        plugin.getLogger().info("Zone border restored.");
    }

    public boolean isShrinking() {
        return shrinking;
    }

    public String debugSummary() {
        return "borderStart=" + borderStartSize
                + " borderEnd=" + borderEndSize
                + " shrinkDelay=" + shrinkStartDelaySeconds + "s"
                + " shrinkDuration=" + shrinkSeconds + "s"
                + " currentSize=" + Math.round(currentSize())
                + " currentRadius=" + Math.round(currentRadius())
                + " protectionDistance=" + config.barrierProtectionDistance()
                + " protectionBand=outside-only"
                + " stationaryBarrier=solid"
                + " instantDeath=" + config.barrierInstantDeath()
                + " vanillaDamage=disabled"
                + " lastAction=" + lastAction
                + " lastActionDetail=" + lastActionDetail
                + " shrinking=" + shrinking;
    }

    public double currentSize() {
        return started ? arena.world().getWorldBorder().getSize() : borderStartSize;
    }

    public double currentRadius() {
        return currentSize() / 2.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
