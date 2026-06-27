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

    private static final String ITEM_EMPTY_EN = "<gray>[empty hand]";
    private static final String ITEM_EMPTY_RU = "<gray>[пустая рука]";
    private static final String INV_FORMAT_EN = "<#FFC64C>[inventory]";
    private static final String INV_FORMAT_RU = "<#FFC64C>[инвентарь]";
    private static final String EC_FORMAT_EN = "<#FFC64C>[enderchest]";
    private static final String EC_FORMAT_RU = "<#FFC64C>[эндер-сундук]";
    private static final String VIEW_HOVER_EN = "<#FCDE9D>Click to view {type} of {player} ({seconds}s)</#FCDE9D>";
    private static final String VIEW_HOVER_RU = "<#FCDE9D>Нажмите, чтобы открыть {type} игрока {player} ({seconds}с)</#FCDE9D>";
    private static final String INV_TITLE_EN = "{player}'s inventory";
    private static final String INV_TITLE_RU = "Инвентарь игрока {player}";
    private static final String EC_TITLE_EN = "{player}'s ender chest";
    private static final String EC_TITLE_RU = "Эндер-сундук игрока {player}";
    private static final String BROADCAST_HEADER_EN = "  <#FF6B6B><bold>✦ <#FFAF7A>Announcement <#FF6B6B>✦ <reset><#FFD580><italic>[{sender}]<reset>";
    private static final String BROADCAST_HEADER_RU = "  <#FF6B6B><bold>✦ <#FFAF7A>Объявление <#FF6B6B>✦ <reset><#FFD580><italic>[{sender}]<reset>";
    private static final String BROADCAST_MESSAGE_EN = "  <#FFF0D1>{message}";
    private static final String BROADCAST_MESSAGE_RU = "  <#FFF0D1>{message}";
    private static final String BROADCAST_CONSOLE_EN = "Console";
    private static final String BROADCAST_CONSOLE_RU = "Консоль";
    private static final String BROADCAST_COOLDOWN_EN = "<#FF5555>[viaStyle] Cooldown: {seconds}s";
    private static final String BROADCAST_COOLDOWN_RU = "<#FF5555>[viaStyle] Кулдаун: {seconds}с";
    private static final String BROADCAST_FEEDBACK_EN = "<#98FB98>[viaStyle] Broadcast sent to {count} player(s).";
    private static final String BROADCAST_FEEDBACK_RU = "<#98FB98>[viaStyle] Объявление отправлено {count} игрок(ам).";
    private static final String BROADCAST_HEADER_LEGACY = "<#FFC64C>▸ Объявление ▸ <#FCDE9D>[{sender}]";
    private static final String BROADCAST_MESSAGE_LEGACY = "<#D9D0D5>{message}";

    // ── Local Chat ───────────────────────────────────────────────────────

    public double  localChatRadius   = 100.0;
    public String  localTrigger      = "";         // empty = local is default, no prefix needed
    public String  localPrefix       = "[L]";
    public String  localPrefixColor  = "#98FB98";
    public String  localNameColor    = "#D9D0D5";
    public String  localMessageColor = "#D9D0D5";
    public String  localFormat       = "{timestamp}{prefix} {lp_prefix}{name}: {message}";
    public boolean localNooneHeard        = false;       // show a hint when nobody received the message
    public String  localNooneHeardMessage = "Nobody heard you."; // text shown to sender when no one is in range

    // ── Chat formatting / MiniMessage ───────────────────────────────────────

    public boolean chatMiniMessageEnabled = true;
    public boolean chatMiniMessageRequirePermission = true;
    public String  chatMiniMessagePermission = "viastyle.chat.minimessage";

    // ── Global Chat ──────────────────────────────────────────────────────

    public String  globalTrigger     = "!";        // prefix to route message to global chat
    public String  globalPrefix       = "[G]";
    public String  globalPrefixColor  = "#FCDE9D";
    public String  globalNameColor    = "#D9D0D5";
    public String  globalMessageColor = "#D9D0D5";
    public String  globalFormat       = "{timestamp}{prefix} {lp_prefix}{name}: {message}";

    // ── Staff Chat ─────────────────────────────────────────────────────────

    public String  staffTrigger      = "\\";       // prefix for staff chat (empty = disabled)
    public String  staffPrefix       = "[Staff]";
    public String  staffPrefixColor  = "#FF5555";
    public String  staffNameColor    = "#D9D0D5";
    public String  staffMessageColor = "#FF5555";
    public String  staffFormat       = "{timestamp}{prefix} {lp_prefix}{name}: {message}";

    // ── Timestamp ──────────────────────────────────────────────────────────────

    public boolean showTimestamp    = false;
    public String  timestampFormat  = "HH:mm";
    public String  timestampColor   = "#B0C4DE";

    // ── Integrations ───────────────────────────────────────────────────────────

    public boolean usePlaceholderApi = true;
    public boolean useBanHammer      = true;
    public boolean useLuckPerms      = true;

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
    public boolean pmBanHammerMute    = true;
    public String  pmSenderFormat   = "[PM -> {receiver}] {message}";
    public String  pmReceiverFormat = "[PM <- {sender}] {message}";
    public String  pmColor          = "#E8CFDF";
    public boolean pmSoundEnabled   = true;
    public String  pmSoundId        = "minecraft:entity.experience_orb.pickup";
    public double  pmSoundVolume    = 1.0;
    public double  pmSoundPitch     = 1.0;

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
    public boolean mentionBold     = false;  // legacy: kept for config compatibility, style uses non-bold mentions
    public String  mentionColor    = "#FFC64C"; // highlight color for @mentions

    // ── Chat Placeholders ([item], [pos], [inv], [ec]) ──────────────────────

    public boolean chatPlaceholdersEnabled = true;
    public boolean chatPlaceholderItemEnabled = true;
    public boolean chatPlaceholderPosEnabled = true;
    public boolean chatPlaceholderInvEnabled = true;
    public boolean chatPlaceholderEcEnabled = true;

    public String chatPlaceholderItemPermission = "viastyle.placeholder.item";
    public String chatPlaceholderPosPermission  = "viastyle.placeholder.pos";
    public String chatPlaceholderInvPermission  = "viastyle.placeholder.inv";
    public String chatPlaceholderEcPermission   = "viastyle.placeholder.ec";

    public int     chatPlaceholderCooldownSeconds = 10;
    public int     chatPlaceholderMaxPerMessage   = 4;
    public int     chatPlaceholderExpireSeconds   = 180;
    public boolean chatPlaceholderDenyIfNoItem    = true;
    public boolean chatPlaceholderLetMessageThrough = true;

    public String chatPlaceholderItemFormat      = "<#FFC64C>[{item}]";
    public String chatPlaceholderItemEmptyFormat = ITEM_EMPTY_EN;
    public String chatPlaceholderPosFormat       = "<#FFC64C>[{x}, {y}, {z}]";
    public String chatPlaceholderPosHover        = "<#D9D0D5>{world}</#D9D0D5><newline><#FCDE9D>{biome}</#FCDE9D>";
    public String chatPlaceholderPosClickSuggest = "/tp {x} {y} {z}";
        public String chatPlaceholderInvFormat       = INV_FORMAT_EN;
        public String chatPlaceholderEcFormat        = EC_FORMAT_EN;
        public String chatPlaceholderViewHover       = VIEW_HOVER_EN;
        public String chatPlaceholderInventoryTitle  = INV_TITLE_EN;
        public String chatPlaceholderEnderChestTitle = EC_TITLE_EN;

        public boolean applyLocalizedPlaceholderDefaults(String languageCode) {
        boolean ru = "ru".equalsIgnoreCase(languageCode);
        boolean changed = false;

        changed |= localizeTemplateField(
            () -> chatPlaceholderItemEmptyFormat,
            value -> chatPlaceholderItemEmptyFormat = value,
            ITEM_EMPTY_EN, ITEM_EMPTY_RU, ru);

        changed |= localizeTemplateField(
            () -> chatPlaceholderInvFormat,
            value -> chatPlaceholderInvFormat = value,
            INV_FORMAT_EN, INV_FORMAT_RU, ru);

        changed |= localizeTemplateField(
            () -> chatPlaceholderEcFormat,
            value -> chatPlaceholderEcFormat = value,
            EC_FORMAT_EN, EC_FORMAT_RU, ru);

        changed |= localizeTemplateField(
            () -> chatPlaceholderViewHover,
            value -> chatPlaceholderViewHover = value,
            VIEW_HOVER_EN, VIEW_HOVER_RU, ru);

        changed |= localizeTemplateField(
            () -> chatPlaceholderInventoryTitle,
            value -> chatPlaceholderInventoryTitle = value,
            INV_TITLE_EN, INV_TITLE_RU, ru);

        changed |= localizeTemplateField(
            () -> chatPlaceholderEnderChestTitle,
            value -> chatPlaceholderEnderChestTitle = value,
            EC_TITLE_EN, EC_TITLE_RU, ru);

        changed |= localizeTemplateField(
            () -> broadcastHeaderFormat,
            value -> broadcastHeaderFormat = value,
            BROADCAST_HEADER_EN, BROADCAST_HEADER_RU, ru);

        changed |= localizeTemplateField(
            () -> broadcastMessageFormat,
            value -> broadcastMessageFormat = value,
            BROADCAST_MESSAGE_EN, BROADCAST_MESSAGE_RU, ru);

        changed |= localizeTemplateField(
            () -> broadcastConsoleSenderName,
            value -> broadcastConsoleSenderName = value,
            BROADCAST_CONSOLE_EN, BROADCAST_CONSOLE_RU, ru);

        changed |= localizeTemplateField(
            () -> broadcastCooldownFormat,
            value -> broadcastCooldownFormat = value,
            BROADCAST_COOLDOWN_EN, BROADCAST_COOLDOWN_RU, ru);

        changed |= localizeTemplateField(
            () -> broadcastFeedbackFormat,
            value -> broadcastFeedbackFormat = value,
            BROADCAST_FEEDBACK_EN, BROADCAST_FEEDBACK_RU, ru);

        if (broadcastHeaderFormat != null && broadcastHeaderFormat.equals(BROADCAST_HEADER_LEGACY)) {
            String target = ru ? BROADCAST_HEADER_RU : BROADCAST_HEADER_EN;
            if (!target.equals(broadcastHeaderFormat)) {
                broadcastHeaderFormat = target;
                changed = true;
            }
        }

        if (broadcastMessageFormat != null && broadcastMessageFormat.equals(BROADCAST_MESSAGE_LEGACY)) {
            String target = ru ? BROADCAST_MESSAGE_RU : BROADCAST_MESSAGE_EN;
            if (!target.equals(broadcastMessageFormat)) {
                broadcastMessageFormat = target;
                changed = true;
            }
        }

        return changed;
        }

        private static boolean localizeTemplateField(java.util.function.Supplier<String> getter,
                             java.util.function.Consumer<String> setter,
                             String enDefault,
                             String ruDefault,
                             boolean ru) {
        String current = getter.get();
        if (current == null || current.isBlank()
            || current.equals(enDefault)
            || current.equals(ruDefault)) {
            String target = ru ? ruDefault : enDefault;
            if (!target.equals(current)) {
            setter.accept(target);
            return true;
            }
        }
        return false;
        }

    // ── Language ───────────────────────────────────────────────────────────────

    public String  defaultLanguage = "en";

    // ── viaSuper ───────────────────────────────────────────────────────────────

    public boolean viaSuperWordSound     = false; // Play sound for each word in viaSuper
    public int     viaSuperSubtitleLength = 7;    // Words with length >= this will be displayed as subtitle

    /**
     * Format for short words shown as BIG TITLE.
        * Use Simplified Text Format tags. {word} = the word itself.
    * Examples: "<#FCDE9D>{word}"  |  "<gr:#FFC64C:#FCDE9D>{word}</gr>"
     */
    public String viaSuperTitleFormat    = "<#FCDE9D>{word}";

    /**
     * Format for long words (>= viaSuperSubtitleLength chars) shown as small SUBTITLE.
        * Use Simplified Text Format tags. {word} = the word itself.
    * Examples: "<#D9D0D5>{word}"  |  "<#B0C4DE>{word}"
     */
    public String viaSuperSubtitleFormat = "<#D9D0D5>{word}";

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

    public String  joinFormat       = "<#98FB98>+ <reset>{name}";
    public String  leaveFormat      = "<#FF5555>- <reset>{name}";
    public String  firstJoinFormat  = "<#98FB98>+ <reset>{name} <#D9D0D5>впервые зашел на сервер!";
    public boolean joinLeavePerPlayerEnabled = true;
    public String  joinLeaveSelfPermission = "viastyle.joinleave.self";

    // ── AFK ─────────────────────────────────────────────────────────────────────

    public boolean afkEnabled = true;
    public int     afkTimeout = 300;
    public boolean afkSuffixEnabled = true;
    public String  afkSuffix  = " <gray>[AFK]";
    public String  afkSuffixColor = "";
    public String  afkNameColor   = "";
    public String  afkPermission   = "viastyle.command.afk";
    public String  afkBypassPermission = "viastyle.afk.bypass";
    public String  afkExemptPlayers    = "";
    public String  afkEnabledColor  = "#FCDE9D";
    public String  afkDisabledColor = "#98FB98";

    // ── Join / Leave panel overrides (viapanel helper fields) ───────────────

    public String  joinLeavePanelPlayerTarget = "";
    public String  joinLeavePanelPlayerJoinFormat = "";
    public String  joinLeavePanelPlayerLeaveFormat = "";
    public String  joinLeavePanelGroupTarget = "";
    public String  joinLeavePanelGroupJoinFormat = "";
    public String  joinLeavePanelGroupLeaveFormat = "";

    // ── Broadcast (/bc) ─────────────────────────────────────────────────────

    public boolean broadcastEnabled = true;
    public String  broadcastPermission = "viastyle.command.broadcast";
    public int     broadcastCooldownSeconds = 300;
    public String  broadcastHeaderFormat = BROADCAST_HEADER_EN;
    public String  broadcastMessageFormat = BROADCAST_MESSAGE_EN;
    public String  broadcastConsoleSenderName = BROADCAST_CONSOLE_EN;
    public String  broadcastCooldownFormat = BROADCAST_COOLDOWN_EN;
    public String  broadcastFeedbackFormat = BROADCAST_FEEDBACK_EN;
    public String  broadcastLogFormat = "[viaStyle] /bc by {sender} sent \"{message}\" to {count} player(s).";
    public boolean broadcastSoundEnabled = true;
    public String  broadcastSoundId = "minecraft:block.note_block.bell";
    public double  broadcastSoundVolume = 1.0;
    public double  broadcastSoundPitch = 1.0;
    public boolean broadcastSendFeedback = true;

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

        // [chat]
        sb.append("# CHAT FORMATTING\n\n");
        sb.append("[chat]\n\n");
        kv(sb, "minimessage_enabled", chatMiniMessageEnabled, "Enable MiniMessage parsing in chat body");
        kv(sb, "minimessage_require_permission", chatMiniMessageRequirePermission, "Require permission for MiniMessage in chat");
        kv(sb, "minimessage_permission", chatMiniMessagePermission, "Permission node for MiniMessage in chat");
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
        sb.append("\n");

        // [blockbot]
        sb.append("# BLOCKBOT / DISCORD\n# mode: \"auto\" = on when BlockBot is present, \"none\" = disabled\n\n");
        sb.append("[blockbot]\n\n");
        kv(sb, "mode",           discordBridgeMode,     "Bridge mode");
        kv(sb, "global_channel", blockbotGlobalChannel, "Channel for global messages");
        kv(sb, "local_channel",  blockbotLocalChannel,  "Channel for local (empty = off)");
        kv(sb, "discord_format", discordFormat,         "Discord→MC format. {message}, {channel}");
        kv(sb, "passthrough",   discordPassthrough,    "true (default) = BlockBot handles Discord->MC natively; false = viaStyle wraps with discord_format");
        kv(sb, "mention_ping",  discordMentionPing,    "Ping in-game players when @theirName appears in any broadcast (including Discord)");
        kv(sb, "mention_mappings", discordMentionMappings, "Manual MC-name:DiscordId mappings, comma-separated");
        sb.append("\n");

        // [pm]
        sb.append("# PRIVATE MESSAGES\n# Tokens: {sender}, {receiver}, {message}\n\n");
        sb.append("[pm]\n\n");
        kv(sb, "allow_self",      pmAllowSelfMessage, "Allow /msg to yourself");
        kv(sb, "banhammer_mute",  pmBanHammerMute,    "Check BanHammer mute on private messages");
        kv(sb, "sender_format",   pmSenderFormat,     "Format shown to the sender");
        kv(sb, "receiver_format", pmReceiverFormat,   "Format shown to the receiver");
        kv(sb, "color",           pmColor,            "Message color (named or #RRGGBB)");
        kv(sb, "sound_enabled",   pmSoundEnabled,     "Play a sound to the receiver on incoming PM");
        kv(sb, "sound_id",        pmSoundId,          "Sound id, e.g. minecraft:entity.experience_orb.pickup");
        kv(sb, "sound_volume",    pmSoundVolume,      "Sound volume");
        kv(sb, "sound_pitch",     pmSoundPitch,       "Sound pitch");
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
        kv(sb, "bold",    mentionBold,     "Legacy switch for bold @mentions (style keeps this disabled)");
        kv(sb, "color",   mentionColor,    "Highlight color for @mentions (named or #RRGGBB)");
        sb.append("\n");

        // [chat_placeholders]
        sb.append("# CHAT PLACEHOLDERS\n");
        sb.append("# Tokens in chat message body: [item], [pos], [inv], [ec]\n\n");
        sb.append("[chat_placeholders]\n\n");
        kv(sb, "enabled",            chatPlaceholdersEnabled, "Enable token parsing in chat messages");
        kv(sb, "item_enabled",       chatPlaceholderItemEnabled, "Enable [item]");
        kv(sb, "pos_enabled",        chatPlaceholderPosEnabled,  "Enable [pos]");
        kv(sb, "inv_enabled",        chatPlaceholderInvEnabled,  "Enable [inv]");
        kv(sb, "ec_enabled",         chatPlaceholderEcEnabled,   "Enable [ec]");
        kv(sb, "item_permission",    chatPlaceholderItemPermission, "Permission node for [item]");
        kv(sb, "pos_permission",     chatPlaceholderPosPermission,  "Permission node for [pos]");
        kv(sb, "inv_permission",     chatPlaceholderInvPermission,  "Permission node for [inv]");
        kv(sb, "ec_permission",      chatPlaceholderEcPermission,   "Permission node for [ec]");
        kv(sb, "cooldown_seconds",   chatPlaceholderCooldownSeconds, "Cooldown between token uses");
        kv(sb, "max_per_message",    chatPlaceholderMaxPerMessage, "Maximum replacements per one message (<=0 = unlimited)");
        kv(sb, "expire_seconds",     chatPlaceholderExpireSeconds, "Lifetime for [inv]/[ec] snapshots");
        kv(sb, "deny_if_no_item",    chatPlaceholderDenyIfNoItem, "Block [item] when both hands are empty");
        kv(sb, "let_message_through", chatPlaceholderLetMessageThrough, "Keep original token in message when replacement fails");
        kv(sb, "item_format",        chatPlaceholderItemFormat, "Format for [item], use {item}");
        kv(sb, "item_empty_format",  chatPlaceholderItemEmptyFormat, "Format when [item] is used with empty hand and deny_if_no_item=false");
        kv(sb, "pos_format",         chatPlaceholderPosFormat, "Format for [pos], tokens: {x} {y} {z} {world} {biome}");
        kv(sb, "pos_hover",          chatPlaceholderPosHover, "Hover text for [pos]");
        kv(sb, "pos_click_suggest",  chatPlaceholderPosClickSuggest, "Suggested command on [pos] click");
        kv(sb, "inv_format",         chatPlaceholderInvFormat, "Format for [inv]");
        kv(sb, "ec_format",          chatPlaceholderEcFormat, "Format for [ec]");
        kv(sb, "view_hover",         chatPlaceholderViewHover, "Hover text for [inv]/[ec], tokens: {type} {player} {id} {seconds}");
        kv(sb, "inventory_title",    chatPlaceholderInventoryTitle, "GUI title for [inv]");
        kv(sb, "enderchest_title",   chatPlaceholderEnderChestTitle, "GUI title for [ec]");
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
        kv(sb, "title_format", viaSuperTitleFormat, "Format for short words shown as title. {word} placeholder");
        kv(sb, "subtitle_format", viaSuperSubtitleFormat, "Format for long words shown as subtitle. {word} placeholder");
        sb.append("\n");

        // [joinleave]
        sb.append("# JOIN / LEAVE MESSAGES\n\n");
        sb.append("[joinleave]\n\n");
        kv(sb, "join_format",       joinFormat,      "Format for join messages.  {name} = colored nickname");
        kv(sb, "leave_format",      leaveFormat,     "Format for leave messages. {name} = colored nickname");
        kv(sb, "first_join_format", firstJoinFormat,  "Format for first-time join. {name} = colored nickname");
        kv(sb, "per_player_enabled", joinLeavePerPlayerEnabled, "Enable per-player overrides from joinleave-users.json");
        kv(sb, "self_permission", joinLeaveSelfPermission, "Permission for players to use /joinleave");
        sb.append("\n");

        // [joinleave_panel]
        sb.append("# AFK\n\n");
        sb.append("[afk]\n\n");
        kv(sb, "enabled", afkEnabled, "Enable AFK idle tracking");
        kv(sb, "timeout", afkTimeout, "Seconds of inactivity before marking a player AFK");
        kv(sb, "suffix_enabled", afkSuffixEnabled, "Enable AFK suffix (false = don't append any suffix)");
        kv(sb, "suffix", afkSuffix, "Suffix appended to the player name when AFK (MiniMessage)");
        kv(sb, "suffix_color", afkSuffixColor, "Color of the AFK suffix (if not specified in suffix)");
        kv(sb, "name_color", afkNameColor, "Override player name colour when AFK (empty = keep existing)");
        kv(sb, "permission", afkPermission, "Permission node for /afk command");
        kv(sb, "bypass_permission", afkBypassPermission, "Permission that exempts a player from auto-AFK");
        kv(sb, "exempt_players", afkExemptPlayers, "Comma-separated UUIDs of players exempt from auto-AFK");
        kv(sb, "enabled_color", afkEnabledColor, "Text color for AFK enabled/joined messages (hex)");
        kv(sb, "disabled_color", afkDisabledColor, "Text color for AFK disabled/left messages (hex)");
        sb.append("\n");

        sb.append("# JOIN / LEAVE PANEL OVERRIDES\n# Helper fields used by viapanel to edit per-player/per-group overrides\n\n");
        sb.append("[joinleave_panel]\n\n");
        kv(sb, "player_target", joinLeavePanelPlayerTarget, "Player name or UUID for player override editing");
        kv(sb, "player_join_format", joinLeavePanelPlayerJoinFormat, "When edited in panel: sets/removes player join override");
        kv(sb, "player_leave_format", joinLeavePanelPlayerLeaveFormat, "When edited in panel: sets/removes player leave override");
        kv(sb, "group_target", joinLeavePanelGroupTarget, "LuckPerms group name for group override editing");
        kv(sb, "group_join_format", joinLeavePanelGroupJoinFormat, "When edited in panel: sets/removes group join override");
        kv(sb, "group_leave_format", joinLeavePanelGroupLeaveFormat, "When edited in panel: sets/removes group leave override");
        sb.append("\n");

        // [broadcast]
        sb.append("# BROADCAST COMMAND (/bc)\n\n");
        sb.append("[broadcast]\n\n");
        kv(sb, "enabled", broadcastEnabled, "Enable /bc command");
        kv(sb, "permission", broadcastPermission, "Permission node required for /bc");
        kv(sb, "cooldown_seconds", broadcastCooldownSeconds, "Cooldown for players between broadcasts");
        kv(sb, "header_format", broadcastHeaderFormat, "Header line format. Tokens: {sender}, {message}");
        kv(sb, "message_format", broadcastMessageFormat, "Message line format. Tokens: {sender}, {message}");
        kv(sb, "console_sender_name", broadcastConsoleSenderName, "Sender name shown for console execution");
        kv(sb, "cooldown_format", broadcastCooldownFormat, "Cooldown warning format. Token: {seconds}");
        kv(sb, "feedback_format", broadcastFeedbackFormat, "Feedback format after successful send. Token: {count}");
        kv(sb, "log_format", broadcastLogFormat, "Server log format. Tokens: {sender}, {message}, {count}");
        kv(sb, "sound_enabled", broadcastSoundEnabled, "Play sound to recipients");
        kv(sb, "sound_id", broadcastSoundId, "Sound id, e.g. minecraft:block.note_block.bell");
        kv(sb, "sound_volume", broadcastSoundVolume, "Sound volume");
        kv(sb, "sound_pitch", broadcastSoundPitch, "Sound pitch");
        kv(sb, "send_feedback", broadcastSendFeedback, "Send feedback to command source");
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

        m.put("chat.minimessage_enabled", "chatMiniMessageEnabled");
        m.put("chat.minimessage_require_permission", "chatMiniMessageRequirePermission");
        m.put("chat.minimessage_permission", "chatMiniMessagePermission");

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

        m.put("blockbot.mode",           "discordBridgeMode");
        m.put("blockbot.global_channel", "blockbotGlobalChannel");
        m.put("blockbot.local_channel",  "blockbotLocalChannel");
        m.put("blockbot.discord_format", "discordFormat");
        m.put("blockbot.passthrough",   "discordPassthrough");
        m.put("blockbot.mention_ping", "discordMentionPing");
        m.put("blockbot.mention_mappings", "discordMentionMappings");

        m.put("pm.allow_self",      "pmAllowSelfMessage");
        m.put("pm.banhammer_mute",  "pmBanHammerMute");
        m.put("pm.sender_format",   "pmSenderFormat");
        m.put("pm.receiver_format", "pmReceiverFormat");
        m.put("pm.color",           "pmColor");
        m.put("pm.sound_enabled",  "pmSoundEnabled");
        m.put("pm.sound_id",       "pmSoundId");
        m.put("pm.sound_volume",   "pmSoundVolume");
        m.put("pm.sound_pitch",    "pmSoundPitch");

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

        m.put("chat_placeholders.enabled", "chatPlaceholdersEnabled");
        m.put("chat_placeholders.item_enabled", "chatPlaceholderItemEnabled");
        m.put("chat_placeholders.pos_enabled", "chatPlaceholderPosEnabled");
        m.put("chat_placeholders.inv_enabled", "chatPlaceholderInvEnabled");
        m.put("chat_placeholders.ec_enabled", "chatPlaceholderEcEnabled");
        m.put("chat_placeholders.item_permission", "chatPlaceholderItemPermission");
        m.put("chat_placeholders.pos_permission", "chatPlaceholderPosPermission");
        m.put("chat_placeholders.inv_permission", "chatPlaceholderInvPermission");
        m.put("chat_placeholders.ec_permission", "chatPlaceholderEcPermission");
        m.put("chat_placeholders.cooldown_seconds", "chatPlaceholderCooldownSeconds");
        m.put("chat_placeholders.max_per_message", "chatPlaceholderMaxPerMessage");
        m.put("chat_placeholders.expire_seconds", "chatPlaceholderExpireSeconds");
        m.put("chat_placeholders.deny_if_no_item", "chatPlaceholderDenyIfNoItem");
        m.put("chat_placeholders.let_message_through", "chatPlaceholderLetMessageThrough");
        m.put("chat_placeholders.item_format", "chatPlaceholderItemFormat");
        m.put("chat_placeholders.item_empty_format", "chatPlaceholderItemEmptyFormat");
        m.put("chat_placeholders.pos_format", "chatPlaceholderPosFormat");
        m.put("chat_placeholders.pos_hover", "chatPlaceholderPosHover");
        m.put("chat_placeholders.pos_click_suggest", "chatPlaceholderPosClickSuggest");
        m.put("chat_placeholders.inv_format", "chatPlaceholderInvFormat");
        m.put("chat_placeholders.ec_format", "chatPlaceholderEcFormat");
        m.put("chat_placeholders.view_hover", "chatPlaceholderViewHover");
        m.put("chat_placeholders.inventory_title", "chatPlaceholderInventoryTitle");
        m.put("chat_placeholders.enderchest_title", "chatPlaceholderEnderChestTitle");

        m.put("language.default",     "defaultLanguage");

        m.put("viasuper.word_sound", "viaSuperWordSound");
        m.put("viasuper.subtitle_length", "viaSuperSubtitleLength");
        m.put("viasuper.title_format", "viaSuperTitleFormat");
        m.put("viasuper.subtitle_format", "viaSuperSubtitleFormat");

        m.put("joinleave.join_format",       "joinFormat");
        m.put("joinleave.leave_format",      "leaveFormat");
        m.put("joinleave.first_join_format", "firstJoinFormat");
        m.put("joinleave.per_player_enabled", "joinLeavePerPlayerEnabled");
        m.put("joinleave.self_permission", "joinLeaveSelfPermission");

        m.put("joinleave_panel.player_target", "joinLeavePanelPlayerTarget");
        m.put("joinleave_panel.player_join_format", "joinLeavePanelPlayerJoinFormat");
        m.put("joinleave_panel.player_leave_format", "joinLeavePanelPlayerLeaveFormat");
        m.put("joinleave_panel.group_target", "joinLeavePanelGroupTarget");
        m.put("joinleave_panel.group_join_format", "joinLeavePanelGroupJoinFormat");
        m.put("joinleave_panel.group_leave_format", "joinLeavePanelGroupLeaveFormat");

        m.put("broadcast.enabled", "broadcastEnabled");
        m.put("broadcast.permission", "broadcastPermission");
        m.put("broadcast.cooldown_seconds", "broadcastCooldownSeconds");
        m.put("broadcast.header_format", "broadcastHeaderFormat");
        m.put("broadcast.message_format", "broadcastMessageFormat");
        m.put("broadcast.console_sender_name", "broadcastConsoleSenderName");
        m.put("broadcast.cooldown_format", "broadcastCooldownFormat");
        m.put("broadcast.feedback_format", "broadcastFeedbackFormat");
        m.put("broadcast.log_format", "broadcastLogFormat");
        m.put("broadcast.sound_enabled", "broadcastSoundEnabled");
        m.put("broadcast.sound_id", "broadcastSoundId");
        m.put("broadcast.sound_volume", "broadcastSoundVolume");
        m.put("broadcast.sound_pitch", "broadcastSoundPitch");
        m.put("broadcast.send_feedback", "broadcastSendFeedback");

        m.put("console.log_global",   "logGlobalToConsole");
        m.put("console.log_local",    "logLocalToConsole");
        m.put("console.log_staff",    "logStaffToConsole");
        m.put("console.log_privates", "logPrivatesToConsole");

        m.put("afk.enabled", "afkEnabled");
        m.put("afk.timeout", "afkTimeout");
        m.put("afk.suffix_enabled", "afkSuffixEnabled");
        m.put("afk.suffix", "afkSuffix");
        m.put("afk.suffix_color", "afkSuffixColor");
        m.put("afk.name_color", "afkNameColor");
        m.put("afk.permission", "afkPermission");
        m.put("afk.bypass_permission", "afkBypassPermission");
        m.put("afk.exempt_players", "afkExemptPlayers");
        m.put("afk.enabled_color", "afkEnabledColor");
        m.put("afk.disabled_color", "afkDisabledColor");

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
    public TextColor getLocalPrefixColor()   { return resolveColor(localPrefixColor,   TextColor.fromRgb(0x98FB98)); }
    public TextColor getLocalNameColor()     { return resolveColor(localNameColor,     TextColor.fromRgb(0xD9D0D5)); }
    public TextColor getLocalMessageColor()  { return resolveColor(localMessageColor,  TextColor.fromRgb(0xD9D0D5)); }
    public TextColor getGlobalPrefixColor()  { return resolveColor(globalPrefixColor,  TextColor.fromRgb(0xFCDE9D)); }
    public TextColor getGlobalNameColor()    { return resolveColor(globalNameColor,    TextColor.fromRgb(0xD9D0D5)); }
    public TextColor getGlobalMessageColor() { return resolveColor(globalMessageColor, TextColor.fromRgb(0xD9D0D5)); }
    public TextColor getStaffPrefixColor()   { return resolveColor(staffPrefixColor,   TextColor.fromRgb(0xFF5555)); }
    public TextColor getStaffNameColor()     { return resolveColor(staffNameColor,     TextColor.fromRgb(0xD9D0D5)); }
    public TextColor getStaffMessageColor()  { return resolveColor(staffMessageColor,  TextColor.fromRgb(0xFF5555)); }
    public TextColor getTimestampColor()     { return resolveColor(timestampColor,      TextColor.fromRgb(0xB0C4DE)); }
    public TextColor getMentionColor()       { return resolveColor(mentionColor,        TextColor.fromRgb(0xFFC64C)); }
    public TextColor getAfkSuffixColor()     { return resolveColor(afkSuffixColor,      TextColor.fromRgb(0x808080)); }
}
