package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player nick colours.
 *
 * <p>Colour sources (in priority order):</p>
 * <ol>
 *   <li>LuckPerms permission node {@code viastyle.nickcolor.<spec>}
 *       (legacy {@code viamod.nickcolor.<spec>} also supported for backward compat)</li>
 *   <li>Manual overrides stored in {@code config/viaStyle-nickcolors.json}</li>
 * </ol>
 *
 * <p>Spec format examples:
 * {@code #ff5555}, {@code gradient:#ff0000:#00ff00}, {@code gold}.</p>
 */
public final class NickColorManager {

    private static final Path COLORS_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle").resolve("nickcolors.json");

    /** Old location for migration. */
    private static final Path OLD_COLORS_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle-nickcolors.json");

    /** Pre-rename location (viamod → viaStyle). */
    private static final Path OLD_VIAMOD_COLORS_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viamod").resolve("nickcolors.json");

    /** Pre-rename flat file. */
    private static final Path OLD_VIAMOD_FLAT_COLORS = FabricLoader.getInstance()
            .getConfigDir().resolve("viamod-nickcolors.json");

    /** UUID → colour spec string  (from file / command). */
    private static final Map<UUID, String> fileOverrides = new ConcurrentHashMap<>();

    /** UUID → resolved colour spec (cached, from LP or file). */
    private static final Map<UUID, String> cache = new ConcurrentHashMap<>();

    // ── LuckPerms reflection handles ───────────────────────────────────────
    private static Method getPermissionMapMethod;
    private static boolean lpEnumerationEnabled = false;

    private NickColorManager() {}

    // ═══════════════════════════════════════════════════════════════════════
    //  Initialisation
    // ═══════════════════════════════════════════════════════════════════════

    public static void init() {
        migrateOldFile();
        loadFile();
        initLpEnumeration();
    }

    private static void migrateOldFile() {
        if (!Files.exists(COLORS_FILE)) {
            try {
                Files.createDirectories(COLORS_FILE.getParent());
            } catch (IOException ignored) {}

            // Try viamod/nickcolors.json first (pre-rename directory)
            if (Files.exists(OLD_VIAMOD_COLORS_FILE)) {
                try {
                    Files.copy(OLD_VIAMOD_COLORS_FILE, COLORS_FILE);
                    viaStyle.LOGGER.info("[viaStyle] Migrated nickcolors.json from viamod/ folder.");
                    return;
                } catch (IOException e) {
                    viaStyle.LOGGER.warn("[viaStyle] Failed to migrate nickcolors.json from viamod/: {}", e.getMessage());
                }
            }

            // Try viamod-nickcolors.json (pre-rename flat file)
            if (Files.exists(OLD_VIAMOD_FLAT_COLORS)) {
                try {
                    Files.move(OLD_VIAMOD_FLAT_COLORS, COLORS_FILE);
                    viaStyle.LOGGER.info("[viaStyle] Migrated viamod-nickcolors.json to viaStyle/ folder.");
                    return;
                } catch (IOException e) {
                    viaStyle.LOGGER.warn("[viaStyle] Failed to migrate viamod-nickcolors.json: {}", e.getMessage());
                }
            }

            // Try viaStyle-nickcolors.json (flat file)
            if (Files.exists(OLD_COLORS_FILE)) {
                try {
                    Files.move(OLD_COLORS_FILE, COLORS_FILE);
                    viaStyle.LOGGER.info("[viaStyle] Migrated nickcolors.json to viaStyle/ folder.");
                } catch (IOException e) {
                    viaStyle.LOGGER.warn("[viaStyle] Failed to migrate nickcolors.json: {}", e.getMessage());
                }
            }
        }
    }

    private static void initLpEnumeration() {
        try {
            Class<?> cachedPermClass = Class.forName(
                    "net.luckperms.api.cacheddata.CachedPermissionData");
            getPermissionMapMethod = cachedPermClass.getMethod("getPermissionMap");
            lpEnumerationEnabled = true;
            viaStyle.LOGGER.info("[viaStyle] NickColor: LuckPerms permission enumeration enabled.");
        } catch (Throwable ignored) {
            viaStyle.LOGGER.info("[viaStyle] NickColor: LuckPerms enumeration unavailable — using file overrides only.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the colour spec for a player, or {@code null} if none set.
     * Checks LuckPerms first, then file overrides.
     */
    public static String getColorSpec(UUID uuid) {
        String cached = cache.get(uuid);
        if (cached != null) return cached;

        String spec = resolveFromLp(uuid);
        if (spec == null) spec = fileOverrides.get(uuid);
        if (spec != null) cache.put(uuid, spec);
        return spec;
    }

    /**
     * Returns a styled {@link Text} for the player's name, or {@code null}
     * if no nick colour is configured.
     */
    public static MutableText getColoredName(ServerPlayerEntity player) {
        if (!viaStyle.CONFIG.nickColorEnabled) return null;
        String spec = getColorSpec(player.getUuid());
        if (spec == null) return null;
        return MiniMessageParser.colorize(player.getName().getString(), spec);
    }

    /**
     * Returns the primary {@link TextColor} for a player (solid colour or
     * first gradient stop), or {@code null} if none set.
     */
    public static TextColor getPrimaryColor(UUID uuid) {
        if (!viaStyle.CONFIG.nickColorEnabled) return null;
        String spec = getColorSpec(uuid);
        return spec != null ? MiniMessageParser.primaryColor(spec) : null;
    }

    /**
     * Sets a manual colour override for a player (saved to file).
     */
    public static void setOverride(UUID uuid, String colorSpec) {
        fileOverrides.put(uuid, colorSpec);
        cache.remove(uuid);
        saveFile();
    }

    /**
     * Removes a manual colour override.
     */
    public static void removeOverride(UUID uuid) {
        fileOverrides.remove(uuid);
        cache.remove(uuid);
        saveFile();
    }

    /**
     * Invalidates the cache for a player.  Call this when LP permissions
     * might have changed (e.g. on join, on a reload command).
     */
    public static void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Invalidates the entire cache and reloads file overrides.
     */
    public static void reload() {
        cache.clear();
        loadFile();
    }

    /**
     * Refreshes colours for all online players and updates tab/nametag.
     */
    public static void refreshAll(MinecraftServer server) {
        cache.clear();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            // Re-resolve will happen lazily on next getColorSpec() call
            TabListManager.updatePlayer(p);
            NametagManager.updatePlayer(p);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  LuckPerms permission scanning
    // ═══════════════════════════════════════════════════════════════════════

    /** Current permission prefix. */
    private static final String PERM_PREFIX = "viastyle.nickcolor.";
    /** Legacy prefix kept for backward-compatibility with existing LP setups. */
    private static final String LEGACY_PERM_PREFIX = "viamod.nickcolor.";

    @SuppressWarnings("unchecked")
    private static String resolveFromLp(UUID uuid) {
        if (!LuckPermsHelper.isAvailable()) return null;

        // ── Priority 1: LP meta system ──────────────────────────────────────────
        // /lp user <name> meta set nickcolor <spec>
        // /lp group <group> meta set nickcolor <spec>
        // LP resolves meta with proper priority: player > child group > parent group.
        // This is the recommended way to configure nick colours.
        String metaSpec = LuckPermsHelper.getMetaValue(uuid, "nickcolor");
        if (metaSpec != null && !metaSpec.isBlank()) return metaSpec;

        // ── Priority 2: permission node scanning (legacy) ──────────────────────
        // /lp user <name> permission set viastyle.nickcolor.<spec> true
        // NOTE: getPermissionMap() includes inherited permissions, so if a player's
        // group AND its parent group both have viastyle.nickcolor.* nodes, the result
        // is non-deterministic. Use LP meta (above) for reliable per-group colours.
        if (!lpEnumerationEnabled) return null;
        try {
            // Walk the LP reflection chain to get CachedPermissionData
            Object api         = LuckPermsHelper.getApi();
            if (api == null) return null;
            Object userManager = LuckPermsHelper.getUserManager(api);
            Object user        = LuckPermsHelper.getUser(userManager, uuid);
            if (user == null) return null;
            Object cachedData  = LuckPermsHelper.getCachedData(user);
            Object permData    = LuckPermsHelper.getPermissionData(cachedData);
            if (permData == null) return null;

            Map<String, Boolean> permMap = (Map<String, Boolean>)
                    getPermissionMapMethod.invoke(permData);

            String legacySpec = null;
            for (var entry : permMap.entrySet()) {
                if (!Boolean.TRUE.equals(entry.getValue())) continue;
                String key = entry.getKey();
                // Prefer new prefix — return immediately if found
                if (key.startsWith(PERM_PREFIX)) {
                    return key.substring(PERM_PREFIX.length());
                }
                // Fall back to legacy viamod.nickcolor.* (backward compat)
                if (legacySpec == null && key.startsWith(LEGACY_PERM_PREFIX)) {
                    legacySpec = key.substring(LEGACY_PERM_PREFIX.length());
                }
            }
            if (legacySpec != null) {
                viaStyle.LOGGER.debug("[viaStyle] NickColor: using legacy LP perm prefix for {}",
                        uuid);
            }
            return legacySpec;
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] NickColor LP scan error: {}", t.getMessage());
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  File I/O  (config/viaStyle-nickcolors.json)
    // ═══════════════════════════════════════════════════════════════════════

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static void loadFile() {
        fileOverrides.clear();
        if (!Files.exists(COLORS_FILE)) return;
        try {
            String json = Files.readString(COLORS_FILE);
            Map<String, String> map = GSON.fromJson(json, MAP_TYPE);
            if (map != null) {
                map.forEach((k, v) -> {
                    try {
                        fileOverrides.put(UUID.fromString(k), v);
                    } catch (IllegalArgumentException ignored) {}
                });
            }
            viaStyle.LOGGER.info("[viaStyle] Loaded {} nick-colour overrides from file.", fileOverrides.size());
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to load nick-colour file: {}", e.getMessage());
        }
    }

    private static void saveFile() {
        try {
            Map<String, String> map = new LinkedHashMap<>();
            fileOverrides.forEach((k, v) -> map.put(k.toString(), v));
            Files.createDirectories(COLORS_FILE.getParent());
            Files.writeString(COLORS_FILE, GSON.toJson(map));
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to save nick-colour file: {}", e.getMessage());
        }
    }
}
