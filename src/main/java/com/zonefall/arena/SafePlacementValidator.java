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
            return SafetyResult.invalid("too close to world bottom/void");
        }
        if (world.getEnvironment() == World.Environment.THE_END && location.getY() < 45.0) {
            return SafetyResult.invalid("too low for End platform safety");
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        if (!feet.isPassable()) {
            return SafetyResult.invalid("feet block is not passable: " + feet.getType());
        }
        if (!head.isPassable()) {
            return SafetyResult.invalid("head block is not passable: " + head.getType());
        }
        if (!ground.getType().isSolid()) {
            return SafetyResult.invalid("ground is not solid: " + ground.getType());
        }
        if (isUnsafeLiquid(ground) || isUnsafeLiquid(feet) || isUnsafeLiquid(head)) {
            return SafetyResult.invalid("location touches unsafe liquid");
        }
        return SafetyResult.valid();
    }

    private static boolean isUnsafeLiquid(Block block) {
        Material type = block.getType();
        return block.isLiquid()
                || type == Material.LAVA
                || type == Material.WATER
                || type.name().contains("LAVA")
                || type.name().contains("WATER");
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
