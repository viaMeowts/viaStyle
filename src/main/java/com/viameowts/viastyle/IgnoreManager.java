package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player ignore lists.
 *
 * <p>Players can {@code /ignore <player>} to block private messages.
 * Ignore lists are persisted to {@code config/viaStyle/ignores.json}.</p>
 */
public final class IgnoreManager {

    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle").resolve("ignores.json");

    /** Pre-rename location. */
    private static final Path OLD_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viamod").resolve("ignores.json");

    /** UUID of ignorer → set of ignored UUIDs. */
    private static final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();

    private IgnoreManager() {}

    // ═══════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════

    public static void init() {
        load();
    }

    /** Returns {@code true} if {@code ignorer} is ignoring {@code ignored}. */
    public static boolean isIgnoring(UUID ignorer, UUID ignored) {
        Set<UUID> set = ignores.get(ignorer);
        return set != null && set.contains(ignored);
    }

    /** Adds {@code ignored} to {@code ignorer}'s ignore list. Returns true if newly added. */
    public static boolean add(UUID ignorer, UUID ignored) {
        Set<UUID> set = ignores.computeIfAbsent(ignorer, k -> ConcurrentHashMap.newKeySet());
        boolean added = set.add(ignored);
        if (added) save();
        return added;
    }

    /** Removes {@code ignored} from {@code ignorer}'s ignore list. Returns true if was present. */
    public static boolean remove(UUID ignorer, UUID ignored) {
        Set<UUID> set = ignores.get(ignorer);
        if (set == null) return false;
        boolean removed = set.remove(ignored);
        if (removed) {
            if (set.isEmpty()) ignores.remove(ignorer);
            save();
        }
        return removed;
    }

    /** Returns the set of UUIDs that {@code ignorer} is ignoring (unmodifiable). */
    public static Set<UUID> getIgnored(UUID ignorer) {
        Set<UUID> set = ignores.get(ignorer);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    /** Cleans up when a player disconnects. Does NOT remove their ignores — they persist. */
    public static void onDisconnect(UUID uuid) {
        // Ignores are persistent — nothing to clean on disconnect.
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  File I/O
    // ═══════════════════════════════════════════════════════════════════════

    private static void load() {
        ignores.clear();
        // Migrate from old viamod/ directory if needed
        if (!Files.exists(FILE) && Files.exists(OLD_FILE)) {
            try {
                Files.createDirectories(FILE.getParent());
                Files.copy(OLD_FILE, FILE);
                viaStyle.LOGGER.info("[viaStyle] Migrated ignores.json from viamod/ folder.");
            } catch (IOException e) {
                viaStyle.LOGGER.warn("[viaStyle] Failed to migrate ignores.json: {}", e.getMessage());
            }
        }
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE);
            Map<String, List<String>> raw = GSON.fromJson(json, MAP_TYPE);
            if (raw != null) {
                raw.forEach((key, list) -> {
                    try {
                        UUID ignorer = UUID.fromString(key);
                        Set<UUID> set = ConcurrentHashMap.newKeySet();
                        for (String s : list) {
                            try { set.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                        }
                        if (!set.isEmpty()) ignores.put(ignorer, set);
                    } catch (IllegalArgumentException ignored) {}
                });
            }
            viaStyle.LOGGER.info("[viaStyle] Loaded {} ignore lists from file.", ignores.size());
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to load ignores: {}", e.getMessage());
        }
    }

    private static void save() {
        try {
            Map<String, List<String>> raw = new LinkedHashMap<>();
            ignores.forEach((key, set) -> {
                List<String> list = new ArrayList<>();
                set.forEach(u -> list.add(u.toString()));
                raw.put(key.toString(), list);
            });
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(raw));
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to save ignores: {}", e.getMessage());
        }
    }
}
