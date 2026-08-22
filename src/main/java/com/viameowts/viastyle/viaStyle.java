package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.viameowts.viapanel.api.ViaPanelApi;
import com.viameowts.viapanel.api.ViaPanelProviders;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

    public static final Map<UUID, Boolean> playerChatModePref = new ConcurrentHashMap<>();
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
                .panelTitle(Text.literal("viaStyle Admin Panel"))
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
                        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                            NickColorManager.invalidate(player.getUuid());
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

    private static void handleJoinLeaveOverrideField(String fieldName, ServerCommandSource source) {
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

    private static UUID resolvePlayerTargetUuid(ServerCommandSource source, String target) {
        String value = normalizePanelField(target);
        if (value == null) return null;

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (player.getName().getString().equalsIgnoreCase(value)) {
                return player.getUuid();
            }
        }

        source.sendError(Lang.get("joinleave.admin.player_not_found"));
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

    public static boolean getPlayerPrefersPrefixForGlobal(UUID playerUuid) {
        return playerChatModePref.getOrDefault(playerUuid, true);
    }

    public static boolean isPmSoundEnabled(UUID playerUuid) {
        return !playerPmSoundDisabled.contains(playerUuid);
    }

    /** Toggles PM sound for a player and persists. Returns the new state (true = enabled). */
    public static boolean togglePmSound(UUID playerUuid) {
        if (playerPmSoundDisabled.contains(playerUuid)) {
            playerPmSoundDisabled.remove(playerUuid);
            savePmSoundPrefs();
            return true;
        } else {
            playerPmSoundDisabled.add(playerUuid);
            savePmSoundPrefs();
            return false;
        }
    }

    /** Enables PM sound for a player and persists. */
    public static void enablePmSound(UUID playerUuid) {
        playerPmSoundDisabled.remove(playerUuid);
        savePmSoundPrefs();
    }

    /** Disables PM sound for a player and persists. */
    public static void disablePmSound(UUID playerUuid) {
        playerPmSoundDisabled.add(playerUuid);
        savePmSoundPrefs();
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
