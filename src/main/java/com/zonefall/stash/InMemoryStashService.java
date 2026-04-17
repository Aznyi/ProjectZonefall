package com.zonefall.stash;

import com.zonefall.loot.LootBundle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placeholder stash service. TODO: replace with disk or database-backed persistence.
 */
public final class InMemoryStashService implements StashService {
    private final Map<UUID, LootBundle> stashes = new ConcurrentHashMap<>();

    @Override
    public void deposit(UUID playerId, LootBundle loot) {
        if (loot.isEmpty()) {
            return;
        }
        stashes.computeIfAbsent(playerId, ignored -> LootBundle.empty()).addAll(loot);
    }

    @Override
    public LootBundle getContents(UUID playerId) {
        return stashes.computeIfAbsent(playerId, ignored -> LootBundle.empty()).copy();
    }

    @Override
    public void saveAll() {
        // In-memory implementation has nothing to flush.
    }
}
