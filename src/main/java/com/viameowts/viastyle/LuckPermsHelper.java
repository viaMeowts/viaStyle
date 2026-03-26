package com.viameowts.viastyle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Reflection-based LuckPerms integration.
 * Retrieves player prefix and suffix from LuckPerms' cached meta-data
 * without a compile-time dependency.
 *
 * <p>The full reflection chain:
 * {@code LuckPermsProvider.get()} &rarr; {@code getUserManager()} &rarr;
 * {@code getUser(UUID)} &rarr; {@code getCachedData()} &rarr;
 * {@code getMetaData()} &rarr; {@code getPrefix()} / {@code getSuffix()}</p>
 */
public final class LuckPermsHelper {

    private static boolean available = false;

    // Cached Method handles for the reflection chain
    private static Method getApiMethod;           // LuckPermsProvider.get()
    private static Method getUserManagerMethod;    // LuckPerms.getUserManager()
    private static Method getUserMethod;           // UserManager.getUser(UUID)
    private static Method getCachedDataMethod;     // PermissionHolder.getCachedData()
    private static Method getMetaDataMethod;       // CachedDataManager.getMetaData()
    private static Method getPrefixMethod;         // CachedMetaData.getPrefix()
    private static Method getSuffixMethod;         // CachedMetaData.getSuffix()
    private static Method getPermissionDataMethod; // CachedDataManager.getPermissionData()
    private static Method checkPermissionMethod;   // CachedPermissionData.checkPermission(String)
    private static Method tristateAsBooleanMethod; // Tristate.asBoolean()

    // Async user loading
    private static Method loadUserMethod;            // UserManager.loadUser(UUID) → CompletableFuture<User>

    // Group weight chain
    private static Method getGroupManagerMethod;    // LuckPerms.getGroupManager()
    private static Method getGroupMethod;           // GroupManager.getGroup(String)
    private static Method getPrimaryGroupMethod;    // User.getPrimaryGroup()
    private static Method getWeightMethod;          // Group.getWeight()

    // Direct group-node scanning — finds max weight across ALL groups player is directly in
    private static Method getNodesMethod;           // PermissionHolder.getNodes() → Collection<Node>
    private static Method nodeGetValueMethod;       // Node.getValue() → boolean
    private static Class<?> inheritanceNodeClass;   // InheritanceNode (for instanceof check)
    private static Method getGroupNameFromNodeMethod; // InheritanceNode.getGroupName() → String

    // LP meta value — CachedMetaData.getMetaValue(String) respects LP priority/inheritance
    private static Method getMetaValueMethod;

