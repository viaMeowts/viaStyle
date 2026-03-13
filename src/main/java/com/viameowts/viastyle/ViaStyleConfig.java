package com.viameowts.viastyle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * viaStyle configuration — config/viaStyle.toml
 *
 * Sections: [local], [global], [staff], [timestamp], [integrations], [blockbot], [pm], [nickcolor], [language]
 */
public class ViaStyleConfig {

    // ── Local Chat ───────────────────────────────────────────────────────

    public double  localChatRadius   = 100.0;
    public String  localTrigger      = "";         // empty = local is default, no prefix needed
    public String  localPrefix       = "[L]";
    public String  localPrefixColor  = "GREEN";
    public String  localNameColor    = "GRAY";
    public String  localMessageColor = "GRAY";
    public String  localFormat       = "{timestamp}{prefix} {lp_prefix}{name}: {message}";
    public boolean localNooneHeard        = false;       // show a hint when nobody received the message
    public String  localNooneHeardMessage = "Nobody heard you."; // text shown to sender when no one is in range

    // ── Global Chat ──────────────────────────────────────────────────────

    public String  globalTrigger     = "!";        // prefix to route message to global chat
    public String  globalPrefix       = "[G]";
    public String  globalPrefixColor  = "YELLOW";
    public String  globalNameColor    = "WHITE";
    public String  globalMessageColor = "WHITE";
    public String  globalFormat       = "{timestamp}{prefix} {lp_prefix}{name}: {message}";

    // ── Staff Chat ─────────────────────────────────────────────────────────

    public String  staffTrigger      = "\\";       // prefix for staff chat (empty = disabled)
    public String  staffPrefix       = "[Staff]";
    public String  staffPrefixColor  = "RED";
    public String  staffNameColor    = "WHITE";
    public String  staffMessageColor = "DARK_RED";
    public String  staffFormat       = "{timestamp}{prefix} {lp_prefix}{name}: {message}";

    // ── Timestamp ──────────────────────────────────────────────────────────────

    public boolean showTimestamp    = false;
    public String  timestampFormat  = "HH:mm";
    public String  timestampColor   = "DARK_GRAY";

    // ── Integrations ───────────────────────────────────────────────────────────

    public boolean usePlaceholderApi = true;
    public boolean useBanHammer      = true;
    public boolean useLuckPerms      = true;
    public boolean useScarpetEvents  = false;

    // ── BlockBot / Discord ─────────────────────────────────────────────────────

    public String  discordBridgeMode     = "auto";
    public String  blockbotGlobalChannel = "chat";
    public String  blockbotLocalChannel  = "";
    public String  discordFormat         = "[Discord] {message}";
    public boolean discordPassthrough    = true;  // true = BlockBot handles Discord->MC natively (recommended), false = viaStyle handles it with discordFormat
    public boolean discordMentionPing   = true;  // true = scan broadcast game messages for @MCPlayerName and trigger in-game mention sound/highlight (works in any mode)
    /**
     * Manual MC-name → Discord user ID mappings used when @name appears in MC chat.
     * Format: comma-separated pairs "MinecraftNick:DiscordUserId"
     * Example: "viaMeowts:406777223153057793,SomePlayer:123456789012345678"
     * These take priority over automatic JDA guild member lookup.
     */
    public String  discordMentionMappings = "";  // "MCNick:DiscordId,MCNick2:DiscordId2"

    // ── Private Messages ───────────────────────────────────────────────────────

    public boolean pmAllowSelfMessage = false;
    public String  pmSenderFormat   = "[PM -> {receiver}] {message}";
    public String  pmReceiverFormat = "[PM <- {sender}] {message}";
    public String  pmColor          = "LIGHT_PURPLE";

    // ── Nick Colour ────────────────────────────────────────────────────────────

