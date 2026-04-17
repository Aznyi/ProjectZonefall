package com.zonefall.util;

import org.bukkit.ChatColor;

/**
 * Small chat formatting helper for prototype admin feedback.
 */
@SuppressWarnings("deprecation")
public final class Messages {
    private static final String PREFIX = ChatColor.DARK_GREEN + "[Zonefall] " + ChatColor.RESET;

    private Messages() {
    }

    public static String info(String message) {
        return PREFIX + ChatColor.GRAY + message;
    }

    public static String ok(String message) {
        return PREFIX + ChatColor.GREEN + message;
    }

    public static String error(String message) {
        return PREFIX + ChatColor.RED + message;
    }
}

