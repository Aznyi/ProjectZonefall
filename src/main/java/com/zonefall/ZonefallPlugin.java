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
import com.zonefall.stash.StashService;
import com.zonefall.stash.YamlStashService;
import com.zonefall.ui.ArenaStatusUi;
import com.zonefall.ui.JoinPadLabelService;
import com.zonefall.ui.WorldMarkerLabelService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin entry point for the Zonefall Phase 1 prototype.
 */
public final class ZonefallPlugin extends JavaPlugin {
    private MatchManager matchManager;
    private ZonefallServices services;
    private ArenaStatusUi arenaStatusUi;
    private JoinPadLabelService joinPadLabelService;
    private WorldMarkerLabelService worldMarkerLabelService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ZonefallConfig config = ZonefallConfig.from(getConfig());
        StashService stashService = config.stashPersistenceEnabled()
                ? new YamlStashService(this)
                : new InMemoryStashService();
        services = new ZonefallServices(
                new InMemoryProfileService(),
                stashService,
                new NoopCraftingService()
        );
        ArenaManager arenaManager = new ArenaManager(this, config, services);
        matchManager = new MatchManager(arenaManager, services, config);
        arenaStatusUi = new ArenaStatusUi(this, arenaManager, config);
        joinPadLabelService = new JoinPadLabelService(this, arenaManager, config);
        worldMarkerLabelService = new WorldMarkerLabelService(this, arenaManager, config);

        ZonefallCommand command = new ZonefallCommand(matchManager);
        PluginCommand pluginCommand = getCommand("zonefall");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new ExtractionListener(matchManager), this);
        getServer().getPluginManager().registerEvents(new MatchListener(matchManager), this);
        arenaStatusUi.start();
        joinPadLabelService.start();
        worldMarkerLabelService.start();

        getLogger().info("Zonefall enabled. Phase 1 prototype ready.");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (arenaStatusUi != null) {
            arenaStatusUi.stop();
        }
        if (joinPadLabelService != null) {
            joinPadLabelService.stop();
        }
        if (worldMarkerLabelService != null) {
            worldMarkerLabelService.stop();
        }
        if (services != null) {
            services.stashService().saveAll();
        }
        getLogger().info("Zonefall disabled.");
    }
}
