package com.viameowts.viastyle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Reflection-based integration with <a href="https://github.com/gnembon/fabric-carpet">fabric-carpet</a>.
 *
 * <p>When Carpet's {@code /log tps} (or any HUD logger) is active for a player,
 * Carpet sends TPS/MSPT data via the tab header.  viaStyle normally
 * overwrites the tab header every tick, erasing Carpet's data.
 * This helper detects active Carpet HUD subscriptions so that
 * {@link TabListManager} can skip sending its own header/footer
 * to those players.</p>
 *
 * <h3>Checked reflection paths</h3>
 * <ul>
 *   <li>{@code carpet.logging.HUDController.player_huds} — map of players with active HUD</li>
 *   <li>{@code carpet.helpers.HudController.player_huds} — alternative location in newer Carpet</li>
 * </ul>
 */
public final class CarpetHelper {

    private static boolean available = false;

    /**
     * {@code HUDController.player_huds} — a {@code Map<PlayerEntity, List<Text>>}.
     * When a player has entries here, Carpet is actively rendering HUD data
     * in their tab header.
     */
    private static Field playerHudsField = null;

    /** Cached reference to the player_huds map (static field, resolved once). */
    private static volatile Map<?, ?> playerHudsMap = null;

    private CarpetHelper() {}

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("carpet")) {
            viaStyle.LOGGER.info("[viaStyle] Carpet not found => carpet integration disabled.");
            return;
        }

        // Try known HUDController locations
        String[] hudControllerClasses = {
                "carpet.logging.HUDController",
                "carpet.helpers.HudController",
                "carpet.logging.HudController"
        };

        for (String className : hudControllerClasses) {
            try {
                Class<?> cls = Class.forName(className);
                playerHudsField = cls.getField("player_huds");
                viaStyle.LOGGER.info("[viaStyle] Carpet HUDController found at {} => integration enabled.", className);
                available = true;

                // Pre-resolve the map reference (it's a static field)
                try {
                    Object obj = playerHudsField.get(null);
                    if (obj instanceof Map<?, ?> map) {
                        playerHudsMap = map;
                    }
                } catch (Throwable ignored) {}

                return;
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {}
        }

        // Carpet is loaded but HUDController not found — might be an API-only dependency
        viaStyle.LOGGER.info("[viaStyle] Carpet loaded but HUDController not resolved => HUD passthrough disabled.");
    }

    /** Returns {@code true} if Carpet is present and the HUD controller was resolved. */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Checks if a player currently has active Carpet HUD loggers
     * (e.g. {@code /log tps}, {@code /log packets}, etc.).
     *
     * <p>If Carpet is not installed or the check fails, returns {@code false}
     * (meaning viaStyle will send its normal header/footer).</p>
     */
    public static boolean hasActiveHud(ServerPlayerEntity player) {
        if (!available || player == null) return false;

        try {
            // Refresh the map reference if needed (in case it was assigned late)
            Map<?, ?> map = playerHudsMap;
            if (map == null && playerHudsField != null) {
                Object obj = playerHudsField.get(null);
                if (obj instanceof Map<?, ?> m) {
                    playerHudsMap = m;
                    map = m;
                }
            }
            if (map == null) return false;

            // player_huds uses PlayerEntity as key — check by identity AND by iterating
            // to handle potential wrapper/mapping differences.
            if (map.containsKey(player)) return true;

            // Some Carpet versions use the player's GameProfile name or UUID as key
            // Iterate if the map is small enough
            if (map.size() <= 100) {
                for (Object key : map.keySet()) {
                    if (key instanceof net.minecraft.entity.player.PlayerEntity pe) {
                        if (pe.getUuid().equals(player.getUuid())) return true;
                    }
                }
            }

            return false;
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] Carpet hasActiveHud check failed: {}", t.getMessage());
            return false;
        }
    }
}