    public boolean nickColorEnabled      = true;  // master toggle
    public boolean nickColorInChat       = true;  // apply nick colour to chat messages
    public boolean nickColorInTab        = true;  // apply nick colour to the tab list
    public boolean nickColorInNametag    = true;  // apply nick colour to the above-head nametag
    public boolean nametagShowLpPrefix   = true;  // show LuckPerms prefix in the above-head nametag
    public String  nametagMode           = "display"; // "team" = 16 vanilla colours, "display" = TextDisplay entity (experimental)
    public String  nametagColorStrategy  = "first"; // "first" = first gradient stop, "average" = average of all stops
    public String  tabSortMode           = "normal"; // "reverse" = higher weight = higher position, "normal" = higher weight = lower, "none" = disabled
    public boolean tabSortSpectatorsToBottom = false; // push spectator-mode players below all others in tab
    public boolean nametagOrphanScanEnabled       = true; // periodically scan worlds for leftover TextDisplay entities with no owner
    public int     nametagOrphanScanIntervalTicks = 200;  // how often to run the scan (ticks, 200 = 10 seconds)

    // ── Mentions ───────────────────────────────────────────────────────────────

    public boolean mentionsEnabled = true;   // @player mentions in chat
    public boolean mentionSound    = true;   // play sound on mention
    public boolean mentionBold     = false;  // bold @mentions in chat
    public String  mentionColor    = "GOLD"; // highlight color for @mentions

    // ── Language ───────────────────────────────────────────────────────────────

    public String  defaultLanguage = "en";

    // ── viaSuper ───────────────────────────────────────────────────────────────

    public boolean viaSuperWordSound     = false; // Play sound for each word in viaSuper
    public int     viaSuperSubtitleLength = 7;    // Words with length >= this will be displayed as subtitle

    /**
     * Format for short words shown as BIG TITLE.
     * Use Simplified Text Format tags and/or legacy &-codes. {word} = the word itself.
     * Examples: "<bold><red>{word}"  |  "<gr:#ff0000:#ffaa00><bold>{word}</gr>"
     */
    public String viaSuperTitleFormat    = "<bold><red>{word}";

    /**
     * Format for long words (>= viaSuperSubtitleLength chars) shown as small SUBTITLE.
     * Use Simplified Text Format tags and/or legacy &-codes. {word} = the word itself.
     * Examples: "<italic><gold>{word}"  |  "<bold><dark_red>{word}"
     */
    public String viaSuperSubtitleFormat = "<bold><dark_red>{word}";

    // ── Console logging ────────────────────────────────────────────────────────

    /** Log [Global] messages to server console. */
    public boolean logGlobalToConsole   = true;
    /** Log [Local] messages to server console. */
    public boolean logLocalToConsole    = true;
    /** Log [Staff] messages to server console. */
    public boolean logStaffToConsole    = true;
    /** Log [PM] private messages to server console. */
    public boolean logPrivatesToConsole = true;

    // ── Join / Leave Messages ──────────────────────────────────────────────────

    public String  joinFormat       = "&a+ &r{name}";
    public String  leaveFormat      = "&c- &r{name}";
    public String  firstJoinFormat  = "&6+ &r{name} &6впервые зашел на сервер!";

    // ══════════════════════════════════════════════════════════════════════════
    //  TOML I/O
    // ══════════════════════════════════════════════════════════════════════════

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle");

    private static final Path TOML_PATH = CONFIG_DIR.resolve("viaStyle.toml");

    private static final Path LEGACY_JSON_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle.json");

    private static final Path OLD_TOML_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("viachat.toml");

    private static final Path OLD_TOML_PATH2 = FabricLoader.getInstance()
            .getConfigDir().resolve("viaStyle.toml");

    /** Pre-rename config directory (viamod → viaStyle). */
    private static final Path OLD_VIAMOD_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("viamod");

    private static final Path OLD_VIAMOD_TOML = OLD_VIAMOD_DIR.resolve("viamod.toml");

