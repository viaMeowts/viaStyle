package com.viameowts.viastyle;

import com.viameowts.viastyle.command.AfkCommand;
import com.viameowts.viastyle.command.ChatModeCommand;
import com.viameowts.viastyle.command.BroadcastCommand;
import com.viameowts.viastyle.command.IgnoreCommand;
import com.viameowts.viastyle.command.JoinLeaveCommand;
import com.viameowts.viastyle.command.NickColorCommand;
import com.viameowts.viastyle.command.PrivateMsgCommand;
import com.viameowts.viastyle.command.PlaceholderViewCommand;
import com.viameowts.viastyle.command.SocialSpyCommand;
import com.viameowts.viastyle.command.PmSoundCommand;
import com.viameowts.viastyle.command.ViaSuperCommand;
import com.viameowts.viastyle.network.Network;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Display;
import java.util.UUID;

public class viaStyleServer implements DedicatedServerModInitializer {
    /** Players whose PLAY_TIME was 0 on join, for the local first-join message. */
    private static final java.util.Set<UUID> FIRST_JOINERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onInitializeServer() {
        viaStyle.LOGGER.info("Initializing viaStyle Server!");
        ChatHandler.register();
        Network.init();

        // ── Init managers ──────────────────────────────────────────────────
        TabListManager.init();
        IgnoreManager.init();
        SocialSpyManager.init();
        JoinLeaveManager.load();

        // ── Commands ───────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register(ChatModeCommand::register);
        CommandRegistrationCallback.EVENT.register(ViaSuperCommand::register);
        CommandRegistrationCallback.EVENT.register(PrivateMsgCommand::register);
        CommandRegistrationCallback.EVENT.register(NickColorCommand::register);
        CommandRegistrationCallback.EVENT.register(IgnoreCommand::register);
        CommandRegistrationCallback.EVENT.register(SocialSpyCommand::register);
        CommandRegistrationCallback.EVENT.register(PlaceholderViewCommand::register);
        CommandRegistrationCallback.EVENT.register(BroadcastCommand::register);
        CommandRegistrationCallback.EVENT.register(JoinLeaveCommand::register);
        CommandRegistrationCallback.EVENT.register(AfkCommand::register);
        CommandRegistrationCallback.EVENT.register(PmSoundCommand::register);
        viaStyle.LOGGER.info("Registered viaStyle commands.");

        // ── Tick-based tab list + nametag updates ──────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TabListManager.onTick(server);
            NametagManager.onTick(server);
            AfkManager.tick(server);
        });

        // ── Player join / leave — apply nick colours to tab + nametag ──────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joinedPlayer = handler.getPlayer();
            AfkManager.initPlayer(joinedPlayer);
            NickColorManager.invalidate(joinedPlayer.getUUID());

            // Detect first join: PLAY_TIME stat is 0 if never played before
            if (joinedPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0) {
                FIRST_JOINERS.add(joinedPlayer.getUUID());
            }

            // Network mode: the proxy decides between network join and server switch.
            boolean networkAnnounces = Network.onJoin(joinedPlayer);

            // Delay to let LP data load and player to fully join.
            server.execute(() -> {
                TabListManager.updatePlayer(joinedPlayer);
                NametagManager.updatePlayer(joinedPlayer);
                TabListManager.updateAll(server);
                NametagManager.updateAll(server);

                if (!networkAnnounces) announceJoinLocally(server, joinedPlayer);
            });

            // Delayed re-apply (1 second later) for LP async load
            TickScheduler.schedule(20, () -> {
                if (joinedPlayer.hasDisconnected()) return;
                NickColorManager.invalidate(joinedPlayer.getUUID());
                TabListManager.updatePlayer(joinedPlayer);
                NametagManager.updatePlayer(joinedPlayer);
                TabListManager.updateAll(server);
                NametagManager.updateAll(server);
            });

            // Async LP user loading — triggers refresh as soon as LP data is ready
            LuckPermsHelper.loadUserAsync(joinedPlayer.getUUID(), () -> {
                server.execute(() -> {
                    if (joinedPlayer.hasDisconnected()) return;
                    NickColorManager.invalidate(joinedPlayer.getUUID());
                    TabListManager.updatePlayer(joinedPlayer);
                    NametagManager.updatePlayer(joinedPlayer);
                    TabListManager.updateAll(server);
                    NametagManager.updateAll(server);
                });
            });

            // Second delayed re-apply (3 seconds later) for Carpet bots
            // and other mods that assign LP groups asynchronously.
            TickScheduler.schedule(60, () -> {
                if (joinedPlayer.hasDisconnected()) return;
                NickColorManager.invalidate(joinedPlayer.getUUID());
                TabListManager.updatePlayer(joinedPlayer);
                NametagManager.updatePlayer(joinedPlayer);
                TabListManager.updateAll(server);
                NametagManager.updateAll(server);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer leavingPlayer = handler.getPlayer();
            UUID leavingUuid = leavingPlayer.getUUID();

            // Thread-safe map removals are fine immediately on any thread.
            NickColorManager.invalidate(leavingUuid);
            AfkManager.removePlayer(leavingUuid);
            viaStyle.playerChannel.remove(leavingUuid);
            FIRST_JOINERS.remove(leavingUuid);
            boolean networkAnnounces = Network.onLeave(leavingPlayer);
            PrivateMsgCommand.clearPlayer(leavingUuid);
            BroadcastCommand.clearPlayer(leavingUuid);

            // Defer entity/scoreboard operations to the server thread.
            server.execute(() -> {
                if (!networkAnnounces) {
                    String leaveFmt = JoinLeaveManager.resolveLeaveFormat(leavingUuid, viaStyle.CONFIG.leaveFormat);
                    Component leaveMsg = safeJoinLeaveMessage(leaveFmt, leavingPlayer, false);
                    broadcastJoinLeaveRespectVanish(server, leavingPlayer, leaveMsg);
                }
                NametagManager.removePlayer(leavingPlayer, server);
            });
        });

        // ── Hide TextDisplay nametag from the owner player ─────────────────
        // C2ME may invoke tracking callbacks off the server thread during
        // async chunk loading, so defer the packet send to be safe.
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof Display.TextDisplay
                    && trackedEntity.entityTags().contains("viastyle_nametag")) {
                java.util.UUID owner = NametagManager.getOwnerUuid(trackedEntity.getId());
                if (owner != null && player.getUUID().equals(owner)) {
                    net.minecraft.server.MinecraftServer srv = PlaceholderHelper.getServer();
                    if (srv != null) {
                        srv.execute(() -> {
                            if (!player.hasDisconnected()) {
                                player.connection.send(
                                        new ClientboundRemoveEntitiesPacket(trackedEntity.getId()));
                            }
                        });
                    }
                }
            }
        });

        // ── Subscribe to LP permission changes for live nick refresh ───────
        // SERVER_STARTED fires after all mods are loaded, so LP should be ready.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
                .SERVER_STARTED.register(server -> {
                    LuckPermsHelper.subscribeToEvents(server);
                    // One-time startup scan: discard any TextDisplay entities saved
                    // to disk from a previous server session (world save persists them).
                    NametagManager.cleanupOrphanedDisplays(server);
                });

        // ── Remove TextDisplay immediately on player death ─────────────────
        // When a player dies MC dismounts all passengers instantly, so the
        // TextDisplay floats at the death location until the next tick.
        // Discarding it here makes the nametag disappear at the same frame.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                NametagManager.onPlayerDeath(player);
            }
        });
    }

    /**
     * Builds a styled join/leave message from a format string.
        * Supports MiniMessage tags, #hex, and {name} placeholder
     * (replaced by the player's nick-coloured name).
     * Package-private so VanishCompat can use it for vanish/unvanish messages.
     */
    static Component buildJoinLeaveMessage(String format, ServerPlayer player) {
        if (format == null) {
            format = "{name}";
        }
        MutableComponent coloredName = NickColorManager.getColoredName(player);
        if (coloredName == null) {
            coloredName = Component.literal(player.getName().getString());
        }

        String normalized = format.replace("%name%", "{name}");
        String[] parts = normalized.split("\\{name}", -1);
        MutableComponent result = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(PlaceholderHelper.parseFormat(parts[i], player));
            }
            if (i < parts.length - 1) {
                result.append(coloredName.copy());
            }
        }
        return result;
    }

    /** Local (non-network) join announcement: first join is detected by the PLAY_TIME stat. */
    public static void announceJoinLocally(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        boolean firstJoin = FIRST_JOINERS.remove(player.getUUID());
        String fmt = firstJoin
                ? JoinLeaveManager.resolveFirstJoinFormat(player.getUUID(), viaStyle.CONFIG.firstJoinFormat)
                : JoinLeaveManager.resolveJoinFormat(player.getUUID(), viaStyle.CONFIG.joinFormat);
        broadcastJoinLeaveRespectVanish(server, player, safeJoinLeaveMessage(fmt, player, true));
    }

    /** Renders a join/leave-style format for {@code player}, with the built-in fallback when empty. */
    public static Component renderJoinLeave(String format, ServerPlayer player, boolean join) {
        return safeJoinLeaveMessage(format, player, join);
    }

    private static Component safeJoinLeaveMessage(String format, ServerPlayer player, boolean join) {
        String finalFormat = format;
        if (finalFormat == null || finalFormat.isBlank()) {
            finalFormat = join ? "<#98FB98>▸ <reset>{name}" : "<#FF9292>• <reset>{name}";
        }
        return buildJoinLeaveMessage(finalFormat, player);
    }

    private static void broadcastJoinLeaveRespectVanish(net.minecraft.server.MinecraftServer server,
                                                        ServerPlayer actor,
                                                        Component message) {
        if (server == null || actor == null || message == null || message.getString().isBlank()) {
            return;
        }

        UUID actorUuid = actor.getUUID();
        boolean actorVanished = VanishHelper.isVanished(actor);
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.getUUID().equals(actorUuid)) continue;
            if (actorVanished && !VanishHelper.canSeePlayer(actor, recipient)) continue;
            recipient.sendSystemMessage(message);
        }
    }


}
