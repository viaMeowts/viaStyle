package com.viameowts.viastyle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Public façade for optional Vanish (DrexHD/Vanish, mod id {@code melius-vanish}) integration.
 *
 * <p>All heavy lifting and Vanish imports live in {@link VanishCompat}, which is only
 * class-loaded by the JVM when the mod is actually present.  This class never
 * references {@code VanishCompat} unless {@link #isAvailable()} is {@code true},
 * preventing {@link ClassNotFoundException} / {@link NoClassDefFoundError} on
 * servers without Vanish.</p>
 */
public final class VanishHelper {

    private static boolean available = false;

    private VanishHelper() {}

    // ═══════════════════════════════════════════════════════════════════════
    //  Initialisation
    // ═══════════════════════════════════════════════════════════════════════

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("melius-vanish")) {
            viaStyle.LOGGER.info("[viaStyle] Vanish (melius-vanish) not found => integration disabled.");
            return;
        }
        // Only reference VanishCompat once we know the mod is loaded.
        available = true;
        VanishCompat.registerEvents();
        viaStyle.LOGGER.info("[viaStyle] Vanish integration enabled (direct VanishAPI, no reflection).");
    }

    /** Returns {@code true} if Vanish is loaded and the integration is active. */
    public static boolean isAvailable() {
        return available;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  isVanished
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if the player is currently vanished.
     * Always returns {@code false} when Vanish is not installed.
     */
    public static boolean isVanished(ServerPlayerEntity player) {
        if (!available || player == null) return false;
        return VanishCompat.isVanished(player);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  canSeePlayer
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if {@code observer} can see {@code actor}.
     * Fails open (returns {@code true}) when Vanish is not installed.
     */
    public static boolean canSeePlayer(ServerPlayerEntity actor, ServerPlayerEntity observer) {
        if (!available) return true;
        if (actor == null || observer == null) return true;
        return VanishCompat.canSeePlayer(actor, observer);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of players visible to {@code viewer}.
     * Falls back to the raw player count when Vanish is not installed.
     */
    public static int countVisiblePlayers(MinecraftServer server, ServerPlayerEntity viewer) {
        if (server == null) return 0;
        if (!available) return server.getPlayerManager().getPlayerList().size();
        return VanishCompat.countVisiblePlayers(server, viewer);
    }
}
