package com.zonefall.stash;

import com.zonefall.loot.LootBundle;

import java.util.UUID;

public interface StashService {
    void deposit(UUID playerId, LootBundle loot);

    LootBundle getContents(UUID playerId);

    void saveAll();
}
