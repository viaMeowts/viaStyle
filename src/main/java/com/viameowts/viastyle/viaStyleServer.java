package com.viameowts.viastyle;

import com.viameowts.viastyle.command.ChatModeCommand;
import com.viameowts.viastyle.command.IgnoreCommand;
import com.viameowts.viastyle.command.NickColorCommand;
import com.viameowts.viastyle.command.PrivateMsgCommand;
import com.viameowts.viastyle.command.SocialSpyCommand;
import com.viameowts.viastyle.command.ViaSuperCommand;
import com.viameowts.viapanel.api.ViaPanelApi;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.UUID;

public class viaStyleServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        viaStyle.LOGGER.info("Initializing viaStyle Server!");
        ChatHandler.register();

        // ── Init managers ──────────────────────────────────────────────────
        TabListManager.init();
        IgnoreManager.init();
        SocialSpyManager.init();

        // ── Commands ───────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register(ChatModeCommand::register);
        CommandRegistrationCallback.EVENT.register(ViaSuperCommand::register);
        CommandRegistrationCallback.EVENT.register(PrivateMsgCommand::register);
        CommandRegistrationCallback.EVENT.register(NickColorCommand::register);
        CommandRegistrationCallback.EVENT.register(IgnoreCommand::register);
        CommandRegistrationCallback.EVENT.register(SocialSpyCommand::register);
        ViaPanelApi.register(new ViaStylePanelProvider());
        viaStyle.LOGGER.info("Registered viaStyle commands.");

        // ── Tick-based tab list + nametag updates ──────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TabListManager.onTick(server);
            NametagManager.onTick(server);
        });

        // ── Player join / leave — apply nick colours to tab + nametag ──────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joinedPlayer = handler.getPlayer();
            NickColorManager.invalidate(joinedPlayer.getUuid());

            // Detect first join: PLAY_TIME stat is 0 if never played before
            boolean firstJoin = joinedPlayer.getStatHandler()
                    .getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME)) == 0;

            // Delay to let LP data load and player to fully join.
            server.execute(() -> {
                TabListManager.updatePlayer(joinedPlayer);
                NametagManager.updatePlayer(joinedPlayer);
                TabListManager.updateAll(server);
                NametagManager.updateAll(server);

                String fmt = firstJoin
                        ? viaStyle.CONFIG.firstJoinFormat
                        : viaStyle.CONFIG.joinFormat;
                Text msg = safeJoinLeaveMessage(fmt, joinedPlayer, true);
                broadcastJoinLeaveRespectVanish(server, joinedPlayer, msg);
            });

            // Delayed re-apply (1 second later) for LP async load
            TickScheduler.schedule(20, () -> {
                if (joinedPlayer.isDisconnected()) return;
                NickColorManager.invalidate(joinedPlayer.getUuid());
                TabListManager.updatePlayer(joinedPlayer);
                NametagManager.updatePlayer(joinedPlayer);
                TabListManager.updateAll(server);
                NametagManager.updateAll(server);
            });

            // Async LP user loading — triggers refresh as soon as LP data is ready
            LuckPermsHelper.loadUserAsync(joinedPlayer.getUuid(), () -> {
                server.execute(() -> {
                    if (joinedPlayer.isDisconnected()) return;
                    NickColorManager.invalidate(joinedPlayer.getUuid());
                    TabListManager.updatePlayer(joinedPlayer);
                    NametagManager.updatePlayer(joinedPlayer);
                    TabListManager.updateAll(server);
                    NametagManager.updateAll(server);
                });
            });

            // Second delayed re-apply (3 seconds later) for Carpet bots
            // and other mods that assign LP groups asynchronously.
            TickScheduler.schedule(60, () -> {
                if (joinedPlayer.isDisconnected()) return;
                NickColorManager.invalidate(joinedPlayer.getUuid());
                TabListManager.updatePlayer(joinedPlayer);
                NametagManager.updatePlayer(joinedPlayer);
                TabListManager.updateAll(server);
                NametagManager.updateAll(server);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity leavingPlayer = handler.getPlayer();
            UUID leavingUuid = leavingPlayer.getUuid();

            // Thread-safe map removals are fine immediately on any thread.
            NickColorManager.invalidate(leavingUuid);
            viaStyle.playerChatModePref.remove(leavingUuid);
            PrivateMsgCommand.clearPlayer(leavingUuid);

            // Defer entity/scoreboard operations to the server thread.
            server.execute(() -> {
                Text leaveMsg = safeJoinLeaveMessage(viaStyle.CONFIG.leaveFormat, leavingPlayer, false);
                broadcastJoinLeaveRespectVanish(server, leavingPlayer, leaveMsg);
                NametagManager.removePlayer(leavingPlayer, server);
            });
        });

        // ── Hide TextDisplay nametag from the owner player ─────────────────
        // C2ME may invoke tracking callbacks off the server thread during
        // async chunk loading, so defer the packet send to be safe.
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof DisplayEntity.TextDisplayEntity
                    && trackedEntity.getCommandTags().contains("viastyle_nametag")) {
                java.util.UUID owner = NametagManager.getOwnerUuid(trackedEntity.getId());
                if (owner != null && player.getUuid().equals(owner)) {
                    net.minecraft.server.MinecraftServer srv = PlaceholderHelper.getServer();
                    if (srv != null) {
                        srv.execute(() -> {
                            if (!player.isDisconnected()) {
                                player.networkHandler.sendPacket(
                                        new EntitiesDestroyS2CPacket(trackedEntity.getId()));
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
            if (entity instanceof ServerPlayerEntity player) {
                NametagManager.onPlayerDeath(player);
            }
        });
    }

    /**
     * Builds a styled join/leave message from a format string.
     * Supports &-colour codes, #hex, and {name} placeholder
     * (replaced by the player's nick-coloured name).
     * Package-private so VanishCompat can use it for vanish/unvanish messages.
     */
    static Text buildJoinLeaveMessage(String format, ServerPlayerEntity player) {
        if (format == null) {
            format = "{name}";
        }
        MutableText coloredName = NickColorManager.getColoredName(player);
        if (coloredName == null) {
            coloredName = Text.literal(player.getName().getString());
        }

        String normalized = format.replace("%name%", "{name}");
        String[] parts = normalized.split("\\{name}", -1);
        MutableText result = Text.empty();
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

    private static Text safeJoinLeaveMessage(String format, ServerPlayerEntity player, boolean join) {
        String finalFormat = format;
        if (finalFormat == null || finalFormat.isBlank()) {
            finalFormat = join ? "&a+ &r{name}" : "&c- &r{name}";
        }
        return buildJoinLeaveMessage(finalFormat, player);
    }

    private static void broadcastJoinLeaveRespectVanish(net.minecraft.server.MinecraftServer server,
                                                        ServerPlayerEntity actor,
                                                        Text message) {
        if (server == null || actor == null || message == null || message.getString().isBlank()) {
            return;
        }

        UUID actorUuid = actor.getUuid();
        boolean actorVanished = VanishHelper.isVanished(actor);
        for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            if (recipient.getUuid().equals(actorUuid)) continue;
            if (actorVanished && !VanishHelper.canSeePlayer(actor, recipient)) continue;
            recipient.sendMessage(message, false);
        }
    }


}