    private LuckPermsHelper() {}

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            viaStyle.LOGGER.info("[viaStyle] LuckPerms not found => prefix/suffix integration disabled.");
            return;
        }
        try {
            Class<?> providerClass   = Class.forName("net.luckperms.api.LuckPermsProvider");
            getApiMethod = providerClass.getMethod("get");

            Class<?> apiClass        = Class.forName("net.luckperms.api.LuckPerms");
            getUserManagerMethod = apiClass.getMethod("getUserManager");

            Class<?> userMgrClass    = Class.forName("net.luckperms.api.model.user.UserManager");
            getUserMethod = userMgrClass.getMethod("getUser", UUID.class);

            // loadUser(UUID) → CompletableFuture<User>  (for async loading on join)
            try {
                loadUserMethod = userMgrClass.getMethod("loadUser", UUID.class);
            } catch (Throwable ignored) {}

            // getCachedData() lives on PermissionHolder (super-interface of User)
            Class<?> permHolder      = Class.forName("net.luckperms.api.model.PermissionHolder");
            getCachedDataMethod = permHolder.getMethod("getCachedData");

            Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            getMetaDataMethod = cachedDataClass.getMethod("getMetaData");

            Class<?> metaDataClass   = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            getPrefixMethod = metaDataClass.getMethod("getPrefix");
            getSuffixMethod = metaDataClass.getMethod("getSuffix");
            // getMetaValue(String key) → @Nullable String — respects LP's full priority chain
            try { getMetaValueMethod = metaDataClass.getMethod("getMetaValue", String.class); } catch (Throwable ignored) {}

            // Permission check chain (best-effort — failure doesn't break prefix/suffix)
            try {
                Class<?> cachedPermClass = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
                getPermissionDataMethod = cachedDataClass.getMethod("getPermissionData");
                checkPermissionMethod   = cachedPermClass.getMethod("checkPermission", String.class);
                Class<?> tristateClass  = Class.forName("net.luckperms.api.util.Tristate");
                tristateAsBooleanMethod = tristateClass.getMethod("asBoolean");
            } catch (Throwable ignored) {}

            // Group weight chain (best-effort)
            try {
                getGroupManagerMethod = apiClass.getMethod("getGroupManager");
                Class<?> groupMgrClass = Class.forName("net.luckperms.api.model.group.GroupManager");
                getGroupMethod = groupMgrClass.getMethod("getGroup", String.class);
                Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
                getPrimaryGroupMethod = userClass.getMethod("getPrimaryGroup");
                Class<?> groupClass = Class.forName("net.luckperms.api.model.group.Group");
                getWeightMethod = groupClass.getMethod("getWeight");
                // Direct node scanning (permHolder is in scope from outer try-block)
                try {
                    getNodesMethod = permHolder.getMethod("getNodes");
                    nodeGetValueMethod = Class.forName("net.luckperms.api.node.Node")
                            .getMethod("getValue");
                    inheritanceNodeClass = Class.forName(
                            "net.luckperms.api.node.types.InheritanceNode");
                    getGroupNameFromNodeMethod = inheritanceNodeClass.getMethod("getGroupName");
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            available = true;
            viaStyle.LOGGER.info("[viaStyle] LuckPerms detected => prefix/suffix integration enabled!");
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] LuckPerms init failed: {}", t.getMessage());
        }
    }

    public static boolean isAvailable() {
        return available && viaStyle.CONFIG != null && viaStyle.CONFIG.useLuckPerms;
    }

    /**
     * Returns the LuckPerms prefix for the given player, or {@code ""} when
     * LuckPerms is unavailable / the user has no prefix set.
     */
    public static String getPrefix(UUID uuid) {
        if (!isAvailable()) return "";
        try {
            Object meta = resolveMeta(uuid);
            if (meta == null) return "";
            Object prefix = getPrefixMethod.invoke(meta);
            return prefix != null ? prefix.toString() : "";
        } catch (Throwable t) {
            viaStyle.LOGGER.error("[viaStyle] LuckPerms getPrefix error: {}", t.getMessage());
            return "";
        }
    }

    /**
     * Returns the LuckPerms suffix for the given player, or {@code ""} when
     * LuckPerms is unavailable / the user has no suffix set.
     */
    public static String getSuffix(UUID uuid) {
        if (!isAvailable()) return "";
        try {
            Object meta = resolveMeta(uuid);
            if (meta == null) return "";
            Object suffix = getSuffixMethod.invoke(meta);
            return suffix != null ? suffix.toString() : "";
        } catch (Throwable t) {
            viaStyle.LOGGER.error("[viaStyle] LuckPerms getSuffix error: {}", t.getMessage());
            return "";
        }
    }

    /** Walks the LuckPerms reflection chain down to CachedMetaData. Returns null on any failure. */
    private static Object resolveMeta(UUID uuid) throws Exception {
        Object api         = getApiMethod.invoke(null);
        Object userManager = getUserManagerMethod.invoke(api);
        Object user        = getUserMethod.invoke(userManager, uuid);
        if (user == null) return null;
        Object cachedData  = getCachedDataMethod.invoke(user);
        return getMetaDataMethod.invoke(cachedData);
    }

    // ── Package-visible accessors for NickColorManager ─────────────────────

    static Object getApi() throws Exception {
        return getApiMethod != null ? getApiMethod.invoke(null) : null;
    }

    static Object getUserManager(Object api) throws Exception {
        return getUserManagerMethod.invoke(api);
    }

    static Object getUser(Object userManager, UUID uuid) throws Exception {
        return getUserMethod.invoke(userManager, uuid);
    }

    static Object getCachedData(Object user) throws Exception {
        return getCachedDataMethod.invoke(user);
    }

    static Object getPermissionData(Object cachedData) throws Exception {
        return getPermissionDataMethod != null
                ? getPermissionDataMethod.invoke(cachedData) : null;
    }

    /**
     * Checks whether the given player has a specific LuckPerms permission.
     * Returns {@code false} when LuckPerms is unavailable or the check fails.
     *
     * <p>Also checks the legacy {@code viamod.*} equivalent for backward
     * compatibility with servers that haven't updated their LP setup yet.</p>
     */
    public static boolean hasPermission(UUID uuid, String permission) {
        if (!isAvailable()) return false;
        if (getPermissionDataMethod == null || checkPermissionMethod == null
                || tristateAsBooleanMethod == null) return false;
        try {
            Object api         = getApiMethod.invoke(null);
            Object userManager = getUserManagerMethod.invoke(api);
            Object user        = getUserMethod.invoke(userManager, uuid);
            if (user == null) return false;
            Object cachedData  = getCachedDataMethod.invoke(user);
            Object permData    = getPermissionDataMethod.invoke(cachedData);
            Object tristate    = checkPermissionMethod.invoke(permData, permission);
            Object result      = tristateAsBooleanMethod.invoke(tristate);
            if (result instanceof Boolean b && b) return true;

            // Backward compat: also check legacy viamod.* equivalent
            if (permission.startsWith("viastyle.")) {
                String legacy = "viamod." + permission.substring("viastyle.".length());
                tristate = checkPermissionMethod.invoke(permData, legacy);
                result   = tristateAsBooleanMethod.invoke(tristate);
                return result instanceof Boolean b2 && b2;
            }
            return false;
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms hasPermission error: {}", t.getMessage());
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
    public static boolean checkPermission(ServerCommandSource source, String permission, int opLevel) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            if (hasPermission(player.getUuid(), permission)) return true;
        }
        return hasOpLevel(source, opLevel);
    }

    /**
     * Player-friendly permission check with LuckPerms-first logic and OP fallback.
     * Uses OP level 2 by default.
     */
    public static boolean checkPlayerPermission(ServerCommandSource source, String permission) {
        return checkPlayerPermission(source, permission, 2);
    }

    /**
     * Player-friendly permission check with LuckPerms-first logic and configurable OP fallback.
     */
    public static boolean checkPlayerPermission(ServerCommandSource source, String permission, int opLevel) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            if (hasPermission(player.getUuid(), permission)) return true;
            return hasOpLevel(source, opLevel);
        }
        return hasOpLevel(source, opLevel);
    }

    /**
     * Minecraft 1.21.11 replaced {@code hasPermissionLevel(int)} with
     * {@code PermissionPredicate} / {@code LeveledPermissionPredicate}.
     */
    public static boolean hasOpLevel(ServerCommandSource source, int opLevel) {
        return source != null && hasOpLevel(source.getPermissions(), opLevel);
    }

    /**
     * Player-side variant of {@link #hasOpLevel(ServerCommandSource, int)}.
     */
    public static boolean hasOpLevel(ServerPlayerEntity player, int opLevel) {
        return player != null && hasOpLevel(player.getPermissions(), opLevel);
    }

    private static boolean hasOpLevel(PermissionPredicate predicate, int opLevel) {
        return predicate instanceof LeveledPermissionPredicate leveled
                && leveled.getLevel().isAtLeast(PermissionLevel.fromLevel(opLevel));
    }

    /**
     * Returns the effective sort weight for a player based on their LuckPerms groups.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Scan all <b>directly-assigned</b> group nodes on the user via
     *       {@code PermissionHolder.getNodes()} and return the <b>maximum</b> weight
     *       found across those groups.  This correctly handles cases where a player is
     *       in {@code admin} (weight 100) even if their "primary group" is {@code default}
     *       (weight 0) — parent/inherited groups of groups are <em>not</em> included.</li>
     *   <li>Falls back to the primary group weight if node scanning is not available.</li>
     * </ol>
     * Returns {@code 0} when LP is unavailable or the query fails.
     */
    public static int getGroupWeight(UUID uuid) {
        if (!isAvailable()) return 0;
        if (getGroupManagerMethod == null || getGroupMethod == null || getWeightMethod == null)
            return 0;
        try {
            Object api         = getApiMethod.invoke(null);
            Object userManager = getUserManagerMethod.invoke(api);
            Object user        = getUserMethod.invoke(userManager, uuid);
            if (user == null) return 0;

            Object groupManager = getGroupManagerMethod.invoke(api);

            // ── Strategy 1: LP meta "weight" ─────────────────────────────────────
            // /lp group <name> weight <n>  stores weight as meta and as a permission node.
            // CachedMetaData.getMetaValue("weight") returns the highest-priority value
            // already resolved through the full LP inheritance chain — the most reliable source.
            if (getMetaValueMethod != null) {
                try {
                    Object meta = resolveMeta(uuid);
                    if (meta != null) {
                        Object val = getMetaValueMethod.invoke(meta, "weight");
                        if (val != null) {
                            String s = val.toString().trim();
                            if (!s.isEmpty()) return Integer.parseInt(s);
                        }
                    }
                } catch (NumberFormatException ignored) {
                } catch (Throwable ignored) {}
            }

            // ── Strategy 2: max weight across directly-assigned group nodes ───────────
            // PermissionHolder.getNodes() contains InheritanceNode entries for each group
            // the player is directly a member of.  We take the maximum weight.
            if (getNodesMethod != null && inheritanceNodeClass != null
                    && getGroupNameFromNodeMethod != null) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<Object> nodes =
                            (java.util.Collection<Object>) getNodesMethod.invoke(user);
                    int maxWeight = Integer.MIN_VALUE;
                    for (Object node : nodes) {
                        if (!inheritanceNodeClass.isInstance(node)) continue;
                        if (nodeGetValueMethod != null
                                && !Boolean.TRUE.equals(nodeGetValueMethod.invoke(node))) continue;
                        String groupName = (String) getGroupNameFromNodeMethod.invoke(node);
                        Object group = getGroupMethod.invoke(groupManager, groupName);
                        if (group == null) continue;
                        Object optInt = getWeightMethod.invoke(group);
                        if (optInt instanceof java.util.OptionalInt oi && oi.isPresent()) {
                            maxWeight = Math.max(maxWeight, oi.getAsInt());
                        }
                    }
                    if (maxWeight != Integer.MIN_VALUE) return maxWeight;
                } catch (Throwable ignored) {}
            }

            // ── Strategy 3: primary group weight fallback ──────────────────────────
            if (getPrimaryGroupMethod != null) {
                String primaryGroup = (String) getPrimaryGroupMethod.invoke(user);
                if (primaryGroup != null) {
                    Object group = getGroupMethod.invoke(groupManager, primaryGroup);
                    if (group != null) {
                        Object optInt = getWeightMethod.invoke(group);
                        if (optInt instanceof java.util.OptionalInt oi && oi.isPresent()) {
                            return oi.getAsInt();
                        }
                    }
                }
            }
            return 0;
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] LuckPerms getGroupWeight error: {}", t.getMessage());
            return 0;
        }
    }

    /**
     * Returns the value of a LuckPerms meta key for a player, or {@code null} if not set.
     *
     * <p>LP's meta system properly respects inheritance priority:
     * player-level meta overrides group meta; child group overrides parent group.
     * This makes it the most reliable way to store per-player/per-group config values.</p>
     *
     * <p>Set via: {@code /lp user <name> meta set nickcolor #ff5555}<br>
     * or: {@code /lp group <group> meta set nickcolor gradient:#ff0000:#00ff00}</p>
     */
    public static String getMetaValue(UUID uuid, String key) {
        if (!isAvailable() || getMetaValueMethod == null) return null;
        try {
            Object meta = resolveMeta(uuid);
            if (meta == null) return null;
            Object value = getMetaValueMethod.invoke(meta, key);
            return value != null ? value.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Asynchronously loads a user's LuckPerms data from storage, then
     * runs the given callback.  If LP is unavailable or the load fails,
     * the callback is run immediately so callers can fall back gracefully.
     *
     * <p>Uses {@code UserManager.loadUser(UUID)} which returns a
     * {@link CompletableFuture}.</p>
     *
     * @param uuid       the player UUID to load
     * @param onComplete callback to invoke after the user data is available
     */
    public static void loadUserAsync(UUID uuid, Runnable onComplete) {
        if (!isAvailable() || loadUserMethod == null) {
            onComplete.run();
            return;
        }
        try {
            Object api         = getApiMethod.invoke(null);
            Object userManager = getUserManagerMethod.invoke(api);
            Object future      = loadUserMethod.invoke(userManager, uuid);
            if (future instanceof CompletableFuture<?> cf) {
                cf.thenRun(onComplete).exceptionally(t -> {
                    viaStyle.LOGGER.warn("[viaStyle] LuckPerms loadUser failed for {}: {}",
                            uuid, t.getMessage());
                    onComplete.run();
                    return null;
                });
            } else {
                onComplete.run();
            }
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] LuckPerms loadUser error: {}", t.getMessage());
            onComplete.run();
        }
    }

    /**
     * Subscribes to LuckPerms {@code UserDataRecalculateEvent} via reflection.
     * When a player's permissions are recalculated (e.g. via lp commands),
     * this invalidates their nick-colour cache and re-applies tab + nametag.
     *
     * <p>Must be called <em>after</em> {@link #init()}. If LP is unavailable
     * or the subscription fails, the error is logged and the mod continues
     * without live-refresh.</p>
     *
     * @param server the current {@link MinecraftServer} instance for
     *               resolving the player from UUID
     */
    public static void subscribeToEvents(MinecraftServer server) {
        if (!available) return;
        try {
            // LuckPerms api → EventBus
            Object api = getApiMethod.invoke(null);
            Class<?> apiClass = Class.forName("net.luckperms.api.LuckPerms");
            Method getEventBusMethod = apiClass.getMethod("getEventBus");
            Object eventBus = getEventBusMethod.invoke(api);

            // EventBus.subscribe(Class, Consumer)
            Class<?> eventBusClass = Class.forName("net.luckperms.api.event.EventBus");
            Class<?> eventClass = Class.forName(
                    "net.luckperms.api.event.user.UserDataRecalculateEvent");
            Method subscribeMethod = eventBusClass.getMethod(
                    "subscribe", Class.class, Consumer.class);

            // Create handler
            // UserDataRecalculateEvent → getUser() → getUniqueId()
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Method getUniqueIdMethod = userClass.getMethod("getUniqueId");
            Method getEventUserMethod = eventClass.getMethod("getUser");

            Consumer<Object> handler = event -> {
                try {
                    Object user = getEventUserMethod.invoke(event);
                    UUID uuid = (UUID) getUniqueIdMethod.invoke(user);

                    // Run on server thread to safely manipulate MC objects
                    server.execute(() -> {
                        // Re-fetch the player — the captured reference may be
                        // stale if the player disconnected between event fire
                        // and task execution.
                        ServerPlayerEntity player = server.getPlayerManager()
                                .getPlayer(uuid);
                        if (player == null || player.isDisconnected()) return;
                        // Extra safety: player must still be tracked by the server
                        if (!server.getPlayerManager().getPlayerList().contains(player)) return;

                        viaStyle.LOGGER.debug(
                                "[viaStyle] LP recalculate event for {} — refreshing",
                                player.getName().getString());
                        try {
                            NickColorManager.invalidate(uuid);
                            TabListManager.updatePlayer(player);
                            NametagManager.updatePlayer(player);
                        } catch (Exception e) {
                            viaStyle.LOGGER.warn(
                                    "[viaStyle] LP recalculate handler skipped for {}: {}",
                                    uuid, e.getMessage());
                        }
                    });
                } catch (Throwable t) {
                    viaStyle.LOGGER.error(
                            "[viaStyle] LP event handler error: {}", t.getMessage());
                }
            };

            subscribeMethod.invoke(eventBus, eventClass, handler);
            viaStyle.LOGGER.info(
                    "[viaStyle] Subscribed to LuckPerms UserDataRecalculateEvent.");
        } catch (Throwable t) {
            viaStyle.LOGGER.warn(
                    "[viaStyle] Failed to subscribe to LuckPerms events: {}",
                    t.getMessage());
        }
    }
}
