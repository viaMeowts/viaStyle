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
 * SocialSpy — lets staff see messages from other players in selected channels.
 *
 * <p>Channels: {@code local}, {@code global}, {@code staff}, {@code pm}.</p>
 * <p>Each admin can toggle individually which channels they want to spy on.</p>
 * <p>State is persisted to {@code config/viaStyle/socialspy.json}.</p>
 */
public final class SocialSpyManager {

    /** Available spy channels. */
    public enum Channel {
        LOCAL, GLOBAL, STAFF, PM;

        public static Channel fromString(String s) {
            try { return valueOf(s.toUpperCase()); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle").resolve("socialspy.json");

    /** Pre-rename location. */
    private static final Path OLD_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viamod").resolve("socialspy.json");

    /** UUID → set of enabled channels. Absent = spy off. */
    private static final Map<UUID, Set<Channel>> spies = new ConcurrentHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();

    private SocialSpyManager() {}

    // ═══════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════

    public static void init() {
        load();
    }

    /** Returns true if the player has SocialSpy enabled for the given channel. */
    public static boolean isSpying(UUID uuid, Channel channel) {
        Set<Channel> set = spies.get(uuid);
        return set != null && set.contains(channel);
    }

    /** Returns true if the player has SocialSpy enabled for ANY channel. */
    public static boolean isActive(UUID uuid) {
        Set<Channel> set = spies.get(uuid);
        return set != null && !set.isEmpty();
    }

    /** Returns the set of channels a player is spying on (unmodifiable). */
    public static Set<Channel> getChannels(UUID uuid) {
        Set<Channel> set = spies.get(uuid);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    /** Enables spy on all channels. */
    public static void enableAll(UUID uuid) {
        Set<Channel> set = spies.computeIfAbsent(uuid, k -> EnumSet.noneOf(Channel.class));
        set.addAll(EnumSet.allOf(Channel.class));
        save();
    }

    /** Disables spy on all channels. */
    public static void disableAll(UUID uuid) {
        spies.remove(uuid);
        save();
    }

    /** Toggles a specific channel. Returns true if now ON. */
    public static boolean toggleChannel(UUID uuid, Channel channel) {
        Set<Channel> set = spies.computeIfAbsent(uuid, k -> EnumSet.noneOf(Channel.class));
        boolean nowOn;
        if (set.contains(channel)) {
            set.remove(channel);
            nowOn = false;
            if (set.isEmpty()) spies.remove(uuid);
        } else {
            set.add(channel);
            nowOn = true;
        }
        save();
        return nowOn;
    }

    /** Enables or disables a specific channel. */
    public static void setChannel(UUID uuid, Channel channel, boolean on) {
        if (on) {
            Set<Channel> set = spies.computeIfAbsent(uuid, k -> EnumSet.noneOf(Channel.class));
            set.add(channel);
        } else {
            Set<Channel> set = spies.get(uuid);
            if (set != null) {
                set.remove(channel);
                if (set.isEmpty()) spies.remove(uuid);
            }
        }
        save();
    }

    /** Returns all UUIDs that have spy enabled for the given channel. */
    public static Set<UUID> getSpiesForChannel(Channel channel) {
        Set<UUID> result = new HashSet<>();
        spies.forEach((uuid, channels) -> {
            if (channels.contains(channel)) {
                result.add(uuid);
            }
        });
        return result;
    }

    /** Cleans up when a player disconnects (state persists). */
    public static void onDisconnect(UUID uuid) {
        // State is persistent — nothing to clean.
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  File I/O
    // ═══════════════════════════════════════════════════════════════════════

    private static void load() {
        spies.clear();
        // Migrate from old viamod/ directory if needed
        if (!Files.exists(FILE) && Files.exists(OLD_FILE)) {
            try {
                Files.createDirectories(FILE.getParent());
                Files.copy(OLD_FILE, FILE);
                viaStyle.LOGGER.info("[viaStyle] Migrated socialspy.json from viamod/ folder.");
            } catch (IOException e) {
                viaStyle.LOGGER.warn("[viaStyle] Failed to migrate socialspy.json: {}", e.getMessage());
            }
        }
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE);
            Map<String, List<String>> raw = GSON.fromJson(json, MAP_TYPE);
            if (raw != null) {
                raw.forEach((key, list) -> {
                    try {
                        UUID uuid = UUID.fromString(key);
                        Set<Channel> set = EnumSet.noneOf(Channel.class);
                        for (String s : list) {
                            Channel ch = Channel.fromString(s);
                            if (ch != null) set.add(ch);
                        }
                        if (!set.isEmpty()) spies.put(uuid, set);
                    } catch (IllegalArgumentException ignored) {}
                });
            }
            viaStyle.LOGGER.info("[viaStyle] Loaded {} SocialSpy profiles.", spies.size());
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to load socialspy: {}", e.getMessage());
        }
    }

    private static void save() {
        try {
            Map<String, List<String>> raw = new LinkedHashMap<>();
            spies.forEach((uuid, channels) -> {
                List<String> list = new ArrayList<>();
                channels.forEach(ch -> list.add(ch.name()));
                raw.put(uuid.toString(), list);
            });
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(raw));
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to save socialspy: {}", e.getMessage());
        }
    }
}
