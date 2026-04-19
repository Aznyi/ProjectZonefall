package com.zonefall.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Shared safety checks for authored points and random arena spawns.
 */
public final class SafePlacementValidator {
    private SafePlacementValidator() {
    }

    public static SafetyResult validate(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return SafetyResult.invalid("world is not loaded");
        }
        if (location.getY() <= world.getMinHeight() + 2) {
            return SafetyResult.invalid("void-unsafe point: too close to world bottom");
        }
        if (world.getEnvironment() == World.Environment.THE_END && location.getY() < 45.0) {
            return SafetyResult.invalid("void-unsafe point: too low for End platform safety");
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        if (isUnsafeLiquid(ground) || isUnsafeLiquid(feet) || isUnsafeLiquid(head)) {
            return SafetyResult.invalid("lava-unsafe point: location touches liquid");
        }
        if (hasUnsafeLiquidNearby(feet) || hasUnsafeLiquidNearby(ground)) {
            return SafetyResult.invalid("lava-unsafe point: unsafe liquid adjacent");
        }
        if (!feet.isPassable()) {
            return SafetyResult.invalid("blocked-headroom point: feet block is not passable: " + feet.getType());
        }
        if (!head.isPassable()) {
            return SafetyResult.invalid("blocked-headroom point: head block is not passable: " + head.getType());
        }
        if (!ground.getType().isSolid()) {
            return SafetyResult.invalid("unsupported-surface point: ground is not solid: " + ground.getType());
        }
        return SafetyResult.valid();
    }

    public static boolean isSafe(Location location) {
        return validate(location).safe();
    }

    private static boolean isUnsafeLiquid(Block block) {
        Material type = block.getType();
        return block.isLiquid()
                || type == Material.LAVA
                || type == Material.WATER
                || type.name().contains("LAVA")
                || type.name().contains("WATER");
    }

    private static boolean hasUnsafeLiquidNearby(Block block) {
        return isUnsafeLiquid(block.getRelative(1, 0, 0))
                || isUnsafeLiquid(block.getRelative(-1, 0, 0))
                || isUnsafeLiquid(block.getRelative(0, 0, 1))
                || isUnsafeLiquid(block.getRelative(0, 0, -1))
                || isUnsafeLiquid(block.getRelative(0, -1, 0));
    }

    public record SafetyResult(boolean safe, String reason) {
        public static SafetyResult valid() {
            return new SafetyResult(true, "safe");
        }

        public static SafetyResult invalid(String reason) {
            return new SafetyResult(false, reason);
        }
    }
}
