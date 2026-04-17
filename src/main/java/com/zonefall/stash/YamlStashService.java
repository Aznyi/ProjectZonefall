package com.zonefall.stash;

import com.zonefall.loot.LootBundle;
import com.zonefall.loot.LootType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Local YAML-backed stash store for development. TODO: replace with a repository backed by a database.
 */
public final class YamlStashService implements StashService {
    private final Plugin plugin;
    private final File file;
    private final Map<UUID, LootBundle> stashes = new ConcurrentHashMap<>();

    public YamlStashService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stashes.yml");
        load();
    }

    @Override
    public void deposit(UUID playerId, LootBundle loot) {
        if (loot.isEmpty()) {
            return;
        }
        stashes.computeIfAbsent(playerId, ignored -> LootBundle.empty()).addAll(loot);
        saveAll();
    }

    @Override
    public LootBundle getContents(UUID playerId) {
        return stashes.computeIfAbsent(playerId, ignored -> LootBundle.empty()).copy();
    }

    @Override
    public void saveAll() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for stash persistence.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, LootBundle> stashEntry : stashes.entrySet()) {
            String basePath = "players." + stashEntry.getKey();
            for (Map.Entry<LootType, Integer> lootEntry : stashEntry.getValue().asMap().entrySet()) {
                yaml.set(basePath + "." + lootEntry.getKey().name(), lootEntry.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save stashes.yml.", ex);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("players")) {
            return;
        }
        for (String playerKey : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(playerKey);
                LootBundle bundle = LootBundle.empty();
                String basePath = "players." + playerKey;
                for (LootType type : LootType.values()) {
                    bundle.add(type, yaml.getInt(basePath + "." + type.name(), 0));
                }
                stashes.put(playerId, bundle);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid stash UUID in stashes.yml: " + playerKey);
            }
        }
        plugin.getLogger().info("Loaded " + stashes.size() + " persistent player stashes.");
    }
}

