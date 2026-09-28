package com.viameowts.viastyle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkManager {

    private static final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private static final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    private AfkManager() {}

    public static void initPlayer(ServerPlayer player) {
        lastActivity.put(player.getUUID(), System.currentTimeMillis());
    }

    /**
     * Detects player-initiated movement via the client's input state.
     * Checks movement keys (WASD), jumping, and sneaking — external forces
     * like water pushing or knockback do NOT count as activity.
     */
    private static boolean hasPlayerInput(ServerPlayer player) {
        Input input = player.getLastClientInput();
        return input.forward() || input.backward()
            || input.left() || input.right()
            || input.jump() || input.shift();
    }

    public static void onActivity(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
        if (afkPlayers.remove(uuid)) {
            ServerPlayer player = PlaceholderHelper.getServer() != null
                    ? PlaceholderHelper.getServer().getPlayerList().getPlayer(uuid) : null;
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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();

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
                server.getPlayerList().getPlayer(uuid) == null);
        afkPlayers.removeIf(uuid ->
                server.getPlayerList().getPlayer(uuid) == null);
    }

    private static boolean hasBypass(ServerPlayer player) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) return true;

        UUID uuid = player.getUUID();

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

    public static boolean toggleAfk(ServerPlayer player) {
        UUID uuid = player.getUUID();
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

    private static void applyVisualChanges(ServerPlayer player) {
        TabListManager.updatePlayer(player);
        NametagManager.updatePlayer(player);
    }

    private static void revertVisualChanges(ServerPlayer player) {
        TabListManager.updatePlayer(player);
        NametagManager.updatePlayer(player);
    }
}
