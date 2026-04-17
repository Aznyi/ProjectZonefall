package com.zonefall.arena;

import com.zonefall.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Sends concise arena lifecycle announcements to hub/spectator players.
 */
public final class ArenaAnnouncementService {
    public void announce(ArenaController arena, String message) {
        String formatted = Messages.info("[" + arena.displayName() + "] " + message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!arena.isParticipant(player)) {
                player.sendMessage(formatted);
            }
        }
    }
}

