package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for the tab list (player list) appearance.
 *
 * <p>File: {@code config/viaStyle/tablist.json}</p>
 *
 * <h3>Placeholders</h3>
 * <ul>
 *   <li>{@code {player}} — player display name (with nick colour if set)</li>
 *   <li>{@code {name}} — raw player name (no nick colour)</li>
 *   <li>{@code {online}} — number of online players</li>
 *   <li>{@code {max}} — max player count</li>
 *   <li>{@code {ping}} — player ping in ms</li>
 *   <li>{@code {tps}} — server TPS</li>
 *   <li>{@code {lp_prefix}} — LuckPerms prefix</li>
 *   <li>{@code {lp_suffix}} — LuckPerms suffix</li>
 * </ul>
 *
 * <h3>Color formatting</h3>
 * <ul>
 *   <li>{@code &6} — legacy Minecraft colour codes (0-9, a-f, l, m, n, o, r)</li>
 *   <li>{@code #RRGGBB} — hex colour (e.g. {@code #ff5555})</li>
 *   <li>{@code gradient:#RRGGBB:#RRGGBB:text} — gradient text</li>
 * </ul>
 */
public class TabListConfig {

    /** Whether tab list customisation is enabled at all. */
    public boolean enabled = true;

    /** How often to update the tab list, in ticks (20 = once per second). -1 = only on join/change. */
    public int updateIntervalTicks = 20;

    /** Whether to modify player display names in the tab list. */
    public boolean modifyPlayerName = true;

    /**
     * Format for each player entry in the tab list.
     * Placeholders: {player}, {name}, {lp_prefix}, {lp_suffix}, {ping}
     */
    public String playerNameFormat = "{lp_prefix}{player}";

    /** Header lines — each element is one line. Supports colour codes and placeholders. */
    public List<String> header = List.of(
            "",
            "<gr:#5bc8f5:#ffffff:#a8ff78>    ✦ viaStyle ✦    </gr>",
            "<gr:#5bc8f5:#a8ff78>┃ Server Network ┃</gr>",
            "",
            "<dark_aqua>Players: <gr:#a8ff78:#5bc8f5>{online}/{max}</gr>  <dark_aqua>TPS: <gr:#a8ff78:#5bc8f5>{tps}</gr>",
            ""
    );

    /** Footer lines — each element is one line. Supports colour codes and placeholders. */
    public List<String> footer = List.of(
            "",
            "<gr:#5bc8f5:#a8ff78>                                        </gr>",
            "",
            "<gray>Ping: <gr:#a8ff78:#5bc8f5>{ping}ms</gr><dark_gray>  •  <gray>Mode: <aqua>survival",
            ""
    );

    /** Whether to show header and footer. */
    public boolean showHeader = true;
    public boolean showFooter = true;

    // ══════════════════════════════════════════════════════════════════════
    //  I/O
    // ══════════════════════════════════════════════════════════════════════

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("tablist.json");

    /** Pre-rename location. */
    private static final Path OLD_CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("viamod").resolve("tablist.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * Loads the tab list config, creating a default if it doesn't exist.
     */
    public static TabListConfig load() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException ignored) {}

        if (!Files.exists(CONFIG_PATH)) {
            // Migrate from old viamod/ directory if it exists
            if (Files.exists(OLD_CONFIG_PATH)) {
                try {
                    Files.copy(OLD_CONFIG_PATH, CONFIG_PATH);
                    viaStyle.LOGGER.info("[viaStyle] Migrated tablist.json from viamod/ folder.");
                } catch (IOException e) {
                    viaStyle.LOGGER.warn("[viaStyle] Failed to migrate tablist.json: {}", e.getMessage());
                }
            }
        }

        if (!Files.exists(CONFIG_PATH)) {
            TabListConfig defaults = new TabListConfig();
            defaults.save();
            viaStyle.LOGGER.info("[viaStyle] Created default tablist config: {}", CONFIG_PATH);
            return defaults;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            TabListConfig config = GSON.fromJson(json, TabListConfig.class);
            if (config == null) config = new TabListConfig();
            config.save(); // re-save to fill in new fields
            viaStyle.LOGGER.info("[viaStyle] Tab list config loaded: {}", CONFIG_PATH);
            return config;
        } catch (Exception e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to load tablist.json: {} — using defaults.", e.getMessage());
            return new TabListConfig();
        }
    }

    /** Saves current settings to disk. */
    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            viaStyle.LOGGER.error("[viaStyle] Failed to save tablist.json: {}", e.getMessage());
        }
    }
}
