package com.viameowts.viastyle;

import me.drex.vanish.api.VanishAPI;
import me.drex.vanish.api.VanishEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct (non-reflection) integration with DrexHD/Vanish.
 *
 * <p>This class is intentionally kept separate from {@link VanishHelper} so that it
 * is only class-loaded by the JVM when the Vanish mod is actually present at
 * runtime.  {@link VanishHelper} checks {@code isModLoaded("melius-vanish")} before
 * ever touching this class, so a {@link ClassNotFoundException} / {@link NoClassDefFoundError}
 * cannot occur on servers without Vanish.</p>
 *
 * <p>After Fabric Loom remaps the Vanish dependency, all Mojang-mapped types
 * ({@code ServerPlayer}, {@code Entity}, …) in the Vanish API are converted to
 * their Yarn equivalents ({@code ServerPlayerEntity}, {@code Entity}, …),
 * making direct calls fully type-safe at compile time.</p>
 */
final class VanishCompat {

    private VanishCompat() {}

    /**
     * Local cache of vanished player UUIDs.
     *
     * <p>Updated synchronously (without {@code server.execute()}) inside {@code VANISH_EVENT}
     * so that {@link #countVisiblePlayers} always has the correct count by the time
     * {@code TabListManager.updateAll()} fires on the next tick.</p>
     */
    private static final Set<UUID> vanishedSet = ConcurrentHashMap.newKeySet();

    // ═══════════════════════════════════════════════════════════════════════
    //  Initialisation — call once from VanishHelper.init()
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Registers a listener on {@code VanishEvents.VANISH_EVENT} so that nametag
     * displays and tab-list counts are kept in sync whenever a player's vanish
     * state changes.
     */
    static void registerEvents() {
        // ── Vanish state change: nametag + tab refresh ─────────────────────
        VanishEvents.VANISH_EVENT.register((player, vanishing) -> {
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server == null) return;

            // Update local cache synchronously so countVisiblePlayers() already
            // returns the correct value when TabListManager reads it on the next tick.
            if (vanishing) vanishedSet.add(player.getUuid());
            else           vanishedSet.remove(player.getUuid());

            // Everything that touches entities / scoreboards must run on the
            // server thread (C2ME enforces this).
            server.execute(() -> {
                if (vanishing) {
                    // Player just became vanished — shouldHideNametag() now returns
                    // true for vanished players, so the tick loop will discard the
                    // entity on its own.  But we also call removePlayer() here for
                    // instant removal without waiting for the next tick cycle.
                    NametagManager.removePlayer(player, server);
                } else {
                    // Player just unvanished — restore the floating nametag.
                    NametagManager.updatePlayer(player);
                    NametagManager.updateAll(server);
                }
                // Refresh header/footer for every online player so {online} is correct.
                TabListManager.updateAll(server);
            });
        });

        // ── Sync vanishedSet on player join / disconnect ───────────────────
        // VANISH_EVENT fires only when state CHANGES — it does not fire on login
        // for players whose vanish state was persisted from a previous session.
        // So we also check VanishAPI.isVanished() after every join.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayerEntity joining = handler.player;
            // Determine initial vanish state on server thread (can't trust calling thread)
            srv.execute(() -> {
                if (VanishAPI.isVanished(joining)) {
                    vanishedSet.add(joining.getUuid());
                } else {
                    vanishedSet.remove(joining.getUuid());
                }
                TabListManager.updateAll(srv);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            // Remove immediately so the next onTick() sees the correct count.
            vanishedSet.remove(handler.player.getUuid());
        });

        // ── Replace Vanish's fake "left the game" message on /vanish ───────
        // Vanish calls VANISH_MESSAGE_EVENT.invoker().getVanishMessage(player)
        // and broadcasts WHATEVER we return via PlayerManager.broadcast().
        // So we return our styled leave message directly — Vanish will broadcast
        // it for us.  No separate broadcast call and no mixin needed.
        VanishEvents.VANISH_MESSAGE_EVENT.register(player -> {
            if (viaStyle.CONFIG != null) {
                String fmt = viaStyle.CONFIG.leaveFormat;
                if (fmt != null && !fmt.isBlank()) {
                    return viaStyleServer.buildJoinLeaveMessage(fmt, player);
                }
            }
            // If no leave format is configured, return empty — Vanish will
            // broadcast Text.empty() which our PlayerManagerBroadcastMixin
            // cancels so that no blank line appears.
            return net.minecraft.text.Text.empty();
        });

        // ── Replace Vanish's fake "joined the game" message on /unvanish ───
        VanishEvents.UN_VANISH_MESSAGE_EVENT.register(player -> {
            if (viaStyle.CONFIG != null) {
                String fmt = viaStyle.CONFIG.joinFormat;
                if (fmt != null && !fmt.isBlank()) {
                    return viaStyleServer.buildJoinLeaveMessage(fmt, player);
                }
            }
            return net.minecraft.text.Text.empty();
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  API wrappers (direct calls — no reflection)
    // ═══════════════════════════════════════════════════════════════════════

    static boolean isVanished(ServerPlayerEntity player) {
        return VanishAPI.isVanished(player);
    }

    /**
     * {@code canSeePlayer(actor, observer)} — "Can {@code observer} see {@code actor}?"
     *
     * <p>In the Vanish API the first argument is called <em>executive</em> (the player
     * who performed the action / whose action is observed) and the second is
     * <em>viewer</em> (the player who is watching).</p>
     */
    static boolean canSeePlayer(ServerPlayerEntity actor, ServerPlayerEntity observer) {
        return VanishAPI.canSeePlayer(actor, observer);
    }

    static int countVisiblePlayers(MinecraftServer server, ServerPlayerEntity viewer) {
        // Per-viewer-aware count:
        //  • If viewer is non-null  → include player p only if viewer can see them
        //    (VanishAPI.canSeePlayer(actor=p, observer=viewer) respects vanish permissions).
        //  • If viewer is null (server context, no player) → exclude all vanished players.
        int count = 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (viewer != null) {
                // Returns true if viewer can see p (admins see vanished, normal players don't).
                if (VanishAPI.canSeePlayer(p, viewer)) count++;
            } else {
                // No viewer context — conservative: hide all vanished players.
                if (!vanishedSet.contains(p.getUuid()) && !VanishAPI.isVanished(p)) count++;
            }
        }
        return count;
    }
}
