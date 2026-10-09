package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.viameowts.viapanel.api.ViaPanelApi;
import com.viameowts.viapanel.api.ViaPanelProviders;
import com.viameowts.viastyle.network.ChatChannel;
import com.viameowts.viastyle.network.Profiles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class viaStyle implements ModInitializer {
    public static final String MOD_ID = "viastyle";
    public static final Logger LOGGER = LoggerFactory.getLogger("viaStyle");

    private static final Gson GSON = new Gson();
    private static final Path PM_SOUND_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle").resolve("pm-sound.json");

    /** Loaded from config/viaStyle.toml — use CONFIG.localChatRadius instead of hard-coded constants. */
    public static ViaStyleConfig CONFIG;

    /** Per-player default chat channel (no trigger typed). Missing = config default_channel. */
    public static final Map<UUID, ChatChannel> playerChannel = new ConcurrentHashMap<>();
    /** Players who have disabled their incoming PM sound via /msound. */
    public static final Set<UUID> playerPmSoundDisabled = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing viaStyle!");
        CONFIG = ViaStyleConfig.load();
        Lang.initialize();
        if (CONFIG.defaultLanguage != null && !CONFIG.defaultLanguage.isBlank()) {
            Lang.setLang(CONFIG.defaultLanguage);
        }
        if (CONFIG.applyLocalizedPlaceholderDefaults(Lang.getCurrentLang())) {
            CONFIG.save();
        }

        // Optional integrations — each helper safely detects its mod via FabricLoader
        PlaceholderHelper.init();
        BanHammerHelper.init();
        LuckPermsHelper.init();
        MeridianaAudit.check("luckperms", "Права: LuckPerms подключён к viaStyle", () -> {
            boolean installed = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms");
            if (!installed) {
                return java.util.List.of("WARN: LuckPerms не установлен: префиксы, группы и узлы viastyle.* не работают, решает только уровень оператора");
            }
            if (CONFIG != null && !CONFIG.useLuckPerms) {
                return java.util.List.of("WARN: LuckPerms стоит, но в viaStyle.toml выключен ([integrations] luckperms = false): узлы viastyle.* игнорируются");
            }
            return java.util.List.of("LuckPerms подключён: узлы viastyle.* решает он");
        });
        BlockBotHelper.init();
        VanishHelper.init();
        CarpetHelper.init();

        // Nick colour system (depends on LuckPermsHelper being initialised first)
        NickColorManager.init();

        // Centralized tick scheduler (replaces per-task event listener registration)
        TickScheduler.init();

        // Load persisted per-player PM sound preferences
        loadPmSoundPrefs();

        // viaPanel admin panel — annotation-based provider
        registerPanel();
    }

    private static void registerPanel() {
        ViaPanelApi.register(ViaPanelProviders
                .builder("viastyle", "viaStyle", CONFIG)
                .panelTitle(Component.literal("viaStyle Admin Panel"))
                .permission(source -> LuckPermsHelper.checkPermission(source, "viastyle.panel", 2))
                .onFieldUpdated((fieldName, source) -> {
                    if (CONFIG == null) return;
                    if ("defaultLanguage".equals(fieldName)) {
                        Lang.setLang(CONFIG.defaultLanguage);
                        if (CONFIG.applyLocalizedPlaceholderDefaults(CONFIG.defaultLanguage)) {
                            CONFIG.save();
                        }
                    }

                    handleJoinLeaveOverrideField(fieldName, source);

                    if (needsVisualRefresh(fieldName)) {
                        var server = source.getServer();
                        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                            NickColorManager.invalidate(player.getUUID());
                        }
                        TabListManager.updateAll(server);
                        NametagManager.updateAll(server);
                    }
                })
                .languageHook(code -> {
                    if (CONFIG == null) return;
                    if (!"ru".equalsIgnoreCase(code) && !"en".equalsIgnoreCase(code)) {
                        return;
                    }
                    CONFIG.defaultLanguage = code.toLowerCase(Locale.ROOT);
                    CONFIG.applyLocalizedPlaceholderDefaults(CONFIG.defaultLanguage);
                    CONFIG.save();
                    Lang.setLang(CONFIG.defaultLanguage);
                })
                .onReload(() -> {
                    if (CONFIG == null) return;
                    Lang.setLang(CONFIG.defaultLanguage);
                    if (CONFIG.applyLocalizedPlaceholderDefaults(CONFIG.defaultLanguage)) {
                        CONFIG.save();
                    }
                    JoinLeaveManager.reload();
                    TabListManager.reloadConfig();
                    NickColorManager.reload();

                    var server = PlaceholderHelper.getServer();
                    if (server != null) {
                        TabListManager.updateAll(server);
                        NametagManager.updateAll(server);
                    }
                })
                .build());
    }

    private static boolean needsVisualRefresh(String fieldName) {
        return fieldName.contains("nickColor") || fieldName.contains("nametag")
                || fieldName.contains("tab") || fieldName.contains("Tab")
                || fieldName.contains("Nametag") || fieldName.contains("NickColor")
                || fieldName.contains("Spectator") || fieldName.contains("spectator")
                || fieldName.contains("afk");
    }

    private static void handleJoinLeaveOverrideField(String fieldName, CommandSourceStack source) {
        if (CONFIG == null) return;

        switch (fieldName) {
            case "joinLeavePanelPlayerTarget" -> {
                UUID uuid = resolvePlayerTargetUuid(source, CONFIG.joinLeavePanelPlayerTarget);
                if (uuid == null) return;
                JoinLeaveManager.MessagePair pair = JoinLeaveManager.getUser(uuid);
                CONFIG.joinLeavePanelPlayerJoinFormat = pair != null && pair.join != null ? pair.join : "";
                CONFIG.joinLeavePanelPlayerLeaveFormat = pair != null && pair.leave != null ? pair.leave : "";
                CONFIG.save();
            }
            case "joinLeavePanelPlayerJoinFormat" -> {
                UUID uuid = resolvePlayerTargetUuid(source, CONFIG.joinLeavePanelPlayerTarget);
                if (uuid == null) return;
                String format = normalizePanelField(CONFIG.joinLeavePanelPlayerJoinFormat);
                if (format == null) JoinLeaveManager.removeUserJoin(uuid);
                else JoinLeaveManager.setUserJoin(uuid, format);
            }
            case "joinLeavePanelPlayerLeaveFormat" -> {
                UUID uuid = resolvePlayerTargetUuid(source, CONFIG.joinLeavePanelPlayerTarget);
                if (uuid == null) return;
                String format = normalizePanelField(CONFIG.joinLeavePanelPlayerLeaveFormat);
                if (format == null) JoinLeaveManager.removeUserLeave(uuid);
                else JoinLeaveManager.setUserLeave(uuid, format);
            }
            case "joinLeavePanelGroupTarget" -> {
                String group = normalizeGroupTarget(CONFIG.joinLeavePanelGroupTarget);
                if (group == null) return;
                JoinLeaveManager.MessagePair pair = JoinLeaveManager.getGroups().get(group);
                CONFIG.joinLeavePanelGroupJoinFormat = pair != null && pair.join != null ? pair.join : "";
                CONFIG.joinLeavePanelGroupLeaveFormat = pair != null && pair.leave != null ? pair.leave : "";
                CONFIG.save();
            }
            case "joinLeavePanelGroupJoinFormat" -> {
                String group = normalizeGroupTarget(CONFIG.joinLeavePanelGroupTarget);
                if (group == null) return;
                String format = normalizePanelField(CONFIG.joinLeavePanelGroupJoinFormat);
                if (format == null) JoinLeaveManager.removeGroupJoin(group);
                else JoinLeaveManager.setGroupJoin(group, format);
            }
            case "joinLeavePanelGroupLeaveFormat" -> {
                String group = normalizeGroupTarget(CONFIG.joinLeavePanelGroupTarget);
                if (group == null) return;
                String format = normalizePanelField(CONFIG.joinLeavePanelGroupLeaveFormat);
                if (format == null) JoinLeaveManager.removeGroupLeave(group);
                else JoinLeaveManager.setGroupLeave(group, format);
            }
            default -> {
            }
        }
    }

    private static UUID resolvePlayerTargetUuid(CommandSourceStack source, String target) {
        String value = normalizePanelField(target);
        if (value == null) return null;

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(value)) {
                return player.getUUID();
            }
        }

        source.sendFailure(Lang.get("joinleave.admin.player_not_found"));
        return null;
    }

    private static String normalizePanelField(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeGroupTarget(String group) {
        String normalized = normalizePanelField(group);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    /** The channel a message without trigger goes to for this player. */
    public static ChatChannel getDefaultChannel(UUID playerUuid) {
        ChatChannel own = playerChannel.get(playerUuid);
        if (own != null) return own;
        ChatChannel configured = CONFIG != null ? ChatChannel.parse(CONFIG.defaultChannel) : null;
        return configured != null ? configured : ChatChannel.LOCAL;
    }

    public static void setDefaultChannel(UUID playerUuid, ChatChannel channel) {
        playerChannel.put(playerUuid, channel);
        Profiles.changed(playerUuid, Profiles.CHANNEL);
    }

    public static boolean isPmSoundEnabled(UUID playerUuid) {
        return !playerPmSoundDisabled.contains(playerUuid);
    }

    /** Toggles PM sound for a player and persists. Returns the new state (true = enabled). */
    public static boolean togglePmSound(UUID playerUuid) {
        boolean enabled;
        if (playerPmSoundDisabled.contains(playerUuid)) {
            playerPmSoundDisabled.remove(playerUuid);
            enabled = true;
        } else {
            playerPmSoundDisabled.add(playerUuid);
            enabled = false;
        }
        savePmSoundPrefs();
        Profiles.changed(playerUuid, Profiles.PM_SOUND_OFF);
        return enabled;
    }

    /** Enables PM sound for a player and persists. */
    public static void enablePmSound(UUID playerUuid) {
        if (!playerPmSoundDisabled.remove(playerUuid)) return;
        savePmSoundPrefs();
        Profiles.changed(playerUuid, Profiles.PM_SOUND_OFF);
    }

    /** Disables PM sound for a player and persists. */
    public static void disablePmSound(UUID playerUuid) {
        if (!playerPmSoundDisabled.add(playerUuid)) return;
        savePmSoundPrefs();
        Profiles.changed(playerUuid, Profiles.PM_SOUND_OFF);
    }

    private static void loadPmSoundPrefs() {
        if (!Files.exists(PM_SOUND_FILE)) return;
        try {
            String json = Files.readString(PM_SOUND_FILE);
            List<String> uuids = GSON.fromJson(json, new TypeToken<List<String>>() {}.getType());
            if (uuids != null) {
                for (String s : uuids) {
                    try {
                        playerPmSoundDisabled.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            LOGGER.info("[viaStyle] Loaded {} PM-sound disabled players.", playerPmSoundDisabled.size());
        } catch (IOException e) {
            LOGGER.warn("[viaStyle] Failed to load pm-sound.json: {}", e.getMessage());
        }
    }

    private static void savePmSoundPrefs() {
        try {
            Files.createDirectories(PM_SOUND_FILE.getParent());
            List<String> uuids = new ArrayList<>();
            for (UUID uuid : playerPmSoundDisabled) {
                uuids.add(uuid.toString());
            }
            Files.writeString(PM_SOUND_FILE, GSON.toJson(uuids));
        } catch (IOException e) {
            LOGGER.warn("[viaStyle] Failed to save pm-sound.json: {}", e.getMessage());
        }
    }
}
