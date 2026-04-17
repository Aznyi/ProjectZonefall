package com.zonefall.core;

import com.zonefall.crafting.CraftingService;
import com.zonefall.profile.ProfileService;
import com.zonefall.stash.StashService;

/**
 * Small service container for systems that will grow into persistent gameplay services later.
 */
public record ZonefallServices(
        ProfileService profileService,
        StashService stashService,
        CraftingService craftingService
) {
}

