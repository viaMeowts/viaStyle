package com.viameowts.viastyle;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class viaStyle implements ModInitializer {
    public static final String MOD_ID = "viastyle";
    public static final Logger LOGGER = LoggerFactory.getLogger("viaStyle");

    /** Loaded from config/viaStyle.toml — use CONFIG.localChatRadius instead of hard-coded constants. */
    public static ViaStyleConfig CONFIG;

    public static final Map<UUID, Boolean> playerChatModePref = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing viaStyle!");
        CONFIG = ViaStyleConfig.load();
        Lang.initialize();
        if (CONFIG.defaultLanguage != null && !CONFIG.defaultLanguage.isBlank()) {
            Lang.setLang(CONFIG.defaultLanguage);
        }

        // Optional integrations — each helper safely detects its mod via FabricLoader
        PlaceholderHelper.init();
        BanHammerHelper.init();
        LuckPermsHelper.init();
        BlockBotHelper.init();
        ScarpetHelper.init();
        VanishHelper.init();
        CarpetHelper.init();

        // Nick colour system (depends on LuckPermsHelper being initialised first)
        NickColorManager.init();

        // Centralized tick scheduler (replaces per-task event listener registration)
        TickScheduler.init();
    }

    public static boolean getPlayerPrefersPrefixForGlobal(UUID playerUuid) {
        return playerChatModePref.getOrDefault(playerUuid, true);
    }
}
