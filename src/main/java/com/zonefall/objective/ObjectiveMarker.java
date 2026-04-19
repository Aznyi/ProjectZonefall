package com.zonefall.objective;

import org.bukkit.Location;

/**
 * Read-only world label data for an active objective.
 */
public record ObjectiveMarker(String id, Location location, String label) {
}
