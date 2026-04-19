package com.zonefall.customblock;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;

public final class CustomLightBlockListener implements Listener {
    private final CustomLightBlockManager manager;

    public CustomLightBlockListener(CustomLightBlockManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block replaced = event.getBlockReplacedState().getBlock();
        if (manager.isSource(replaced)) {
            manager.removeSource(replaced);
        }
        manager.itemType(event.getItemInHand()).ifPresent(type -> manager.addSource(event.getBlockPlaced(), type));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        CustomLightBlockType type = manager.sourceType(event.getBlock());
        if (type == null) {
            return;
        }
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            manager.removeSource(event.getBlock());
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(),
                    manager.createItem(type, 1));
            return;
        }
        manager.removeSource(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (manager.isSource(event.getBlock())) {
            manager.removeSource(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeSources(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeSources(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(manager::isSource)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(manager::isSource)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.restoreChunk(event.getChunk());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        manager.saveAll();
    }

    private void removeSources(List<Block> blocks) {
        List<Block> sources = new ArrayList<>();
        for (Block block : blocks) {
            if (manager.isSource(block)) {
                sources.add(block);
            }
        }
        sources.forEach(manager::removeSource);
    }
}
