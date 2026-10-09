package com.viameowts.viastyle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LuckPerms integration. Every call goes through {@link LuckPermsBridge}, which uses the real
 * LuckPerms API (compile-only) and is only loaded when the {@code luckperms} mod is present.
 *
 * <p>What viaStyle reads from LuckPerms:</p>
 * <ul>
 *   <li>prefix / suffix (cached meta; {@code &c}, {@code &#rrggbb} and {@code §} codes are converted)</li>
 *   <li>primary group, its display name, and the sort weight</li>
 *   <li>any meta key ({@code {lp_meta:key}} in formats, {@code nickcolor} for nick colours)</li>
 *   <li>permission checks with the order player node, then explicit deny, then group, then OP level</li>
 * </ul>
 */
public final class LuckPermsHelper {

    /** Everything the formats need about a player, read from LuckPerms in one go. */
    public record Info(String prefix, String suffix, String group, String groupName, int weight) {
        public static final Info EMPTY = new Info("", "", "", "", 0);
    }

    private static boolean available = false;

    private LuckPermsHelper() {}

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            viaStyle.LOGGER.info("[viaStyle] LuckPerms not found => prefix/suffix integration disabled.");
            return;
        }
        try {
            LuckPermsBridge.probe();
            available = true;
            viaStyle.LOGGER.info("[viaStyle] LuckPerms detected => prefix/suffix integration enabled!");
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] LuckPerms init failed: {}", t.toString());
        }
    }

    public static boolean isAvailable() {
        return available && viaStyle.CONFIG != null && viaStyle.CONFIG.useLuckPerms;
    }

    /** Prefix, suffix, group and weight of the player. {@link Info#EMPTY} when LuckPerms is off. */
    public static Info info(UUID uuid) {
        if (!isAvailable()) return Info.EMPTY;
        try {
            Info raw = LuckPermsBridge.info(uuid);
            return new Info(LegacyText.toMiniTags(raw.prefix()), LegacyText.toMiniTags(raw.suffix()),
                    raw.group(), raw.groupName(), raw.weight());
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms info error: {}", t.toString());
            return Info.EMPTY;
        }
    }

    /** LuckPerms prefix as MiniMessage tags, {@code ""} when absent. */
    public static String getPrefix(UUID uuid) {
        return info(uuid).prefix();
    }

    /** LuckPerms suffix as MiniMessage tags, {@code ""} when absent. */
    public static String getSuffix(UUID uuid) {
        return info(uuid).suffix();
    }

    /** Sort weight of the player, {@code 0} without LuckPerms. */
    public static int getGroupWeight(UUID uuid) {
        return info(uuid).weight();
    }

    /** Primary group name or {@code null}. */
    public static String getPrimaryGroup(UUID uuid) {
        String group = info(uuid).group();
        return group.isEmpty() ? null : group;
    }

    /**
     * Meta value of a player, {@code null} if not set. LuckPerms resolves it with the full
     * priority chain (player over child group over parent group).
     * Set with {@code /lp user <name> meta set nickcolor #ff5555}.
     */
    public static String getMetaValue(UUID uuid, String key) {
        if (!isAvailable()) return null;
        try {
            return LuckPermsBridge.metaValue(uuid, key);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Names of the groups the player is directly in (the primary group if there are none). */
    public static List<String> getGroupNames(UUID uuid) {
        if (!isAvailable()) return new ArrayList<>();
        try {
            return LuckPermsBridge.groups(uuid);
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms getGroupNames error: {}", t.toString());
            return new ArrayList<>();
        }
    }

    /** All resolved permissions of the player (inherited included), or {@code null}. */
    static Map<String, Boolean> getPermissionMap(UUID uuid) {
        if (!isAvailable()) return null;
        try {
            return LuckPermsBridge.permissionMap(uuid);
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms getPermissionMap error: {}", t.toString());
            return null;
        }
    }

    private static String legacyNode(String permission) {
        return permission.startsWith("viastyle.") ? "viamod." + permission.substring("viastyle.".length()) : null;
    }

    /**
     * Whether the player has the permission through LuckPerms. The legacy {@code viamod.*}
     * equivalent is accepted too, for servers that have not updated their setup.
     */
    public static boolean hasPermission(UUID uuid, String permission) {
        if (!isAvailable()) return false;
        try {
            if (LuckPermsBridge.check(uuid, permission) > 0) return true;
            String legacy = legacyNode(permission);
            return legacy != null && LuckPermsBridge.check(uuid, legacy) > 0;
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms hasPermission error: {}", t.toString());
            return false;
        }
    }

    /** Value of a node set directly on the player, {@code null} when there is none. */
    static Boolean getDirectPermission(UUID uuid, String permission) {
        if (!isAvailable()) return null;
        try {
            return LuckPermsBridge.direct(uuid, permission, legacyNode(permission));
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms getDirectPermission error: {}", t.toString());
            return null;
        }
    }

    /** True if LuckPerms explicitly denies the node, so a deny beats group allow and OP fallback. */
    public static boolean hasPermissionDenied(UUID uuid, String permission) {
        if (!isAvailable()) return false;
        try {
            if (LuckPermsBridge.check(uuid, permission) < 0) return true;
            String legacy = legacyNode(permission);
            return legacy != null && LuckPermsBridge.check(uuid, legacy) < 0;
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms hasPermissionDenied error: {}", t.toString());
            return false;
        }
    }

    /**
     * Checks a named LuckPerms permission for a command source, falling back
     * to vanilla OP level check. Designed for Brigadier {@code .requires()} predicates.
     *
     * <p>Also checks the legacy {@code viamod.*} equivalent for backward compat.</p>
     *
     * @param source     the command source
     * @param permission the LuckPerms permission node (e.g. {@code "viastyle.command.nickcolor"})
     * @param opLevel    fallback vanilla OP level (usually 2)
     * @return {@code true} if the source has the LP permission or meets the OP level
     */
    public static boolean checkPermission(CommandSourceStack source, String permission, int opLevel) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Boolean direct = getDirectPermission(player.getUUID(), permission);
            if (direct != null) return direct;
            if (hasPermissionDenied(player.getUUID(), permission)) return false;
            if (hasPermission(player.getUUID(), permission)) return true;
        }
        return hasOpLevel(source, opLevel);
    }

    /**
     * Player-friendly permission check with LuckPerms-first logic and OP fallback.
     * Uses OP level 2 by default.
     */
    public static boolean checkPlayerPermission(CommandSourceStack source, String permission) {
        return checkPlayerPermission(source, permission, 2);
    }

    /**
     * Player-friendly permission check with LuckPerms-first logic and configurable OP fallback.
     */
    public static boolean checkPlayerPermission(CommandSourceStack source, String permission, int opLevel) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Boolean direct = getDirectPermission(player.getUUID(), permission);
            if (direct != null) return direct;
            if (hasPermissionDenied(player.getUUID(), permission)) return false;
            if (hasPermission(player.getUUID(), permission)) return true;
            return hasOpLevel(source, opLevel);
        }
        return hasOpLevel(source, opLevel);
    }

    /**
     * Player-side permission check with ordered precedence: player → group → default.
     */
    public static boolean checkPlayerPermission(ServerPlayer player, String permission, int opLevel) {
        if (player == null) return false;
        Boolean direct = getDirectPermission(player.getUUID(), permission);
        if (direct != null) return direct;
        if (hasPermissionDenied(player.getUUID(), permission)) return false;
        if (hasPermission(player.getUUID(), permission)) return true;
        return hasOpLevel(player, opLevel);
    }

    /**
     * Player-side permission check with OP level 2 fallback.
     */
    public static boolean checkPlayerPermission(ServerPlayer player, String permission) {
        return checkPlayerPermission(player, permission, 2);
    }

    /**
     * Minecraft 1.21.11 replaced {@code hasPermissionLevel(int)} with
     * {@code PermissionPredicate} / {@code LeveledPermissionPredicate}.
     */
    public static boolean hasOpLevel(CommandSourceStack source, int opLevel) {
        return source != null && hasOpLevel(source.permissions(), opLevel);
    }

    /**
     * Player-side variant of {@link #hasOpLevel(CommandSourceStack, int)}.
     */
    public static boolean hasOpLevel(ServerPlayer player, int opLevel) {
        return player != null && hasOpLevel(player.permissions(), opLevel);
    }

    private static boolean hasOpLevel(PermissionSet predicate, int opLevel) {
        if (opLevel <= 0) return true; // level 0 = everyone (non-ops may carry no level at all)
        return predicate instanceof LevelBasedPermissionSet leveled
                && leveled.level().isEqualOrHigherThan(PermissionLevel.byId(opLevel));
    }

    /**
     * Loads the player's data from storage, then runs the callback. Without LuckPerms (or if the
     * load fails) the callback runs immediately so callers can fall back.
     */
    public static void loadUserAsync(UUID uuid, Runnable onComplete) {
        if (!isAvailable()) {
            onComplete.run();
            return;
        }
        try {
            CompletableFuture<?> future = LuckPermsBridge.load(uuid);
            future.thenRun(onComplete).exceptionally(t -> {
                viaStyle.LOGGER.warn("[viaStyle] LuckPerms loadUser failed for {}: {}", uuid, t.getMessage());
                onComplete.run();
                return null;
            });
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] LuckPerms loadUser error: {}", t.toString());
            onComplete.run();
        }
    }

    /**
     * Subscribes to LuckPerms' {@code UserDataRecalculateEvent}: when a player's groups, prefix or
     * permissions change (any {@code /lp} command, even from the web editor), their nick colour,
     * tab entry and nametag are refreshed, and the tab list is re-sorted.
     */
    public static void subscribeToEvents(MinecraftServer server) {
        if (!available) return;
        try {
            Consumer<UUID> handler = uuid -> server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null || player.hasDisconnected()) return;
                try {
                    NickColorManager.invalidate(uuid);
                    TabListManager.updatePlayer(player);
                    TabListManager.requestFullRefresh();
                    NametagManager.updatePlayer(player);
                } catch (Exception e) {
                    viaStyle.LOGGER.warn("[viaStyle] LP recalculate handler skipped for {}: {}", uuid, e.getMessage());
                }
            });
            LuckPermsBridge.subscribe(handler);
            viaStyle.LOGGER.info("[viaStyle] Subscribed to LuckPerms UserDataRecalculateEvent.");
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to subscribe to LuckPerms events: {}", t.toString());
        }
    }
}