    /**
     * Loads config from {@code config/viaStyle/viaStyle.toml}.
     * Migrates from old locations if needed.
     */
    public static ViaStyleConfig load() {
        // ── Ensure config directory exists ─────────────────────────────────
        try { Files.createDirectories(CONFIG_DIR); } catch (IOException ignored) {}

        // ── config/viamod/viamod.toml → config/viaStyle/viaStyle.toml ─────────
        if (!Files.exists(TOML_PATH) && Files.exists(OLD_VIAMOD_TOML)) {
            viaStyle.LOGGER.info("[viaStyle] Found old viamod/viamod.toml — migrating to viaStyle/viaStyle.toml...");
            try {
                String toml = Files.readString(OLD_VIAMOD_TOML);
                ViaStyleConfig migrated = fromToml(toml);
                migrated.save();
                Files.move(OLD_VIAMOD_TOML, OLD_VIAMOD_TOML.resolveSibling("viamod.toml.old"));
                // Also migrate nickcolors.json if present in old dir
                Path oldNickColors = OLD_VIAMOD_DIR.resolve("nickcolors.json");
                Path newNickColors = CONFIG_DIR.resolve("nickcolors.json");
                if (Files.exists(oldNickColors) && !Files.exists(newNickColors)) {
                    Files.copy(oldNickColors, newNickColors);
                    viaStyle.LOGGER.info("[viaStyle] Also migrated nickcolors.json from viamod/ folder.");
                }
                viaStyle.LOGGER.info("[viaStyle] Migration from viamod/ complete.");
                return migrated;
            } catch (Exception e) {
                viaStyle.LOGGER.warn("[viaStyle] viamod/ migration failed: {} — continuing.", e.getMessage());
            }
        }

        // ── config/viachat.toml → config/viaStyle/viaStyle.toml ───────────────
        if (!Files.exists(TOML_PATH) && Files.exists(OLD_TOML_PATH)) {
            viaStyle.LOGGER.info("[viaStyle] Found old viachat.toml — migrating to viaStyle/viaStyle.toml...");
            try {
                String toml = Files.readString(OLD_TOML_PATH);
                ViaStyleConfig migrated = fromToml(toml);
                migrated.save();
                Files.move(OLD_TOML_PATH, OLD_TOML_PATH.resolveSibling("viachat.toml.old"));
                viaStyle.LOGGER.info("[viaStyle] Migration complete — old config renamed to viachat.toml.old");
                return migrated;
            } catch (Exception e) {
                viaStyle.LOGGER.warn("[viaStyle] Old TOML migration failed: {} — continuing.", e.getMessage());
            }
        }

        // ── config/viaStyle.toml (flat) → config/viaStyle/viaStyle.toml ─────────
        if (!Files.exists(TOML_PATH) && Files.exists(OLD_TOML_PATH2)) {
            viaStyle.LOGGER.info("[viaStyle] Found flat viaStyle.toml — migrating to viaStyle/viaStyle.toml...");
            try {
                String toml = Files.readString(OLD_TOML_PATH2);
                ViaStyleConfig migrated = fromToml(toml);
                migrated.save();
                Files.delete(OLD_TOML_PATH2);
                viaStyle.LOGGER.info("[viaStyle] Migration from flat viaStyle.toml complete.");
                return migrated;
            } catch (Exception e) {
                viaStyle.LOGGER.warn("[viaStyle] Flat TOML migration failed: {} — continuing.", e.getMessage());
            }
        }

        // ── JSON → TOML migration ──────────────────────────────────────────
        if (!Files.exists(TOML_PATH) && Files.exists(LEGACY_JSON_PATH)) {
            viaStyle.LOGGER.info("[viaStyle] Found legacy viaStyle.json — migrating to viaStyle.toml...");
            try {
                String json = Files.readString(LEGACY_JSON_PATH);
                json = stripJsonComments(json);
                Gson gson = new GsonBuilder().create();
                ViaStyleConfig migrated = gson.fromJson(json, ViaStyleConfig.class);
                if (migrated == null) migrated = new ViaStyleConfig();
                migrated.save();
                Files.move(LEGACY_JSON_PATH, LEGACY_JSON_PATH.resolveSibling("viaStyle.json.old"));
                viaStyle.LOGGER.info("[viaStyle] Migration complete — old config renamed to viaStyle.json.old");
                return migrated;
            } catch (Exception e) {
                viaStyle.LOGGER.warn("[viaStyle] JSON migration failed: {} — creating fresh config.", e.getMessage());
            }
        }

        // ── Normal TOML load ───────────────────────────────────────────────
        if (!Files.exists(TOML_PATH)) {
            ViaStyleConfig defaults = new ViaStyleConfig();
            defaults.save();
            viaStyle.LOGGER.info("[viaStyle] Created default config: {}", TOML_PATH);
            return defaults;
        }
        try {
            String toml = Files.readString(TOML_PATH);
            ViaStyleConfig config = fromToml(toml);
            config.save(); // re-save to fill in any new fields from updates
            viaStyle.LOGGER.info("[viaStyle] Config loaded: {}", TOML_PATH);
            return config;
        } catch (IOException e) {
            viaStyle.LOGGER.error("[viaStyle] Failed to load config: {}", e.getMessage());
            return new ViaStyleConfig();
        }
    }

