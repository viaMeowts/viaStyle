package com.viameowts.viastyle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Optional bridge to TextPlaceholderAPI (placeholder-api mod).
 *
 * Usage:
 * <pre>
 *   // During mod init:
 *   PlaceholderHelper.init();
 *
 *   // To process a Text (replaces %placeholders% if PAPI is available):
 *   Text result = PlaceholderHelper.process(myText, player);
 * </pre>
 *
 * The actual PAPI classes are only loaded by the JVM when placeholder-api is present,
 * because {@link PapiPlaceholderProvider} is only instantiated inside {@link #init()}
 * after the mod-present check, and that call is wrapped in a try-catch.
 */
public final class PlaceholderHelper {

    private static PlaceholderProvider provider = null;
    /** Cached server reference — set from TabListManager.onTick so server-level placeholders
     *  (%server:max_players%, %server:online%, etc.) resolve even when player is null. */
    private static net.minecraft.server.MinecraftServer cachedServer = null;

    private PlaceholderHelper() {}

    /** Called every tick from TabListManager to keep a fresh server reference. */
    public static void setServer(net.minecraft.server.MinecraftServer server) {
        cachedServer = server;
    }

    /** Returns the cached MinecraftServer, or {@code null} if not yet set. */
    public static net.minecraft.server.MinecraftServer getServer() {
        return cachedServer;
    }

    /**
     * Must be called once during server mod initialization.
     * Detects whether placeholder-api is installed and, if so, wires up
     * {@link PapiPlaceholderProvider}.
     */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            viaStyle.LOGGER.info("[viaStyle] TextPlaceholderAPI not found => PAPI support disabled.");
            return;
        }
        try {
            provider = new PapiPlaceholderProvider();
            viaStyle.LOGGER.info("[viaStyle] TextPlaceholderAPI detected => PAPI support enabled!");
            // Register custom viaStyle placeholders (e.g. %viastyle:online%)
            PapiPlaceholderProvider.registerCustomPlaceholders();
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to initialise TextPlaceholderAPI: {}", t.getMessage());
        }
    }

    /** Returns {@code true} when PAPI is installed AND the config toggle is on. */
    public static boolean isAvailable() {
        return provider != null && viaStyle.CONFIG != null && viaStyle.CONFIG.usePlaceholderApi;
    }

    /**
     * Processes {@code %namespace:key%} placeholder patterns in the given text
     * for the specified player. Returns the text unchanged when PAPI is unavailable
     * or disabled in the config.
     */
    public static Text process(Text text, ServerPlayerEntity player) {
        if (!isAvailable()) return text;
        try {
            return provider.parse(text, player);
        } catch (Throwable t) {
            viaStyle.LOGGER.error("[viaStyle] PAPI processing error: {}", t.getMessage());
            return text;
        }
    }

    /**
     * Parses a raw format string using the full Patbox Simplified Text Format engine
     * plus PAPI placeholder resolution — all in one step.
     *
     * <p>Supports: {@code <dark_green>}, {@code <gradient:#f00:#0f0>text</gradient>},
     * {@code <bold>}, {@code <shadow:#000>}, {@code &a}, {@code #RRGGBB},
     * {@code %player:displayname%}, {@code %server:tps%}, etc.</p>
     *
     * <p>Falls back to the built-in {@link TabListManager#parseLegacyAndHex} parser
     * when PAPI is unavailable or disabled.</p>
     *
     * @param input  raw format string from config
     * @param player player context for placeholder resolution
     *               ({@code null} = format only, no placeholder resolution)
     * @return styled {@link Text}
     */
    public static Text parseFormat(String input, ServerPlayerEntity player) {
        if (input == null || input.isEmpty()) return Text.empty();
        if (isAvailable()) {
            try {
                return provider.parseFormat(input, player);
            } catch (Throwable t) {
                viaStyle.LOGGER.warn("[viaStyle] PAPI parseFormat error: {}", t.getMessage());
            }
        }
        // Fallback: our own legacy + MiniMessage-subset parser
        return TabListManager.parseLegacyAndHex(input);
    }
}
