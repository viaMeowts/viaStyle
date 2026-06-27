package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class JoinLeaveManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle").resolve("joinleave-users.json");

    private static Map<String, MessagePair> users = new LinkedHashMap<>();
        private static Map<String, MessagePair> groups = new LinkedHashMap<>();

    private JoinLeaveManager() {}

    public static class MessagePair {
        public String join;
        public String leave;

        public MessagePair() {}

        public MessagePair(String join, String leave) {
            this.join = join;
            this.leave = leave;
        }
    }

    private static class DataFile {
        Map<String, MessagePair> users;
        Map<String, MessagePair> groups;
    }

    public static void load() {
        if (!Files.exists(DATA_PATH)) {
            users = new LinkedHashMap<>();
            groups = new LinkedHashMap<>();
            return;
        }
        try {
            String json = Files.readString(DATA_PATH);
            DataFile data = GSON.fromJson(json, DataFile.class);
            users = data != null && data.users != null ? data.users : new LinkedHashMap<>();
            groups = data != null && data.groups != null ? data.groups : new LinkedHashMap<>();
            viaStyle.LOGGER.info("[viaStyle] Loaded joinleave-users.json ({} users, {} groups).",
                    users.size(), groups.size());
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to load joinleave-users.json: {}", e.getMessage());
            users = new LinkedHashMap<>();
            groups = new LinkedHashMap<>();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(DATA_PATH.getParent());
            DataFile data = new DataFile();
            data.users = users;
            data.groups = groups;
            Files.writeString(DATA_PATH, GSON.toJson(data));
        } catch (IOException e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to save joinleave-users.json: {}", e.getMessage());
        }
    }

    public static void reload() {
        load();
    }

    public static String resolveJoinFormat(UUID uuid, String defaultFormat) {
        if (!isPerPlayerEnabled()) return defaultFormat;
        MessagePair pair = users.get(uuid.toString());
        if (pair != null && pair.join != null && !pair.join.isBlank()) {
            return pair.join;
        }
        String groupFormat = resolveGroupFormat(uuid, true);
        if (groupFormat != null) return groupFormat;
        return defaultFormat;
    }

    public static String resolveLeaveFormat(UUID uuid, String defaultFormat) {
        if (!isPerPlayerEnabled()) return defaultFormat;
        MessagePair pair = users.get(uuid.toString());
        if (pair != null && pair.leave != null && !pair.leave.isBlank()) {
            return pair.leave;
        }
        String groupFormat = resolveGroupFormat(uuid, false);
        if (groupFormat != null) return groupFormat;
        return defaultFormat;
    }

    public static String resolveFirstJoinFormat(UUID uuid, String defaultFormat) {
        if (!isPerPlayerEnabled()) return defaultFormat;
        MessagePair pair = users.get(uuid.toString());
        if (pair != null && pair.join != null && !pair.join.isBlank()) {
            return pair.join;
        }
        String groupFormat = resolveGroupFormat(uuid, true);
        if (groupFormat != null) return groupFormat;
        return defaultFormat;
    }

    private static String resolveGroupFormat(UUID uuid, boolean join) {
        if (groups.isEmpty()) return null;

        String primary = normalizeGroupName(LuckPermsHelper.getPrimaryGroup(uuid));
        if (primary != null) {
            MessagePair primaryPair = groups.get(primary);
            if (primaryPair != null) {
                String format = join ? primaryPair.join : primaryPair.leave;
                if (format != null && !format.isBlank()) return format;
            }
        }

        List<String> playerGroups = LuckPermsHelper.getGroupNames(uuid);
        for (String group : playerGroups) {
            String normalized = normalizeGroupName(group);
            if (normalized == null) continue;
            if (normalized.equals(primary)) continue;
            MessagePair pair = groups.get(normalized);
            if (pair != null) {
                String format = join ? pair.join : pair.leave;
                if (format != null && !format.isBlank()) return format;
            }
        }

        return null;
    }

    public static MessagePair getUser(UUID uuid) {
        return users.get(uuid.toString());
    }

    public static void setUserJoin(UUID uuid, String format) {
        MessagePair pair = users.computeIfAbsent(uuid.toString(), key -> new MessagePair());
        pair.join = format;
        save();
    }

    public static void setUserLeave(UUID uuid, String format) {
        MessagePair pair = users.computeIfAbsent(uuid.toString(), key -> new MessagePair());
        pair.leave = format;
        save();
    }

    public static void removeUserJoin(UUID uuid) {
        MessagePair pair = users.get(uuid.toString());
        if (pair == null) return;
        pair.join = null;
        cleanupEmpty(uuid.toString(), pair);
        save();
    }

    public static void removeUserLeave(UUID uuid) {
        MessagePair pair = users.get(uuid.toString());
        if (pair == null) return;
        pair.leave = null;
        cleanupEmpty(uuid.toString(), pair);
        save();
    }

    public static void removeUser(UUID uuid) {
        users.remove(uuid.toString());
        save();
    }

    public static void setGroupJoin(String groupName, String format) {
        String key = normalizeGroupName(groupName);
        if (key == null) return;
        MessagePair pair = groups.computeIfAbsent(key, k -> new MessagePair());
        pair.join = format;
        save();
    }

    public static void setGroupLeave(String groupName, String format) {
        String key = normalizeGroupName(groupName);
        if (key == null) return;
        MessagePair pair = groups.computeIfAbsent(key, k -> new MessagePair());
        pair.leave = format;
        save();
    }

    public static void removeGroupJoin(String groupName) {
        String key = normalizeGroupName(groupName);
        if (key == null) return;
        MessagePair pair = groups.get(key);
        if (pair == null) return;
        pair.join = null;
        cleanupGroupEmpty(key, pair);
        save();
    }

    public static void removeGroupLeave(String groupName) {
        String key = normalizeGroupName(groupName);
        if (key == null) return;
        MessagePair pair = groups.get(key);
        if (pair == null) return;
        pair.leave = null;
        cleanupGroupEmpty(key, pair);
        save();
    }

    public static void removeGroup(String groupName) {
        String key = normalizeGroupName(groupName);
        if (key == null) return;
        groups.remove(key);
        save();
    }

    public static Map<String, MessagePair> getUsers() {
        return users;
    }

    public static Map<String, MessagePair> getGroups() {
        return groups;
    }

    private static void cleanupEmpty(String key, MessagePair pair) {
        boolean noJoin = pair.join == null || pair.join.isBlank();
        boolean noLeave = pair.leave == null || pair.leave.isBlank();
        if (noJoin && noLeave) {
            users.remove(key);
        }
    }

    private static void cleanupGroupEmpty(String key, MessagePair pair) {
        boolean noJoin = pair.join == null || pair.join.isBlank();
        boolean noLeave = pair.leave == null || pair.leave.isBlank();
        if (noJoin && noLeave) {
            groups.remove(key);
        }
    }

    private static String normalizeGroupName(String groupName) {
        if (groupName == null) return null;
        String normalized = groupName.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean isPerPlayerEnabled() {
        return viaStyle.CONFIG != null && viaStyle.CONFIG.joinLeavePerPlayerEnabled;
    }
}
