package com.viameowts.viastyle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PlayerInput;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkManager {

    private static final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private static final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    private AfkManager() {}

    public static void initPlayer(ServerPlayerEntity player) {
        lastActivity.put(player.getUuid(), System.currentTimeMillis());
    }

    /**
     * Detects player-initiated movement via the client's input state.
     * Checks movement keys (WASD), jumping, and sneaking — external forces
     * like water pushing or knockback do NOT count as activity.
     */
    private static boolean hasPlayerInput(ServerPlayerEntity player) {
        PlayerInput input = player.getPlayerInput();
        return input.forward() || input.backward()
            || input.left() || input.right()
            || input.jump() || input.sneak();
    }

    public static void onActivity(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
        if (afkPlayers.remove(uuid)) {
            ServerPlayerEntity player = PlaceholderHelper.getServer() != null
                    ? PlaceholderHelper.getServer().getPlayerManager().getPlayer(uuid) : null;
            if (player != null) {
                revertVisualChanges(player);
            }
        }
    }

    public static boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    public static void tick(MinecraftServer server) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.afkEnabled) return;

        long now = System.currentTimeMillis();
        int timeoutMs = cfg.afkTimeout * 1000;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();

            if (hasBypass(player)) continue;

            // Player-initiated movement clears AFK
            if (hasPlayerInput(player)) {
                onActivity(uuid);
            }

            Long last = lastActivity.get(uuid);
            if (last == null) {
                lastActivity.put(uuid, now);
                continue;
            }

            boolean currentlyAfk = afkPlayers.contains(uuid);
            boolean shouldBeAfk = (now - last) >= timeoutMs;

            if (shouldBeAfk && !currentlyAfk) {
                afkPlayers.add(uuid);
                applyVisualChanges(player);
            } else if (!shouldBeAfk && currentlyAfk) {
                afkPlayers.remove(uuid);
                revertVisualChanges(player);
            }
        }

        cleanupDisconnected(server);
    }

    private static void cleanupDisconnected(MinecraftServer server) {
        lastActivity.keySet().removeIf(uuid ->
                server.getPlayerManager().getPlayer(uuid) == null);
        afkPlayers.removeIf(uuid ->
                server.getPlayerManager().getPlayer(uuid) == null);
    }

    private static boolean hasBypass(ServerPlayerEntity player) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) return true;

        UUID uuid = player.getUuid();

        Boolean direct = LuckPermsHelper.getDirectPermission(uuid, cfg.afkBypassPermission);
        if (direct != null) return direct;
        if (LuckPermsHelper.hasPermissionDenied(uuid, cfg.afkBypassPermission)) return false;
        if (LuckPermsHelper.hasPermission(uuid, cfg.afkBypassPermission)) return true;

        String exempt = cfg.afkExemptPlayers;
        if (exempt != null && !exempt.isBlank()) {
            for (String s : exempt.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        if (UUID.fromString(trimmed).equals(uuid)) return true;
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        return false;
    }

    public static boolean toggleAfk(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (afkPlayers.contains(uuid)) {
            onActivity(uuid);
            return false;
        } else {
            afkPlayers.add(uuid);
            lastActivity.put(uuid, 0L);
            applyVisualChanges(player);
            return true;
        }
    }

    public static Set<UUID> getAfkPlayers() {
        return Collections.unmodifiableSet(afkPlayers);
    }

    public static void removePlayer(UUID uuid) {
        lastActivity.remove(uuid);
        afkPlayers.remove(uuid);
    }

    private static void applyVisualChanges(ServerPlayerEntity player) {
        TabListManager.updatePlayer(player);
        NametagManager.updatePlayer(player);
    }

    private static void revertVisualChanges(ServerPlayerEntity player) {
        TabListManager.updatePlayer(player);
        NametagManager.updatePlayer(player);
    }
}
