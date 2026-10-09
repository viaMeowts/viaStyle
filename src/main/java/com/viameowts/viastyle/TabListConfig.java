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
 *   <li>{@code {server}} / {@code {server_id}} — display name and id of this server</li>
 *   <li>{@code {lp_prefix}} / {@code {lp_suffix}} — LuckPerms prefix and suffix ({@code &c}, {@code &#rrggbb} work)</li>
 *   <li>{@code {lp_group}} / {@code {lp_group_name}} — primary group and its display name</li>
 *   <li>{@code {lp_weight}} — weight used for sorting</li>
 *   <li>{@code {lp_meta:key}} — any LuckPerms meta value</li>
 * </ul>
 *
 * <h3>Tags</h3>
 * <p>Every tag with a body needs a closing tag ({@code <gradient:#aaa:#bbb>text</gradient>}).
 * An unclosed gradient runs to the end of the line.</p>
 *
 * <h3>Color formatting</h3>
 * <ul>
 *   <li>{@code <#RRGGBB>} / {@code <color:#RRGGBB>} — MiniMessage hex colour tags</li>
 *   <li>{@code #RRGGBB} — hex colour (e.g. {@code #ff5555})</li>
 *   <li>{@code gradient:#RRGGBB:#RRGGBB:text} — gradient text</li>
 * </ul>
 */
public class TabListConfig {

    /** Version of the default layout this file was written with. Used to upgrade old defaults. */
    public int configVersion = CURRENT_VERSION;

    private static final int CURRENT_VERSION = 2;

    /** Whether tab list customisation is enabled at all. */
    public boolean enabled = true;

    /** How often to update the tab list, in ticks (20 = once per second). -1 = only on join/change. */
    public int updateIntervalTicks = 20;

    /** Whether to modify player display names in the tab list. */
    public boolean modifyPlayerName = true;

    /**
     * Format for each player entry in the tab list.
     * Placeholders: {player}, {name}, {lp_prefix}, {lp_suffix}, {lp_group}, {lp_group_name},
     * {lp_weight}, {lp_meta:key}, {ping}, {afk_suffix}
     */
    public String playerNameFormat = "{lp_prefix}{player}{lp_suffix}";

    /**
     * Per-group entry format, keyed by LuckPerms primary group name (lower case). Falls back to
     * {@link #playerNameFormat}. Example: {@code "admin": "<red>[A] </red>{player}"}.
     */
    public java.util.Map<String, String> groupFormats = new java.util.LinkedHashMap<>();

    /** Format for the group of the player, or the general one. */
    public String formatFor(String group) {
        if (group != null && !group.isEmpty() && groupFormats != null) {
            String format = groupFormats.get(group.toLowerCase());
            if (format != null && !format.isBlank()) return format;
        }
        return playerNameFormat;
    }

    private static final List<String> DEFAULT_HEADER = List.of(
            "",
            "<gradient:#5bc8f5:#ffffff:#a8ff78>✦ viaStyle ✦</gradient>",
            "<gradient:#5bc8f5:#a8ff78>{server}</gradient>",
            "",
            "<dark_aqua>Players: <gradient:#a8ff78:#5bc8f5>{online}/{max}</gradient>  <dark_aqua>TPS: {tps}",
            ""
    );

    private static final List<String> DEFAULT_FOOTER = List.of(
            "",
            "<gradient:#5bc8f5:#a8ff78>━━━━━━━━━━━━━━━━━━━━━━━━</gradient>",
            "<gray>Ping: <gradient:#a8ff78:#5bc8f5>{ping}ms</gradient>  <dark_gray>•  <gray>MSPT: {mspt}",
            ""
    );

    /** The layout shipped up to 3.0.0: gradient tags without a closing tag and a hard-coded mode. */
    private static final List<String> OLD_DEFAULT_HEADER = List.of(
            "",
            "<gr:#5bc8f5:#ffffff:#a8ff78>    ✦ viaStyle ✦    ",
            "<gr:#5bc8f5:#a8ff78>┃ Server Network ┃",
            "",
            "<dark_aqua>Players: <gr:#a8ff78:#5bc8f5>{online}/{max}  <dark_aqua>TPS: <gr:#a8ff78:#5bc8f5>{tps}",
            ""
    );

    private static final List<String> OLD_DEFAULT_FOOTER = List.of(
            "",
            "<gr:#5bc8f5:#a8ff78>                                        ",
            "",
            "<gray>Ping: <gr:#a8ff78:#5bc8f5>{ping}ms<dark_gray>  •  <gray>Mode: <aqua>survival",
            ""
    );

    /** Header lines — each element is one line. Supports colour codes and placeholders. */
    public List<String> header = DEFAULT_HEADER;

    /** Footer lines — each element is one line. Supports colour codes and placeholders. */
    public List<String> footer = DEFAULT_FOOTER;

    /** Whether to show header and footer. */
    public boolean showHeader = true;
    public boolean showFooter = true;

    // ══════════════════════════════════════════════════════════════════════
    //  I/O
    // ══════════════════════════════════════════════════════════════════════

    private static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("viaStyle");
    }

    private static Path configPath() {
        return configDir().resolve("tablist.json");
    }

    /** Pre-rename location. */
    private static Path oldConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("viamod").resolve("tablist.json");
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * Loads the tab list config, creating a default if it doesn't exist.
     */
    public static TabListConfig load() {
        try {
            Files.createDirectories(configDir());
        } catch (IOException ignored) {}

        if (!Files.exists(configPath())) {
            // Migrate from old viamod/ directory if it exists
            if (Files.exists(oldConfigPath())) {
                try {
                    Files.copy(oldConfigPath(), configPath());
                    viaStyle.LOGGER.info("[viaStyle] Migrated tablist.json from viamod/ folder.");
                } catch (IOException e) {
                    viaStyle.LOGGER.warn("[viaStyle] Failed to migrate tablist.json: {}", e.getMessage());
                }
            }
        }

        if (!Files.exists(configPath())) {
            TabListConfig defaults = new TabListConfig();
            defaults.save();
            viaStyle.LOGGER.info("[viaStyle] Created default tablist config: {}", configPath());
            return defaults;
        }

        try {
            String json = Files.readString(configPath());
            TabListConfig config = GSON.fromJson(json, TabListConfig.class);
            if (config == null) config = new TabListConfig();
            // Files from before 3.1.0 have no version field and Gson would leave the default.
            if (!json.contains("\"configVersion\"")) config.configVersion = 1;
            config.upgrade();
            config.save(); // re-save to fill in new fields
            viaStyle.LOGGER.info("[viaStyle] Tab list config loaded: {}", configPath());
            return config;
        } catch (Exception e) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to load tablist.json: {} — using defaults.", e.getMessage());
            return new TabListConfig();
        }
    }

    /**
     * Brings a file written by an older version up to date. Only layouts that are still the
     * untouched old defaults are replaced; anything the admin edited stays as it is.
     */
    private void upgrade() {
        if (groupFormats == null) groupFormats = new java.util.LinkedHashMap<>();
        if (configVersion >= CURRENT_VERSION) return;
        if (OLD_DEFAULT_HEADER.equals(header)) header = DEFAULT_HEADER;
        if (OLD_DEFAULT_FOOTER.equals(footer)) footer = DEFAULT_FOOTER;
        if ("{lp_prefix}{player}".equals(playerNameFormat)) playerNameFormat = "{lp_prefix}{player}{lp_suffix}";
        configVersion = CURRENT_VERSION;
        viaStyle.LOGGER.info("[viaStyle] tablist.json upgraded to layout version {}.", CURRENT_VERSION);
    }

    /** Saves current settings to disk. */
    public void save() {
        try {
            Files.createDirectories(configDir());
            Files.writeString(configPath(), GSON.toJson(this));
        } catch (IOException e) {
            viaStyle.LOGGER.error("[viaStyle] Failed to save tablist.json: {}", e.getMessage());
        }
    }
}
