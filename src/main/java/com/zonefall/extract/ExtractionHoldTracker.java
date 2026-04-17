package com.zonefall.extract;

import com.zonefall.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks extraction commitment time while players remain inside an extraction zone.
 */
public final class ExtractionHoldTracker {
    private final Map<UUID, Integer> progressSeconds = new HashMap<>();

    public ExtractionHoldResult tick(Player player, boolean insideZone, int requiredSeconds) {
        UUID playerId = player.getUniqueId();
        if (!insideZone) {
            if (cancel(playerId)) {
                player.sendMessage(Messages.error("Extraction canceled. You left the zone."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            }
            return ExtractionHoldResult.idle();
        }

        if (requiredSeconds <= 0) {
            progressSeconds.remove(playerId);
            return ExtractionHoldResult.completed();
        }

        int progress = progressSeconds.merge(playerId, 1, Integer::sum);
        int remaining = Math.max(0, requiredSeconds - progress);
        player.sendActionBar(Component.text("EXTRACTING | " + remaining + "s remaining | Hold position"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, 1.2f);
        if (progress >= requiredSeconds) {
            progressSeconds.remove(playerId);
            player.sendMessage(Messages.ok("Extraction lock complete."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            return ExtractionHoldResult.completed();
        }
        return ExtractionHoldResult.inProgress(progress, requiredSeconds);
    }

    public boolean cancel(UUID playerId) {
        return progressSeconds.remove(playerId) != null;
    }

    public String describe(UUID playerId) {
        Integer progress = progressSeconds.get(playerId);
        return progress == null ? "none" : progress + "s";
    }

    public String describeForUi(UUID playerId) {
        if (playerId == null) {
            return "none";
        }
        Integer progress = progressSeconds.get(playerId);
        return progress == null ? "none" : progress + "s held";
    }
}
