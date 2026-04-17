package com.zonefall.crafting;

import java.util.UUID;

/**
 * Phase 1 no-op crafting service. TODO: implement recipes and stash material costs.
 */
public final class NoopCraftingService implements CraftingService {
    @Override
    public boolean canCraftPrototypeItem(UUID playerId, String recipeId) {
        return false;
    }
}

