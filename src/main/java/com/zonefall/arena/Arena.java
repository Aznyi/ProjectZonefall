package com.zonefall.arena;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Describes where a match runs. Phase 1 uses the current world instead of cloned instances.
 */
public record Arena(String id, World world, Location center, Location extractionLocation) {
}

