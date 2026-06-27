package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
