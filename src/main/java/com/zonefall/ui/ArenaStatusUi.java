package com.zonefall.ui;

import com.zonefall.arena.ArenaController;
import com.zonefall.arena.ArenaManager;
import com.zonefall.core.ZonefallConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lightweight player-facing arena status display.
 */
public final class ArenaStatusUi {
    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final ZonefallConfig config;
    private BukkitTask task;

    public ArenaStatusUi(Plugin plugin, ArenaManager arenaManager, ZonefallConfig config) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.config = config;
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (config.joinPadParticles()) {
            arenaManager.drawJoinPointMarkers();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<ArenaController> activeArena = arenaManager.findForPlayer(player.getUniqueId());
            String text = activeArena
                    .map(arena -> arena.playerStatusLine(player))
                    .orElseGet(this::hubStatusLine);
            player.sendActionBar(Component.text(text));
        }
    }

    private String hubStatusLine() {
        return arenaManager.arenas().stream()
                .map(arena -> arena.id() + ":" + arena.state() + " " + arena.activePlayerCount() + "p")
                .collect(Collectors.joining(" | "));
    }
}
