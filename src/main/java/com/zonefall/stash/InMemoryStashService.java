package com.zonefall.stash;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Placeholder stash service for extracted-match accounting.
 */
public final class InMemoryStashService implements StashService {
    private final Map<UUID, AtomicInteger> prototypeExtractions = new ConcurrentHashMap<>();

    @Override
    public void recordPrototypeExtraction(UUID playerId) {
        prototypeExtractions.computeIfAbsent(playerId, ignored -> new AtomicInteger()).incrementAndGet();
    }
}

