package com.zonefall;

import com.zonefall.arena.ArenaManager;
import com.zonefall.command.ZonefallCommand;
import com.zonefall.core.ZonefallConfig;
import com.zonefall.core.ZonefallServices;
import com.zonefall.crafting.NoopCraftingService;
import com.zonefall.extract.ExtractionListener;
import com.zonefall.match.MatchListener;
import com.zonefall.match.MatchManager;
import com.zonefall.profile.InMemoryProfileService;
import com.zonefall.stash.InMemoryStashService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin entry point for the Zonefall Phase 1 prototype.
 */
public final class ZonefallPlugin extends JavaPlugin {
    private MatchManager matchManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ZonefallConfig config = ZonefallConfig.from(getConfig());
        ZonefallServices services = new ZonefallServices(
                new InMemoryProfileService(),
                new InMemoryStashService(),
                new NoopCraftingService()
        );
        ArenaManager arenaManager = new ArenaManager(this, config);
        matchManager = new MatchManager(this, config, arenaManager, services);

        ZonefallCommand command = new ZonefallCommand(matchManager);
        PluginCommand pluginCommand = getCommand("zonefall");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new ExtractionListener(matchManager), this);
        getServer().getPluginManager().registerEvents(new MatchListener(matchManager), this);

        getLogger().info("Zonefall enabled. Phase 1 prototype ready.");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        getLogger().info("Zonefall disabled.");
    }
}

