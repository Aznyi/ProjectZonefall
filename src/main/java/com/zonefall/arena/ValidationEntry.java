package com.zonefall.arena;

import org.bukkit.Location;

/**
 * One line in an arena validation report.
 */
public record ValidationEntry(ValidationStatus status, String name, Location location, String reason, String detail) {
    public String format(boolean detailed) {
        String coords = location == null
                ? "unknown"
                : location.getWorld().getName() + " "
                + oneDecimal(location.getX()) + " "
                + oneDecimal(location.getY()) + " "
                + oneDecimal(location.getZ());
        return status + " " + name + " @ " + coords + " - " + reason
                + (detailed && detail != null && !detail.isBlank() ? " [" + detail + "]" : "");
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