    /** Saves the current config to {@code config/viaStyle.toml}. */
    public void save() {
        try {
            Files.createDirectories(TOML_PATH.getParent());
            Files.writeString(TOML_PATH, toToml());
        } catch (IOException e) {
            viaStyle.LOGGER.error("[viaStyle] Failed to save config: {}", e.getMessage());
        }
    }

    // ── TOML writer ────────────────────────────────────────────────────────────

    private String toToml() {
        StringBuilder sb = new StringBuilder();
        sb.append("# viaStyle configuration\n");
        sb.append("# Colors: named (GREEN, YELLOW, DARK_GRAY...) or hex \"#RRGGBB\"\n");
        sb.append("# Format tokens: {timestamp} {prefix} {name} {message} {lp_prefix} {lp_suffix}\n\n");

        // [local]
        sb.append("# LOCAL CHAT\n\n");
        sb.append("[local]\n\n");
        kv(sb, "radius",        localChatRadius,  "Chat range in blocks");
        kv(sb, "trigger",       localTrigger,     "Prefix char for local chat (empty = local is default)");
        kv(sb, "prefix",        localPrefix,       "Prefix text, e.g. \"[L]\"");
        kv(sb, "prefix_color",  localPrefixColor,  "Color of the prefix");
        kv(sb, "name_color",    localNameColor,    "Player name color");
        kv(sb, "message_color", localMessageColor, "Message text color");
        kv(sb, "format",        localFormat,       "Message template");
        kv(sb, "noone_heard",         localNooneHeard,        "Show hint to sender when nobody is in range (default false)");
        kv(sb, "noone_heard_message", localNooneHeardMessage, "Text shown to sender when nobody is in range");
        sb.append("\n");

        // [global]
        sb.append("# GLOBAL CHAT\n\n");
        sb.append("[global]\n\n");
        kv(sb, "trigger",       globalTrigger,     "Prefix char to switch to global chat");
        kv(sb, "prefix",        globalPrefix,       "Prefix text, e.g. \"[G]\"");
        kv(sb, "prefix_color",  globalPrefixColor,  "Color of the prefix");
        kv(sb, "name_color",    globalNameColor,    "Player name color");
        kv(sb, "message_color", globalMessageColor, "Message text color");
        kv(sb, "format",        globalFormat,       "Message template");
        sb.append("\n");

        // [staff]
        sb.append("# STAFF CHAT\n# Requires permission \"viastyle.staff\" or OP level 2.\n\n");
        sb.append("[staff]\n\n");
        kv(sb, "trigger",       staffTrigger,      "Prefix char for staff chat (empty = disabled)");
        kv(sb, "prefix",        staffPrefix,       "Prefix text");
        kv(sb, "prefix_color",  staffPrefixColor,  "Color of the prefix");
        kv(sb, "name_color",    staffNameColor,    "Player name color");
        kv(sb, "message_color", staffMessageColor, "Message text color");
        kv(sb, "format",        staffFormat,       "Message template");
        sb.append("\n");

        // [timestamp]
        sb.append("# TIMESTAMP\n\n");
        sb.append("[timestamp]\n\n");
        kv(sb, "enabled",  showTimestamp,   "Show time before messages");
        kv(sb, "format",   timestampFormat, "Java pattern: HH:mm, HH:mm:ss, hh:mm a");
        kv(sb, "color",    timestampColor,  "Timestamp text color");
        sb.append("\n");

        // [integrations]
        sb.append("# INTEGRATIONS\n# Each requires the corresponding mod installed.\n\n");
        sb.append("[integrations]\n\n");
        kv(sb, "placeholder_api", usePlaceholderApi, "TextPlaceholderAPI (placeholder-api)");
        kv(sb, "banhammer",       useBanHammer,      "BanHammer mute check");
        kv(sb, "luckperms",       useLuckPerms,      "LuckPerms prefix/suffix");
        kv(sb, "scarpet",         useScarpetEvents,  "Scarpet events (carpet)");
        sb.append("\n");

        // [blockbot]
        sb.append("# BLOCKBOT / DISCORD\n# mode: \"auto\" = on when BlockBot is present, \"none\" = disabled\n\n");
        sb.append("[blockbot]\n\n");
        kv(sb, "mode",           discordBridgeMode,     "Bridge mode");
        kv(sb, "global_channel", blockbotGlobalChannel, "Channel for global messages");
        kv(sb, "local_channel",  blockbotLocalChannel,  "Channel for local (empty = off)");
        kv(sb, "discord_format", discordFormat,         "Discord→MC format. {message}, {channel}");
        kv(sb, "passthrough",   discordPassthrough,    "true (default) = BlockBot handles Discord->MC natively; false = viaStyle wraps with discord_format");
        sb.append("\n");

        // [pm]
        sb.append("# PRIVATE MESSAGES\n# Tokens: {sender}, {receiver}, {message}\n\n");
        sb.append("[pm]\n\n");
        kv(sb, "allow_self",      pmAllowSelfMessage, "Allow /msg to yourself");
        kv(sb, "sender_format",   pmSenderFormat,     "Format shown to the sender");
        kv(sb, "receiver_format", pmReceiverFormat,   "Format shown to the receiver");
        kv(sb, "color",           pmColor,            "Message color (named or #RRGGBB)");
        sb.append("\n");

        // [nickcolor]
        sb.append("# NICK COLOUR\n# Permission: viastyle.nickcolor.<spec>  (e.g. viastyle.nickcolor.#ff5555)\n");
        sb.append("# Spec: #RRGGBB, gradient:#RRGGBB:#RRGGBB, or named colour (gold, red, ...)\n");
        sb.append("# Fallback: /nickcolor set <player> <spec>  or  config/viaStyle-nickcolors.json\n\n");
        sb.append("[nickcolor]\n\n");
        kv(sb, "enabled",    nickColorEnabled,   "Master toggle for nick colours");
        kv(sb, "in_chat",    nickColorInChat,    "Apply nick colour in chat messages");
        kv(sb, "in_tab",     nickColorInTab,     "Apply nick colour in the tab list");
        kv(sb, "in_nametag",       nickColorInNametag,  "Apply nick colour to the above-head nametag");
        kv(sb, "nametag_lp_prefix", nametagShowLpPrefix, "Show LuckPerms prefix in the above-head nametag (display and team modes)");
        kv(sb, "nametag_mode", nametagMode,      "\"team\" = 16 vanilla colours via scoreboard, \"display\" = TextDisplay entity with full gradients (experimental)");
        kv(sb, "nametag_color_strategy", nametagColorStrategy, "Team mode gradient strategy: \"first\" = use first gradient colour, \"average\" = average of all gradient stops");
        kv(sb, "tab_sort_mode", tabSortMode, "Tab list sort by LP group weight: \"reverse\" = higher weight = higher, \"normal\" = higher weight = lower, \"none\" = disabled");
        kv(sb, "spectators_to_bottom", tabSortSpectatorsToBottom, "Push spectator-mode players below all others in tab list");
        sb.append("\n");

        // [mentions]
        sb.append("# MENTIONS\n# @PlayerName highlights in chat messages\n\n");
        sb.append("[mentions]\n\n");
        kv(sb, "enabled", mentionsEnabled, "Enable @player mentions in chat");
        kv(sb, "sound",   mentionSound,    "Play a sound when mentioned");
        kv(sb, "bold",    mentionBold,     "Bold @mentions in chat");
        kv(sb, "color",   mentionColor,    "Highlight color for @mentions (named or #RRGGBB)");
        sb.append("\n");

        // [language]
        sb.append("# LANGUAGE\n\n");
        sb.append("[language]\n\n");
        kv(sb, "default", defaultLanguage, "\"en\" or \"ru\"");
        sb.append("\n");

        // [viasuper]
        sb.append("# VIA SUPER\n\n");
        sb.append("[viasuper]\n\n");
        kv(sb, "word_sound", viaSuperWordSound, "Play a sound for each word displayed");
        kv(sb, "subtitle_length", viaSuperSubtitleLength, "Words with length >= this are shown as subtitle (smaller font)");
        sb.append("\n");

        // [joinleave]
        sb.append("# JOIN / LEAVE MESSAGES\n\n");
        sb.append("[joinleave]\n\n");
        kv(sb, "join_format",       joinFormat,      "Format for join messages.  {name} = colored nickname");
        kv(sb, "leave_format",      leaveFormat,     "Format for leave messages. {name} = colored nickname");
        kv(sb, "first_join_format", firstJoinFormat,  "Format for first-time join. {name} = colored nickname");
        sb.append("\n");

        // [console]
        sb.append("# CONSOLE LOGGING\n\n");
        sb.append("[console]\n\n");
        kv(sb, "log_global",   logGlobalToConsole,   "Log [Global] messages to server console");
        kv(sb, "log_local",    logLocalToConsole,     "Log [Local] messages to server console");
        kv(sb, "log_staff",    logStaffToConsole,     "Log [Staff] messages to server console");
        kv(sb, "log_privates", logPrivatesToConsole,  "Log [PM] private messages to server console");

        return sb.toString();
    }

