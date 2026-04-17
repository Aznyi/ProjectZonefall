package com.zonefall.profile;

import java.util.UUID;

public interface ProfileService {
    PlayerProfile loadProfile(UUID playerId);
}

