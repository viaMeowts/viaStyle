package com.viameowts.viastyle;

import com.viameowts.viastyle.mixin.DisplayEntityAccessor;
import com.viameowts.viastyle.mixin.TextDisplayEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.GameMode;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player nametag colours above the head.
 *
 * <p>Two modes (config: {@code nickcolor.nametag_mode}):</p>
 * <ul>
 *   <li><b>team</b> — scoreboard team colour, limited to 16 vanilla
 *       colours.  Hex and gradient specs are mapped to the nearest
 *       vanilla colour.</li>
 *   <li><b>display</b> — (experimental) spawns a {@code TextDisplayEntity}
 *       above the player with full MiniMessage gradient / hex support.
 *       The display entity is teleported to follow the player every tick.
 *       The vanilla nametag is hidden via team visibility.</li>
 * </ul>
 */
public final class NametagManager {

    private static final String TEAM_PREFIX = "vm_";
    private static final String DISPLAY_TAG = "viastyle_nametag";

    /** Height above player position for the TextDisplay. */
    private static final double DISPLAY_Y_OFFSET = 2.3;

    /** Display-mode: track per-player TextDisplayEntity. */
    private static final Map<UUID, DisplayEntity.TextDisplayEntity> displayEntities
            = new ConcurrentHashMap<>();

    /** Reverse mapping: entity ID → owner player UUID (for hiding from owner). */
    private static final Map<Integer, UUID> entityOwners = new ConcurrentHashMap<>();

    /** Tracks players whose TextDisplay is temporarily hidden (dead/spectator/invis). */
    private static final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();

    /** Full-refresh counter (team re-check, orphan cleanup). */
    private static int fullRefreshCounter = 0;
    private static final int FULL_REFRESH_INTERVAL = 100; // every 5 seconds

    /** Orphan-scan counter — uses its own interval from config. */
    private static int orphanScanCounter = 0;

    private NametagManager() {}

    // ═══════════════════════════════════════════════════════════════════════
    //  Tick handler — register on END_SERVER_TICK
    // ═══════════════════════════════════════════════════════════════════════

    public static void onTick(MinecraftServer server) {
        if (!viaStyle.CONFIG.nickColorEnabled || !viaStyle.CONFIG.nickColorInNametag) return;

        // ── Display mode: visibility management + teleport ────────
        if ("display".equalsIgnoreCase(viaStyle.CONFIG.nametagMode)) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID uuid = p.getUuid();
                DisplayEntity.TextDisplayEntity td = displayEntities.get(uuid);

                boolean shouldHide = shouldHideNametag(p);

                if (shouldHide) {
                    // Player dead/spectator/invisible — fully discard the entity
                    // so the entity tracker stops re-sending spawn packets.
                    hiddenPlayers.add(uuid);
                    if (td != null && !td.isRemoved()) {
                        removeDisplayEntity(p);
                    }
                } else {
                    if (hiddenPlayers.remove(uuid)) {
                        // Player is visible again — re-create the display
                        updatePlayerDisplay(p);
                        td = displayEntities.get(uuid);
                    }
                    if (td != null && !td.isRemoved()) {
                        // Keep destroying for the owner every tick
                        p.networkHandler.sendPacket(
                                new EntitiesDestroyS2CPacket(td.getId()));
                        // Teleport fallback for unmounted entities
                        if (!td.hasVehicle()) {
                            td.setPosition(p.getX(), p.getY() + DISPLAY_Y_OFFSET, p.getZ());
                        }
                    }
                }
            }