    /** Writes a key–value line with a preceding comment. */
    private static void kv(StringBuilder sb, String key, Object value, String comment) {
        sb.append("# ").append(comment).append("\n");
        if (value instanceof String s) {
            sb.append(key).append(" = \"").append(escapeToml(s)).append("\"\n");
        } else if (value instanceof Boolean b) {
            sb.append(key).append(" = ").append(b).append("\n");
        } else if (value instanceof Integer i) {
            sb.append(key).append(" = ").append(i).append("\n");
        } else if (value instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append(key).append(" = ").append((long) d.doubleValue()).append(".0\n");
            } else {
                sb.append(key).append(" = ").append(d).append("\n");
            }
        } else {
            sb.append(key).append(" = ").append(value).append("\n");
        }
    }

    /** Escapes special characters in a TOML string value. */
    private static String escapeToml(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    // ── TOML reader ────────────────────────────────────────────────────────────

    /**
     * Parses a simple TOML into a ViaStyleConfig.
     * Supports: [section] headers, key = value, # comments, strings, booleans, numbers.
     */
    private static ViaStyleConfig fromToml(String toml) {
        ViaStyleConfig cfg = new ViaStyleConfig();
        Map<String, String> keyMap = buildKeyMap();
        String currentSection = "";

        try (BufferedReader reader = new BufferedReader(new StringReader(toml))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim();
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq < 0) continue;

                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();

                // Strip inline comment (outside strings)
                if (!val.startsWith("\"")) {
                    int hash = val.indexOf('#');
                    if (hash >= 0) val = val.substring(0, hash).trim();
                }

                String mapKey = currentSection.isEmpty() ? key : currentSection + "." + key;
                String fieldName = keyMap.get(mapKey);
                if (fieldName == null) continue;

                try {
                    java.lang.reflect.Field field = ViaStyleConfig.class.getField(fieldName);
                    Class<?> type = field.getType();

                    if (type == String.class) {
                        field.set(cfg, unquoteToml(val));
                    } else if (type == boolean.class) {
                        field.setBoolean(cfg, Boolean.parseBoolean(val));
                    } else if (type == double.class) {
                        field.setDouble(cfg, Double.parseDouble(val));
                    } else if (type == int.class) {
                        // Handle "7.0" from old configs that wrote int as double
                        String intVal = val.contains(".") ? val.substring(0, val.indexOf('.')) : val;
                        field.setInt(cfg, Integer.parseInt(intVal));
                    }
                } catch (NoSuchFieldException | IllegalAccessException | NumberFormatException e) {
                    viaStyle.LOGGER.warn("[viaStyle] Config: could not set '{}': {}", mapKey, e.getMessage());
                }
            }
        } catch (IOException e) {
            viaStyle.LOGGER.error("[viaStyle] Config parse error: {}", e.getMessage());
        }

        return cfg;
    }

    /** Removes surrounding quotes and handles basic TOML escapes. */
    private static String unquoteToml(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
            s = s.replace("\\\"", "\"")
                 .replace("\\\\", "\\")
                 .replace("\\n", "\n")
                 .replace("\\t", "\t");
        }
        return s;
    }

    /** Maps "section.toml_key" → Java field name. */
    private static Map<String, String> buildKeyMap() {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("local.radius",         "localChatRadius");
        m.put("local.trigger",        "localTrigger");
        m.put("local.prefix",         "localPrefix");
        m.put("local.prefix_color",   "localPrefixColor");
        m.put("local.name_color",     "localNameColor");
        m.put("local.message_color",  "localMessageColor");
        m.put("local.format",              "localFormat");
        m.put("local.noone_heard",         "localNooneHeard");
        m.put("local.noone_heard_message", "localNooneHeardMessage");

        m.put("global.trigger",       "globalTrigger");
        m.put("global.prefix",        "globalPrefix");
        m.put("global.prefix_color",  "globalPrefixColor");
        m.put("global.name_color",    "globalNameColor");
        m.put("global.message_color", "globalMessageColor");
        m.put("global.format",        "globalFormat");

        m.put("staff.trigger",        "staffTrigger");
        m.put("staff.prefix",         "staffPrefix");
        m.put("staff.prefix_color",   "staffPrefixColor");
        m.put("staff.name_color",     "staffNameColor");
        m.put("staff.message_color",  "staffMessageColor");
        m.put("staff.format",         "staffFormat");

        m.put("timestamp.enabled",    "showTimestamp");
        m.put("timestamp.format",     "timestampFormat");
        m.put("timestamp.color",      "timestampColor");

        m.put("integrations.placeholder_api", "usePlaceholderApi");
        m.put("integrations.banhammer",       "useBanHammer");
        m.put("integrations.luckperms",       "useLuckPerms");
        m.put("integrations.scarpet",         "useScarpetEvents");

        m.put("blockbot.mode",           "discordBridgeMode");
        m.put("blockbot.global_channel", "blockbotGlobalChannel");
        m.put("blockbot.local_channel",  "blockbotLocalChannel");
        m.put("blockbot.discord_format", "discordFormat");
        m.put("blockbot.passthrough",   "discordPassthrough");

        m.put("pm.allow_self",     "pmAllowSelfMessage");
        m.put("pm.sender_format",   "pmSenderFormat");
        m.put("pm.receiver_format", "pmReceiverFormat");
        m.put("pm.color",           "pmColor");

        m.put("nickcolor.enabled",    "nickColorEnabled");
        m.put("nickcolor.in_chat",    "nickColorInChat");
        m.put("nickcolor.in_tab",     "nickColorInTab");
        m.put("nickcolor.in_nametag",       "nickColorInNametag");
        m.put("nickcolor.nametag_lp_prefix", "nametagShowLpPrefix");
        m.put("nickcolor.nametag_mode",      "nametagMode");
        m.put("nickcolor.nametag_color_strategy", "nametagColorStrategy");
        m.put("nickcolor.tab_sort_mode", "tabSortMode");
        m.put("nickcolor.spectators_to_bottom", "tabSortSpectatorsToBottom");

        m.put("mentions.enabled", "mentionsEnabled");
        m.put("mentions.sound",   "mentionSound");
        m.put("mentions.bold",    "mentionBold");
        m.put("mentions.color",   "mentionColor");

        m.put("language.default",     "defaultLanguage");

        m.put("viasuper.word_sound",  "viaSuperWordSound");
        m.put("viasuper.subtitle_length", "viaSuperSubtitleLength");

        m.put("joinleave.join_format",       "joinFormat");
        m.put("joinleave.leave_format",      "leaveFormat");
        m.put("joinleave.first_join_format", "firstJoinFormat");

        m.put("console.log_global",   "logGlobalToConsole");
        m.put("console.log_local",    "logLocalToConsole");
        m.put("console.log_staff",    "logStaffToConsole");
        m.put("console.log_privates", "logPrivatesToConsole");

        return m;
    }

    // ── Legacy JSON helpers ────────────────────────────────────────────────────

    private static String stripJsonComments(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            if (!line.trim().startsWith("//")) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    // ── Color resolution ───────────────────────────────────────────────────────

    /**
     * Resolves a color string to a {@link TextColor}.
     * Accepts named colors (GREEN, YELLOW, DARK_GRAY...) and hex (#RRGGBB).
     */
    public TextColor resolveColor(String colorStr, TextColor fallback) {
        if (colorStr == null || colorStr.isBlank()) return fallback;
        String trimmed = colorStr.trim();

        if (trimmed.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(trimmed.substring(1), 16);
                return TextColor.fromRgb(rgb);
            } catch (NumberFormatException e) {
                viaStyle.LOGGER.warn("[viaStyle] Invalid hex color '{}', using fallback.", trimmed);
                return fallback;
            }
        }

        try {
            Formatting fmt = Formatting.valueOf(trimmed.toUpperCase());
            if (!fmt.isModifier() && fmt.getColorValue() != null) {
                return TextColor.fromFormatting(fmt);
            }
        } catch (IllegalArgumentException ignored) {}

        viaStyle.LOGGER.warn("[viaStyle] Unknown color '{}', using fallback.", colorStr);
        return fallback;
    }

    // Convenience getters
    public TextColor getLocalPrefixColor()   { return resolveColor(localPrefixColor,   TextColor.fromFormatting(Formatting.GREEN));     }
    public TextColor getLocalNameColor()     { return resolveColor(localNameColor,     TextColor.fromFormatting(Formatting.GRAY));      }
    public TextColor getLocalMessageColor()  { return resolveColor(localMessageColor,  TextColor.fromFormatting(Formatting.GRAY));      }
    public TextColor getGlobalPrefixColor()  { return resolveColor(globalPrefixColor,  TextColor.fromFormatting(Formatting.YELLOW));    }
    public TextColor getGlobalNameColor()    { return resolveColor(globalNameColor,    TextColor.fromFormatting(Formatting.WHITE));     }
    public TextColor getGlobalMessageColor() { return resolveColor(globalMessageColor, TextColor.fromFormatting(Formatting.WHITE));     }
    public TextColor getStaffPrefixColor()   { return resolveColor(staffPrefixColor,   TextColor.fromFormatting(Formatting.RED));       }
    public TextColor getStaffNameColor()     { return resolveColor(staffNameColor,     TextColor.fromFormatting(Formatting.DARK_RED));  }
    public TextColor getStaffMessageColor()  { return resolveColor(staffMessageColor,  TextColor.fromFormatting(Formatting.DARK_RED));  }
    public TextColor getTimestampColor()     { return resolveColor(timestampColor,     TextColor.fromFormatting(Formatting.DARK_GRAY)); }
    public TextColor getMentionColor()       { return resolveColor(mentionColor,       TextColor.fromFormatting(Formatting.GOLD));      }
}
