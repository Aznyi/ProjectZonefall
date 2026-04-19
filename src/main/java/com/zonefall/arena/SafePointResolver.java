package com.zonefall.arena;

import org.bukkit.Location;

import java.util.Optional;

/**
 * Finds nearby player-safe locations for authored teleports and emergency arena snap-backs.
 */
public final class SafePointResolver {
    private static final int DEFAULT_SEARCH_RADIUS = 10;

    private SafePointResolver() {
    }

    public static Optional<Location> resolve(Location preferred, CuboidRegion playableRegion) {
        return resolve(preferred, playableRegion, preferred, DEFAULT_SEARCH_RADIUS);
    }

    public static Optional<Location> resolve(Location preferred, CuboidRegion playableRegion,
                                             Location inwardReference, int maxRadius) {
        if (isCandidateSafe(preferred, playableRegion)) {
            return Optional.of(preferred.clone());
        }

        Location inwardStep = stepToward(preferred, inwardReference, 2.0);
        if (isCandidateSafe(inwardStep, playableRegion)) {
            return Optional.of(inwardStep);
        }

        for (int radius = 1; radius <= maxRadius; radius++) {
            Optional<Location> directional = scanRing(inwardStep, playableRegion, inwardReference, radius);
            if (directional.isPresent()) {
                return directional;
            }
        }
        return Optional.empty();
    }

    public static boolean isCandidateSafe(Location location, CuboidRegion playableRegion) {
        return playableRegion.contains(location) && SafePlacementValidator.isSafe(location);
    }

    private static Optional<Location> scanRing(Location origin, CuboidRegion playableRegion,
                                               Location inwardReference, int radius) {
        int[][] offsets = {
                {0, 0},
                {radius, 0},
                {-radius, 0},
                {0, radius},
                {0, -radius},
                {radius, radius},
                {radius, -radius},
                {-radius, radius},
                {-radius, -radius}
        };
        for (int[] offset : offsets) {
            Location candidate = origin.clone().add(offset[0], 0, offset[1]);
            candidate = stepToward(candidate, inwardReference, Math.max(0, radius - 1));
            for (int yOffset = -2; yOffset <= 3; yOffset++) {
                Location adjusted = candidate.clone().add(0, yOffset, 0);
                if (isCandidateSafe(adjusted, playableRegion)) {
                    return Optional.of(adjusted);
                }
            }
        }
        return Optional.empty();
    }

    private static Location stepToward(Location from, Location to, double blocks) {
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return from.clone();
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            return from.clone();
        }
        Location result = from.clone();
        result.add((dx / length) * blocks, 0, (dz / length) * blocks);
        return result;
    }
}
