package com.zonefall.profile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 profile store. TODO: replace with disk or database-backed persistence.
 */
public final class InMemoryProfileService implements ProfileService {
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public PlayerProfile loadProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, PlayerProfile::new);
    }
}