            // ── Orphan cleanup: catch bots/players removed mid-tick ────
            // Carpet fake players can be killed and removed from the
            // player list within the same tick, so the loop above never
            // sees them as dead.  Clean up any TextDisplays whose owner
            // is no longer online.
            displayEntities.entrySet().removeIf(entry -> {
                UUID orphanUuid = entry.getKey();
                if (server.getPlayerManager().getPlayer(orphanUuid) == null) {
                    DisplayEntity.TextDisplayEntity orphan = entry.getValue();
                    entityOwners.remove(orphan.getId());
                    if (!orphan.isRemoved()) {
                        EntitiesDestroyS2CPacket pkt =
                                new EntitiesDestroyS2CPacket(orphan.getId());
                        for (ServerPlayerEntity online :
                                server.getPlayerManager().getPlayerList()) {
                            online.networkHandler.sendPacket(pkt);
                        }
                        orphan.discard();
                    }
                    hiddenPlayers.remove(orphanUuid);
                    return true;
                }
                return false;
            });
        }

        // ── Periodic full refresh for both modes ───────────────────────
        if (++fullRefreshCounter >= FULL_REFRESH_INTERVAL) {
            fullRefreshCounter = 0;
            updateAll(server);
        }

        // ── Orphan scan (configurable interval, can be disabled) ────────────
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg.nametagOrphanScanEnabled && cfg.nametagOrphanScanIntervalTicks > 0) {
            if (++orphanScanCounter >= cfg.nametagOrphanScanIntervalTicks) {
                orphanScanCounter = 0;
                if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                    cleanupOrphanedDisplays(server);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════

    public static void updatePlayer(ServerPlayerEntity player) {
        if (!viaStyle.CONFIG.nickColorEnabled || !viaStyle.CONFIG.nickColorInNametag) return;

        String mode = viaStyle.CONFIG.nametagMode;
        if ("display".equalsIgnoreCase(mode)) {
            updatePlayerDisplay(player);
        } else {
            updatePlayerTeam(player);
        }
    }

    public static void updateAll(MinecraftServer server) {
        if (!viaStyle.CONFIG.nickColorEnabled || !viaStyle.CONFIG.nickColorInNametag) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            updatePlayer(p);
        }
    }

    /**
     * Called immediately when a player dies.
     * Minecraft dismounts all passengers on death, so the TextDisplay would
     * float at the death location until the next tick. We discard it right away.
     */
    public static void onPlayerDeath(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        hiddenPlayers.add(uuid);
        DisplayEntity.TextDisplayEntity td = displayEntities.remove(uuid);
        if (td != null) {
            entityOwners.remove(td.getId());
            if (!td.isRemoved()) {
                try { td.discard(); } catch (Exception ignored) {}
            }
        }
    }

    public static void removePlayer(ServerPlayerEntity player) {
        removePlayer(player, null);
    }

    /**
     * Called at disconnect. Marks the player as removed from our tracking
     * and does a best-effort entity discard.
     * The periodic {@code cleanupOrphanedDisplays} scan (every 5 s) is the
     * real safety net for anything that slips through here.
     */
    public static void removePlayer(ServerPlayerEntity player, MinecraftServer server) {
        UUID uuid = player.getUuid();
        hiddenPlayers.remove(uuid);

        // Scoreboard team cleanup — best-effort
        if (server != null) {
            try {
                ServerScoreboard scoreboard = server.getScoreboard();
                String teamName = teamName(player.getName().getString());
                removeTeam(scoreboard, teamName, player.getName().getString());
            } catch (Exception e) {
                viaStyle.LOGGER.warn("[viaStyle] removePlayer scoreboard cleanup failed for {}: {}",
                        uuid, e.getMessage());
            }
        }

        // Entity discard — best-effort.
        // discard() triggers the entity tracker, which sends EntitiesDestroyS2CPacket
        // to all tracking clients automatically — no need to broadcast manually.
        DisplayEntity.TextDisplayEntity td = displayEntities.remove(uuid);
        if (td != null) {
            entityOwners.remove(td.getId());
            if (!td.isRemoved()) {
                try {
                    td.discard();
                } catch (Exception e) {
                    viaStyle.LOGGER.warn("[viaStyle] discard() failed for {}'s TextDisplay: {}",
                            uuid, e.getMessage());
                    // The periodic orphan scan will clean this up within 5 seconds.
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Mode: TEAM — scoreboard team colour  (16 vanilla colours)
    // ═══════════════════════════════════════════════════════════════════════

    private static void updatePlayerTeam(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        // Clean up any leftover display entity from a previous mode switch
        removeDisplayEntity(player);

        ServerScoreboard scoreboard = server.getScoreboard();
        String playerName = player.getName().getString();
        String teamName   = teamName(playerName);

        TextColor teamColor = resolveTeamColor(player);

        // LP prefix for nametag
        MutableText lpPrefixText = Text.empty();
        if (viaStyle.CONFIG.nametagShowLpPrefix) {
            String lpPrefix = LuckPermsHelper.getPrefix(player.getUuid());
            if (lpPrefix != null && !lpPrefix.isBlank()) {
                lpPrefixText = parseLegacyColors(lpPrefix);
            }
        }

        boolean hasPrefix = !lpPrefixText.getString().isEmpty();

        if (teamColor == null && !hasPrefix) {
            viaStyle.LOGGER.debug("[viaStyle] Nametag(team): no colour or prefix for {} — skipping.", playerName);
            removeTeam(scoreboard, teamName, playerName);
            return;
        }

        // Check if player is already on a foreign team (from another mod/plugin)
        Team existingTeam = scoreboard.getScoreHolderTeam(playerName);
        if (existingTeam != null && !existingTeam.getName().equals(teamName)) {
            viaStyle.LOGGER.debug("[viaStyle] Nametag(team): {} is on foreign team '{}', removing first.",
                    playerName, existingTeam.getName());
            try {
                scoreboard.removeScoreHolderFromTeam(playerName, existingTeam);
            } catch (java.util.ConcurrentModificationException cme) {
                viaStyle.LOGGER.warn("[viaStyle] Skipped foreign-team removal for {} (CME)", playerName);
            }
        }

        boolean isNew = false;
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
            isNew = true;
        }

        Formatting closest = teamColor != null ? closestFormatting(teamColor) : null;
        team.setColor(closest != null ? closest : Formatting.RESET);
        team.setPrefix(lpPrefixText);
        team.setSuffix(Text.empty());
        team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.ALWAYS);

        if (!team.getPlayerList().contains(playerName)) {
            try {
                scoreboard.addScoreHolderToTeam(playerName, team);
            } catch (java.util.ConcurrentModificationException cme) {
                viaStyle.LOGGER.warn("[viaStyle] Skipped addScoreHolderToTeam(team) for {} (CME)", playerName);
            }
        }

        if (isNew) {
            scoreboard.updateScoreboardTeamAndPlayers(team);
        } else {
            scoreboard.updateScoreboardTeam(team);
        }

        viaStyle.LOGGER.debug("[viaStyle] Nametag(team): {} → {} (team={}, new={})",
                playerName, closest, teamName, isNew);
    }

    /**
     * Resolves which single colour to use for the team, based on the config
     * strategy ({@code "first"} or {@code "average"}).
     */
    private static TextColor resolveTeamColor(ServerPlayerEntity player) {
        String strategy = viaStyle.CONFIG.nametagColorStrategy;
        if ("average".equalsIgnoreCase(strategy)) {
            return MiniMessageParser.averageColor(
                    NickColorManager.getColorSpec(player.getUuid()));
        }
        // default: "first" — use the primary (first stop) colour
        return NickColorManager.getPrimaryColor(player.getUuid());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Mode: DISPLAY — TextDisplayEntity riding player  (full gradient/hex)
    // ═══════════════════════════════════════════════════════════════════════

    private static void updatePlayerDisplay(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        // Skip if the player is dead/spectator/invisible — onTick will
        // re-create the display when the player becomes visible again.
        if (shouldHideNametag(player)) return;

        ServerScoreboard scoreboard = server.getScoreboard();
        String playerName = player.getName().getString();
        String teamName   = teamName(playerName);

        MutableText coloredName = NickColorManager.getColoredName(player);

        // Resolve LP prefix for nametag
        MutableText lpPrefixText = Text.empty();
        if (viaStyle.CONFIG.nametagShowLpPrefix) {
            String lpPrefix = LuckPermsHelper.getPrefix(player.getUuid());
            if (lpPrefix != null && !lpPrefix.isBlank()) {
                lpPrefixText = parseLegacyColors(lpPrefix);
            }
        }
        boolean hasPrefix = !lpPrefixText.getString().isEmpty();

        if (coloredName == null && !hasPrefix) {
            // No nick colour and no LP prefix → restore vanilla nametag
            removeDisplayEntity(player);
            removeTeam(scoreboard, teamName, playerName);
            return;
        }

        // Build the full nametag text: [lpPrefix][coloredName or plain name]
        MutableText nameText = coloredName != null
                ? coloredName
                : Text.literal(playerName);
        MutableText fullText = hasPrefix
                ? lpPrefixText.copy().append(nameText)
                : nameText;

        // 1. Hide vanilla nametag via scoreboard team
        ensureHiddenTeam(scoreboard, teamName, playerName);

        // 2. Create or update the TextDisplayEntity
        UUID uuid = player.getUuid();
        DisplayEntity.TextDisplayEntity td = displayEntities.get(uuid);
        boolean needsRecreate = td == null
                || td.isRemoved()
                || td.getEntityWorld() != player.getEntityWorld()
                || !td.hasVehicle();

        if (needsRecreate) {
            if (td != null && !td.isRemoved()) {
                td.discard();
            }
            td = spawnTextDisplay((ServerWorld) player.getEntityWorld(), player, fullText);
            if (td != null) {
                displayEntities.put(uuid, td);
                entityOwners.put(td.getId(), uuid);
            }
        } else {
            // Just update the text content
            ((TextDisplayEntityAccessor) td).invokeSetText(fullText);
        }
    }

    /**
     * Spawns a new {@code TextDisplayEntity} and mounts it on the player
     * as a passenger. Uses a mixin redirect on {@code Entity.startRiding}
     * to bypass the vanilla {@code isSaveable()} check for player entities.
     *
     * @return the display entity, or {@code null} on failure
     */
    private static DisplayEntity.TextDisplayEntity spawnTextDisplay(
            ServerWorld world, ServerPlayerEntity player, Text text) {
        try {
            DisplayEntity.TextDisplayEntity td =
                    new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);

            // ── Text & appearance ──────────────────────────────────────────
            ((TextDisplayEntityAccessor) td).invokeSetText(text);
            ((TextDisplayEntityAccessor) td).invokeSetBackground(0);       // transparent bg
            ((TextDisplayEntityAccessor) td).invokeSetTextOpacity((byte) -1); // fully opaque

            // ── Billboard: always face viewer ──────────────────────────────
            ((DisplayEntityAccessor) td).invokeSetBillboardMode(
                    DisplayEntity.BillboardMode.CENTER);

            // ── Translation: offset above the passenger attachment ─────────
            td.getDataTracker().set(
                    DisplayEntityAccessor.getTranslationField(),
                    new org.joml.Vector3f(0f, 0.3f, 0f));

            // ── Entity flags ───────────────────────────────────────────────
            td.setNoGravity(true);
            td.setSilent(true);
            td.setInvulnerable(true);
            td.addCommandTag(DISPLAY_TAG);

            // ── Position & spawn ───────────────────────────────────────────
            td.setPosition(player.getX(), player.getY() + DISPLAY_Y_OFFSET, player.getZ());
            world.spawnEntity(td);

            // ── Mount on player as passenger ───────────────────────────────
            // The EntityStartRidingMixin redirect bypasses the isSaveable()
            // check for entities tagged with DISPLAY_TAG.
            boolean mounted = td.startRiding(player, true, true);

            if (!mounted) {
                viaStyle.LOGGER.warn("[viaStyle] TextDisplay could not mount player {} — using teleport fallback",
                        player.getName().getString());
            } else {
                viaStyle.LOGGER.info("[viaStyle] TextDisplay mounted on {}",
                        player.getName().getString());
            }

            // Immediately hide from the owner so they never see the
            // spawn animation or the entity at the initial position.
            player.networkHandler.sendPacket(
                    new EntitiesDestroyS2CPacket(td.getId()));

            return td;
        } catch (Exception e) {
            viaStyle.LOGGER.error("[viaStyle] Failed to create TextDisplay for {}: {}",
                    player.getName().getString(), e.getMessage());
            return null;
        }
    }

    /**
     * Ensures a team exists with {@code nameTagVisibility = NEVER} so the
     * vanilla nametag is hidden while the TextDisplayEntity is active.
     */
    private static void ensureHiddenTeam(ServerScoreboard scoreboard,
                                          String teamName, String playerName) {
        boolean isNew = false;
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
            isNew = true;
        }

        team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
        team.setColor(Formatting.RESET);
        team.setPrefix(Text.empty());
        team.setSuffix(Text.empty());

        if (!team.getPlayerList().contains(playerName)) {
            try {
                scoreboard.addScoreHolderToTeam(playerName, team);
            } catch (java.util.ConcurrentModificationException cme) {
                // The scoreboard's internal team-player map was modified
                // concurrently (e.g. player disconnecting). Safe to ignore —
                // the nametag will be corrected on the next update.
                viaStyle.LOGGER.warn(
                        "[viaStyle] Skipped addScoreHolderToTeam for {} (scoreboard CME during disconnect)",
                        playerName);
            }
        }

        if (isNew) {
            scoreboard.updateScoreboardTeamAndPlayers(team);
        } else {
            scoreboard.updateScoreboardTeam(team);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Cleanup helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Removes the TextDisplay entity for a player (e.g. when toggling modes
     * or when no colour/prefix is needed). Not used at disconnect — see
     * {@link #removePlayer(ServerPlayerEntity, MinecraftServer)}.
     */
    private static void removeDisplayEntity(ServerPlayerEntity player) {
        DisplayEntity.TextDisplayEntity td = displayEntities.remove(player.getUuid());
        if (td != null) {
            entityOwners.remove(td.getId());
            if (!td.isRemoved()) {
                try {
                    td.discard();
                } catch (Exception e) {
                    viaStyle.LOGGER.warn("[viaStyle] removeDisplayEntity discard failed for {}: {}",
                            player.getName().getString(), e.getMessage());
                }
            }
        }
    }

    /**
     * Returns the owner player UUID for a tracked TextDisplay entity,
     * or {@code null} if the entity isn't one of ours.
     */
    public static UUID getOwnerUuid(int entityId) {
        return entityOwners.get(entityId);
    }

    /**
     * Determines whether a player's TextDisplay nametag should be hidden.
     * Returns {@code true} if the player is dead, in spectator mode, or
     * has the invisibility status effect.
     */
    private static boolean shouldHideNametag(ServerPlayerEntity player) {
        // Dead players — nametag should not float at death location
        if (player.isDead()) return true;
        // Spectators are invisible
        if (player.getGameMode() == GameMode.SPECTATOR) return true;
        // Invisibility potion
        if (player.hasStatusEffect(StatusEffects.INVISIBILITY)) return true;
        // Vanished players — remove nametag while invisible to others
        if (VanishHelper.isVanished(player)) return true;
        return false;
    }

    /**
     * Scans all worlds for {@code TextDisplayEntity} objects tagged with
     * {@value DISPLAY_TAG} whose owner is no longer online (or has no owner)
     * and discards them. This is the last-resort safety net for entities that
     * slipped through the normal {@link #removeDisplayEntity} path.
     */
    public static void cleanupOrphanedDisplays(MinecraftServer server) {
        int removed = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<Entity> orphans = new ArrayList<>();
            world.iterateEntities().forEach(e -> {
                if (!(e instanceof DisplayEntity.TextDisplayEntity)) return;
                if (!e.getCommandTags().contains(DISPLAY_TAG)) return;
                if (e.isRemoved()) return; // already discarded, world will remove it shortly

                // Check if this entity has a live owner
                UUID ownerUuid = entityOwners.get(e.getId());
                if (ownerUuid != null && server.getPlayerManager().getPlayer(ownerUuid) != null) {
                    return; // owner is online — don't touch it
                }
                orphans.add(e);
            });
            for (Entity e : orphans) {
                entityOwners.remove(e.getId());
                try { e.discard(); } catch (Exception ignored) {}
                removed++;
            }
        }
        if (removed > 0) {
            viaStyle.LOGGER.debug("[viaStyle] Cleaned up {} orphaned nametag display(s).", removed);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Shared helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static String teamName(String playerName) {
        // Scoreboard team names: max 16 chars
        String raw = TEAM_PREFIX + playerName;
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }

    private static void removeTeam(Scoreboard scoreboard, String teamName,
                                    String playerName) {
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            try {
                if (team.getPlayerList().contains(playerName)) {
                    scoreboard.removeScoreHolderFromTeam(playerName, team);
                }
                scoreboard.removeTeam(team);
            } catch (java.util.ConcurrentModificationException cme) {
                viaStyle.LOGGER.warn(
                        "[viaStyle] Skipped removeTeam for {} (scoreboard CME during disconnect)",
                        playerName);
            }
        }
    }

    /**
     * Converts legacy {@code &} / {@code §} color codes to a styled {@link MutableText}.
     * Used to render LuckPerms prefixes that use legacy formatting.
     */
    private static MutableText parseLegacyColors(String input) {
        if (input == null || input.isEmpty()) return Text.empty();
        // Full Patbox format pipeline (falls back to built-in parser if PAPI absent).
        return Text.empty().append(PlaceholderHelper.parseFormat(input, null));
    }

    /**
     * Finds the closest vanilla {@link Formatting} to the given RGB colour.
     */
    private static Formatting closestFormatting(TextColor target) {
        int tr = (target.getRgb() >> 16) & 0xFF;
        int tg = (target.getRgb() >> 8) & 0xFF;
        int tb = target.getRgb() & 0xFF;

        Formatting best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Formatting fmt : Formatting.values()) {
            if (fmt.isModifier() || fmt.getColorValue() == null) continue;
            int cv = fmt.getColorValue();
            int cr = (cv >> 16) & 0xFF, cg = (cv >> 8) & 0xFF, cb = cv & 0xFF;
            int dist = (tr - cr) * (tr - cr) + (tg - cg) * (tg - cg) + (tb - cb) * (tb - cb);
            if (dist < bestDist) {
                bestDist = dist;
                best = fmt;
            }
        }
        return best;
    }
}
