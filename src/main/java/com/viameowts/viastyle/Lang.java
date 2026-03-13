package com.viameowts.viastyle;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

/**
 * Bilingual message registry (en / ru).
 *
 * <p>Every user-facing string in the mod should go through {@code Lang.get(key)}
 * so that switching the language actually changes all output.</p>
 */
public class Lang {

    private static volatile String currentLang = "en";

    // ── Color palette ──────────────────────────────────────────────────────
    /** Top-level headings (panel title, etc.) */
    private static final TextColor COLOR_TOP    = TextColor.fromRgb(0xfabd39);
    /** Mid-level headings (page titles, labels) */
    private static final TextColor COLOR_MID    = TextColor.fromRgb(0xfaca64);
    /** Normal text (descriptions, field names) */
    private static final TextColor COLOR_NORMAL = TextColor.fromRgb(0xfcde9d);
    /** Positive / ON */
    private static final TextColor COLOR_GREEN  = TextColor.fromRgb(0x0ac467);
    /** Negative / OFF / errors */
    private static final TextColor COLOR_RED    = TextColor.fromRgb(0xcf324c);

    private static final Map<String, Text> enMessages = new HashMap<>();
    private static final Map<String, Text> ruMessages = new HashMap<>();

    public static void initialize() {
        // ── General errors ─────────────────────────────────────────────────
        put("error.player_only",
                styled("This command can only be run by a player.", Formatting.RED),
                styled("Эту команду может использовать только игрок.", Formatting.RED));
        put("error.player_not_found",
                styled("Player not found.", Formatting.RED),
                styled("Игрок не найден.", Formatting.RED));
        put("error.config_not_loaded",
                styled("Config not loaded.", Formatting.RED),
                styled("Конфиг не загружен.", Formatting.RED));

        // ── Chat Mode (/viaStyle) ────────────────────────────────────────────
        put("command.set.prefix_local",
                styled("Chat mode set: ", Formatting.GRAY)
                        .append(styled("Use '!' for Local chat", Formatting.GREEN))
                        .append(styled(".", Formatting.GRAY)),
                styled("Режим чата изменен: ", Formatting.GRAY)
                        .append(styled("Используйте '!' для Локального чата", Formatting.GREEN))
                        .append(styled(".", Formatting.GRAY)));
        put("command.set.prefix_global",
                styled("Chat mode set: ", Formatting.GRAY)
                        .append(styled("Use '!' for Global chat", Formatting.YELLOW))
                        .append(styled(" (default).", Formatting.GRAY)),
                styled("Режим чата изменен: ", Formatting.GRAY)
                        .append(styled("Используйте '!' для Глобального чата", Formatting.YELLOW))
                        .append(styled(" (по умолчанию).", Formatting.GRAY)));
        put("command.current.prefix_local",
                styled("Current mode: ", Formatting.GRAY)
                        .append(styled("'!' means Local chat", Formatting.GREEN)),
                styled("Текущий режим: ", Formatting.GRAY)
                        .append(styled("'!' означает Локальный чат", Formatting.GREEN)));
        put("command.current.prefix_global",
                styled("Current mode: ", Formatting.GRAY)
                        .append(styled("'!' means Global chat", Formatting.YELLOW)),
                styled("Текущий режим: ", Formatting.GRAY)
                        .append(styled("'!' означает Глобальный чат", Formatting.YELLOW)));
        put("command.lang.set",
                styled("Language set to: ", Formatting.GRAY),
                styled("Язык изменен на: ", Formatting.GRAY));
        put("command.lang.invalid",
                styled("Invalid language. Use 'en' or 'ru'.", Formatting.RED),
                styled("Неверный язык. Используйте 'en' или 'ru'.", Formatting.RED));
        put("command.lang.current",
                styled("Current language: ", Formatting.GRAY),
                styled("Текущий язык: ", Formatting.GRAY));

        // ── Chat ───────────────────────────────────────────────────────────
        put("chat.muted",
                styled("You are muted and cannot send messages.", Formatting.RED),
                styled("Вы заглушены и не можете отправлять сообщения.", Formatting.RED));
        put("chat.staff_no_permission",
                styled("You don't have permission to use staff chat.", Formatting.RED),
                styled("У вас нет разрешения на использование стаф-чата.", Formatting.RED));

        // ── Ignore ─────────────────────────────────────────────────────────
        put("ignore.added",
                styled("Now ignoring ", Formatting.YELLOW),
                styled("Теперь игнорируете ", Formatting.YELLOW));
        put("ignore.added_suffix",
                styled(". They can no longer PM you.", Formatting.YELLOW),
                styled(". Личные сообщения от них заблокированы.", Formatting.YELLOW));
        put("ignore.removed",
                styled("Unignored ", Formatting.GREEN),
                styled("Разблокирован ", Formatting.GREEN));
        put("ignore.not_ignoring",
                styled("You are not ignoring ", Formatting.GRAY),
                styled("Вы не игнорируете ", Formatting.GRAY));
        put("ignore.self",
                styled("You cannot ignore yourself.", Formatting.RED),
                styled("Вы не можете игнорировать себя.", Formatting.RED));
        put("ignore.list_empty",
                styled("Your ignore list is empty.", Formatting.GRAY),
                styled("Ваш список игнорирования пуст.", Formatting.GRAY));
        put("ignore.list_header",
                styled("Ignored players", Formatting.YELLOW),
                styled("Игнорируемые игроки", Formatting.YELLOW));
        put("ignore.offline",
                styled(" (offline)", Formatting.DARK_GRAY),
                styled(" (офлайн)", Formatting.DARK_GRAY));

        // ── Private Messages ───────────────────────────────────────────────
        put("pm.error.self",
                styled("You cannot message yourself.", Formatting.RED),
                styled("Вы не можете написать себе.", Formatting.RED));
        put("pm.error.no_reply",
                styled("No one to reply to.", Formatting.RED),
                styled("Некому ответить.", Formatting.RED));
        put("pm.error.offline",
                styled("That player is no longer online.", Formatting.RED),
                styled("Этот игрок больше не в сети.", Formatting.RED));
        put("pm.error.ignored",
                styled("That player is ignoring you.", Formatting.RED),
                styled("Этот игрок игнорирует вас.", Formatting.RED));

        // ── Mentions ───────────────────────────────────────────────────────
        put("mention.notify",
                styled("You were mentioned by ", Formatting.GOLD),
                styled("Вас упомянул ", Formatting.GOLD));

        // ── SocialSpy ──────────────────────────────────────────────────────
        put("spy.header",
                styled("SocialSpy", Formatting.GOLD),
                styled("SocialSpy", Formatting.GOLD));
        put("spy.master",
                styled("  Master: ", Formatting.GRAY),
                styled("  Общий: ", Formatting.GRAY));
        put("spy.enabled_all",
                styled("SocialSpy ", Formatting.GOLD)
                        .append(styled("enabled", Formatting.GREEN))
                        .append(styled(" for all channels.", Formatting.GOLD)),
                styled("SocialSpy ", Formatting.GOLD)
                        .append(styled("включен", Formatting.GREEN))
                        .append(styled(" для всех каналов.", Formatting.GOLD)));
        put("spy.disabled_all",
                styled("SocialSpy ", Formatting.GOLD)
                        .append(styled("disabled", Formatting.RED))
                        .append(styled(".", Formatting.GOLD)),
                styled("SocialSpy ", Formatting.GOLD)
                        .append(styled("выключен", Formatting.RED))
                        .append(styled(".", Formatting.GOLD)));
        put("spy.channel_unknown",
                styled("Unknown channel. Use: local, global, staff, pm", Formatting.RED),
                styled("Неизвестный канал. Используйте: local, global, staff, pm", Formatting.RED));
        put("spy.click_toggle",
                styled("Click to toggle", Formatting.YELLOW),
                styled("Нажмите для переключения", Formatting.YELLOW));
        put("spy.click_enable_all",
                styled("Click to enable all", Formatting.GREEN),
                styled("Нажмите чтобы включить все", Formatting.GREEN));
        put("spy.click_disable_all",
                styled("Click to disable all", Formatting.RED),
                styled("Нажмите чтобы выключить все", Formatting.RED));

        // ── Admin Panel ────────────────────────────────────────────────────
        put("panel.title",
                hex("viaStyle Admin Panel", COLOR_TOP),
                hex("Панель управления viaStyle", COLOR_TOP));
        put("panel.page.chat",
                hex("Chat Settings", COLOR_MID),
                hex("Настройки чата", COLOR_MID));
        put("panel.page.timestamp",
                hex("Timestamp", COLOR_MID),
                hex("Метка времени", COLOR_MID));
        put("panel.page.integrations",
                hex("Integrations", COLOR_MID),
                hex("Интеграции", COLOR_MID));
        put("panel.page.nickcolor",
                hex("Nick Colour & Nametag", COLOR_MID),
                hex("Цвет ника и неймтег", COLOR_MID));
        put("panel.page.tablist",
                hex("Tab List", COLOR_MID),
                hex("Таб-лист", COLOR_MID));
        put("panel.page.pm",
                hex("Private Messages", COLOR_MID),
                hex("Личные сообщения", COLOR_MID));
        put("panel.page.mentions",
                hex("Mentions", COLOR_MID),
                hex("Упоминания", COLOR_MID));
        put("panel.page.viasuper",
                hex("ViaSuper", COLOR_MID),
                hex("ViaSuper", COLOR_MID));
        put("panel.page.joinleave",
                hex("Join / Leave", COLOR_MID),
                hex("Вход / Выход", COLOR_MID));
        put("panel.page.console",
                hex("Console Logging", COLOR_MID),
                hex("Логирование в консоль", COLOR_MID));
        put("panel.page.language",
                hex("Language", COLOR_MID),
                hex("Язык", COLOR_MID));
        put("panel.page.blockbot",
                hex("BlockBot / Discord", COLOR_MID),
                hex("BlockBot / Discord", COLOR_MID));
        put("panel.reload",
                hex("⟳ Reload Config", COLOR_GREEN),
                hex("⟳ Перезагрузить конфиг", COLOR_GREEN));
        put("panel.reload_hover",
                hex("Reload config from disk", COLOR_NORMAL),
                hex("Перезагрузить конфиг из файла", COLOR_NORMAL));
        put("panel.reload_done",
                hex("Config reloaded successfully.", COLOR_GREEN),
                hex("Конфиг успешно перезагружен.", COLOR_GREEN));
        put("panel.back",
                hex("◄ Back", COLOR_MID),
                hex("◄ Назад", COLOR_MID));
        put("panel.back_hover",
                hex("Back to main panel", COLOR_NORMAL),
                hex("Вернуться в главное меню", COLOR_NORMAL));
        put("panel.click_toggle",
                hex("Click to toggle", COLOR_NORMAL),
                hex("Нажмите для переключения", COLOR_NORMAL));
        put("panel.click_edit",
                hex("Click to edit", COLOR_NORMAL),
                hex("Нажмите для редактирования", COLOR_NORMAL));
        put("panel.saved",
                styled(" (saved)", Formatting.DARK_GRAY),
                styled(" (сохранено)", Formatting.DARK_GRAY));
        put("panel.field_not_boolean",
                hex("This field is not a boolean.", COLOR_RED),
                hex("Это поле не является переключателем.", COLOR_RED));
        put("panel.unknown_field",
                hex("Unknown config field.", COLOR_RED),
                hex("Неизвестное поле конфига.", COLOR_RED));
        put("panel.invalid_number",
                hex("Invalid number.", COLOR_RED),
                hex("Неверное число.", COLOR_RED));

        // ── Reload (/viaStyle reload) ────────────────────────────────────────
        put("reload.done",
                styled("[viaStyle] All configurations reloaded.", Formatting.GREEN),
                styled("[viaStyle] Все конфигурации перезагружены.", Formatting.GREEN));

        // ── NickColor ──────────────────────────────────────────────────────
        put("nickcolor.set",
                styled("Set nick colour for ", Formatting.GREEN),
                styled("Цвет ника установлен для ", Formatting.GREEN));
        put("nickcolor.removed",
                styled("Removed nick colour for ", Formatting.GREEN),
                styled("Цвет ника убран для ", Formatting.GREEN));
        put("nickcolor.reloaded",
                styled("Nick colours reloaded.", Formatting.GREEN),
                styled("Цвета ников перезагружены.", Formatting.GREEN));
        put("nickcolor.invalid_spec",
                styled("Invalid colour spec.", Formatting.RED),
                styled("Неверный формат цвета.", Formatting.RED));

        // ── Panel field descriptions (shown in hover tooltips) ─────────────
        // CHAT page
        put("panel.field.localChatRadius",
                hex("Distance (blocks) within which local chat is visible.", COLOR_NORMAL),
                hex("Расстояние (блоки), в пределах которого виден локальный чат.", COLOR_NORMAL));
        put("panel.field.localPrefix",
                hex("Prefix tag displayed before local chat messages.", COLOR_NORMAL),
                hex("Тег-префикс перед сообщениями локального чата.", COLOR_NORMAL));
        put("panel.field.localPrefixColor",
                hex("Color of the local chat prefix tag.", COLOR_NORMAL),
                hex("Цвет тега-префикса локального чата.", COLOR_NORMAL));
        put("panel.field.localNameColor",
                hex("Color of player names in local chat.", COLOR_NORMAL),
                hex("Цвет имён игроков в локальном чате.", COLOR_NORMAL));
        put("panel.field.localMessageColor",
                hex("Color of the message text in local chat.", COLOR_NORMAL),
                hex("Цвет текста сообщений в локальном чате.", COLOR_NORMAL));
        put("panel.field.localNooneHeard",
                hex("Show a hint to the sender when nobody is in range to receive the local message.", COLOR_NORMAL),
                hex("Показывать подсказку отправителю, если в радиусе никого нет.", COLOR_NORMAL));
        put("panel.field.localNooneHeardMessage",
                hex("Text shown to sender when nobody heard the local message.", COLOR_NORMAL),
                hex("Текст, который покажется отправителю, если никто не услышал.", COLOR_NORMAL));
        put("panel.field.globalTrigger",
                hex("Character that switches a message to global chat (e.g. '!').", COLOR_NORMAL),
                hex("Символ-триггер для отправки в глобальный чат (например '!').", COLOR_NORMAL));
        put("panel.field.globalPrefix",
                hex("Prefix tag displayed before global chat messages.", COLOR_NORMAL),
                hex("Тег-префикс перед сообщениями глобального чата.", COLOR_NORMAL));
        put("panel.field.globalPrefixColor",
                hex("Color of the global chat prefix tag.", COLOR_NORMAL),
                hex("Цвет тега-префикса глобального чата.", COLOR_NORMAL));
        put("panel.field.globalNameColor",
                hex("Color of player names in global chat.", COLOR_NORMAL),
                hex("Цвет имён игроков в глобальном чате.", COLOR_NORMAL));
        put("panel.field.globalMessageColor",
                hex("Color of the message text in global chat.", COLOR_NORMAL),
                hex("Цвет текста сообщений в глобальном чате.", COLOR_NORMAL));
        put("panel.field.staffTrigger",
                hex("Character that routes a message to staff-only chat (e.g. '\\').", COLOR_NORMAL),
                hex("Символ для отправки в стаф-чат (например '\\').", COLOR_NORMAL));
        put("panel.field.staffPrefix",
                hex("Prefix tag displayed before staff chat messages.", COLOR_NORMAL),
                hex("Тег-префикс перед сообщениями стаф-чата.", COLOR_NORMAL));
        put("panel.field.staffPrefixColor",
                hex("Color of the staff chat prefix tag.", COLOR_NORMAL),
                hex("Цвет тега-префикса стаф-чата.", COLOR_NORMAL));
        put("panel.field.staffNameColor",
                hex("Color of player names in staff chat.", COLOR_NORMAL),
                hex("Цвет имён игроков в стаф-чате.", COLOR_NORMAL));
        put("panel.field.staffMessageColor",
                hex("Color of the message text in staff chat.", COLOR_NORMAL),
                hex("Цвет текста сообщений в стаф-чате.", COLOR_NORMAL));
        // TIMESTAMP page
        put("panel.field.showTimestamp",
                hex("Show a time stamp before every chat message.", COLOR_NORMAL),
                hex("Показывать метку времени перед каждым сообщением.", COLOR_NORMAL));
        put("panel.field.timestampFormat",
                hex("Java DateTimeFormatter pattern (e.g. HH:mm, HH:mm:ss).", COLOR_NORMAL),
                hex("Шаблон времени Java DateTimeFormatter (например HH:mm).", COLOR_NORMAL));
        put("panel.field.timestampColor",
                hex("Color of the timestamp text.", COLOR_NORMAL),
                hex("Цвет текста метки времени.", COLOR_NORMAL));
        // INTEGRATIONS page
        put("panel.field.usePlaceholderApi",
                hex("Enable PlaceholderAPI (TextPlaceholderAPI) support for chat and tab.", COLOR_NORMAL),
                hex("Включить поддержку PlaceholderAPI (TextPlaceholderAPI) в чате и табе.", COLOR_NORMAL));
        put("panel.field.useBanHammer",
                hex("Enable BanHammer integration for mute checks.", COLOR_NORMAL),
                hex("Включить интеграцию с BanHammer для проверки заглушённых.", COLOR_NORMAL));
        put("panel.field.useLuckPerms",
                hex("Enable LuckPerms integration for prefixes and permissions.", COLOR_NORMAL),
                hex("Включить интеграцию с LuckPerms для префиксов и прав.", COLOR_NORMAL));
        put("panel.field.useScarpetEvents",
                hex("Emit viaStyle events to Scarpet scripts.", COLOR_NORMAL),
                hex("Отправлять события viaStyle в Scarpet-скрипты.", COLOR_NORMAL));
        // NICKCOLOR page
        put("panel.field.nickColorEnabled",
                hex("Master toggle — enable or disable the nick colour system entirely.", COLOR_NORMAL),
                hex("Главный переключатель цветных ников. Отключает всю систему.", COLOR_NORMAL));
        put("panel.field.nickColorInChat",
                hex("Apply each player's nick colour to their name in chat.", COLOR_NORMAL),
                hex("Применять цвет ника к имени в чате.", COLOR_NORMAL));
        put("panel.field.nickColorInTab",
                hex("Apply each player's nick colour to their name in the tab list.", COLOR_NORMAL),
                hex("Применять цвет ника в таб-листе.", COLOR_NORMAL));
        put("panel.field.nickColorInNametag",
                hex("Apply each player's nick colour to their above-head nametag.", COLOR_NORMAL),
                hex("Применять цвет ника в неймтеге над головой.", COLOR_NORMAL));
        put("panel.field.nametagShowLpPrefix",
                hex("Show the LuckPerms prefix in the above-head nametag.", COLOR_NORMAL),
                hex("Показывать префикс LuckPerms в неймтеге над головой.", COLOR_NORMAL));
        put("panel.field.nametagMode",
                hex("'team' = 16 vanilla colours via teams. 'display' = TextDisplay entity (full RGB).", COLOR_NORMAL),
                hex("'team' — 16 цветов через команды. 'display' — TextDisplay (полный RGB).", COLOR_NORMAL));
        put("panel.field.nametagColorStrategy",
                hex("'first' = use first gradient colour. 'average' = average of all gradient colours.", COLOR_NORMAL),
                hex("'first' — первый цвет градиента. 'average' — среднее всех цветов.", COLOR_NORMAL));
        put("panel.field.nametagOrphanScanEnabled",
                hex("Periodically scan worlds for leftover nametag display entities.", COLOR_NORMAL),
                hex("Периодически сканировать мир на \"сиротские\" неймтег-сущности.", COLOR_NORMAL));
        put("panel.field.nametagOrphanScanIntervalTicks",
                hex("Interval (ticks) between orphan scans. 200 ticks = 10 seconds.", COLOR_NORMAL),
                hex("Интервал (тики) между сканированиями. 200 тиков = 10 секунд.", COLOR_NORMAL));
        // TABLIST page
        put("panel.field.tabSortMode",
                hex("Tab list sort mode: 'normal', 'reverse' (high weight = top), or 'none' (disabled).", COLOR_NORMAL),
                hex("Режим сортировки таб-листа: 'normal', 'reverse' (выш. вес = выше) или 'none' (откл.).", COLOR_NORMAL));
        put("panel.field.tabSortSpectatorsToBottom",
                hex("Push players in spectator mode to the bottom of the tab list.", COLOR_NORMAL),
                hex("Переместить игроков в режиме наблюдателя в конец таб-листа.", COLOR_NORMAL));
        // PM page
        put("panel.field.pmAllowSelfMessage",
                hex("Allow players to send a private message to themselves.", COLOR_NORMAL),
                hex("Разрешить игрокам отправлять ЛС самим себе.", COLOR_NORMAL));
        put("panel.field.pmSenderFormat",
                hex("PM format shown to the sender. Placeholders: {receiver}, {message}.", COLOR_NORMAL),
                hex("Формат ЛС для отправителя. Плейсхолдеры: {receiver}, {message}.", COLOR_NORMAL));
        put("panel.field.pmReceiverFormat",
                hex("PM format shown to the receiver. Placeholders: {sender}, {message}.", COLOR_NORMAL),
                hex("Формат ЛС для получателя. Плейсхолдеры: {sender}, {message}.", COLOR_NORMAL));
        put("panel.field.pmColor",
                hex("Default color applied to private messages.", COLOR_NORMAL),
                hex("Цвет личных сообщений по умолчанию.", COLOR_NORMAL));
        // MENTIONS page
        put("panel.field.mentionsEnabled",
                hex("Enable @player mentions in chat (sound + action-bar notification).", COLOR_NORMAL),
                hex("Включить упоминания @игрок в чате (звук + уведомление на экране).", COLOR_NORMAL));
        put("panel.field.mentionSound",
                hex("Play a ping sound when a player is mentioned.", COLOR_NORMAL),
                hex("Воспроизводить звук при упоминании игрока.", COLOR_NORMAL));
        put("panel.field.mentionBold",
                hex("Display @mention tokens in bold text.", COLOR_NORMAL),
                hex("Выделять @упоминания жирным шрифтом.", COLOR_NORMAL));
        put("panel.field.mentionColor",
                hex("Highlight color for @mentions in chat (Formatting or #RRGGBB).", COLOR_NORMAL),
                hex("Цвет выделения @упоминаний (имя форматирования или #RRGGBB).", COLOR_NORMAL));
        // VIASUPER page
        put("panel.field.viaSuperWordSound",
                hex("Play a sound for each word sent via viaSuper display.", COLOR_NORMAL),
                hex("Воспроизводить звук для каждого слова в viaSuper.", COLOR_NORMAL));
        put("panel.field.viaSuperSubtitleLength",
                hex("Words with >= this many characters appear as subtitle; shorter ones as big title.", COLOR_NORMAL),
                hex("Слова длиной >= этого значения показываются как субтитр; короткие — как заголовок.", COLOR_NORMAL));
        // JOINLEAVE page
        put("panel.field.joinFormat",
                hex("Join message format. Placeholder: {name}. Legacy &-codes supported.", COLOR_NORMAL),
                hex("Формат сообщения входа. Плейсхолдер: {name}. Поддерживаются &-коды.", COLOR_NORMAL));
        put("panel.field.leaveFormat",
                hex("Leave message format. Placeholder: {name}. Legacy &-codes supported.", COLOR_NORMAL),
                hex("Формат сообщения выхода. Плейсхолдер: {name}. Поддерживаются &-коды.", COLOR_NORMAL));
        put("panel.field.firstJoinFormat",
                hex("Format for a player's very first join. Placeholder: {name}.", COLOR_NORMAL),
                hex("Формат первого входа игрока. Плейсхолдер: {name}.", COLOR_NORMAL));
        // CONSOLE page
        put("panel.field.logGlobalToConsole",
                hex("Log global chat messages to the server console.", COLOR_NORMAL),
                hex("Записывать сообщения глобального чата в консоль.", COLOR_NORMAL));
        put("panel.field.logLocalToConsole",
                hex("Log local chat messages to the server console.", COLOR_NORMAL),
                hex("Записывать сообщения локального чата в консоль.", COLOR_NORMAL));
        put("panel.field.logStaffToConsole",
                hex("Log staff chat messages to the server console.", COLOR_NORMAL),
                hex("Записывать сообщения стаф-чата в консоль.", COLOR_NORMAL));
        put("panel.field.logPrivatesToConsole",
                hex("Log private messages to the server console.", COLOR_NORMAL),
                hex("Записывать личные сообщения в консоль.", COLOR_NORMAL));
        // LANGUAGE page
        put("panel.field.defaultLanguage",
                hex("Interface language: 'en' (English) or 'ru' (Russian).", COLOR_NORMAL),
                hex("Язык интерфейса: 'en' (английский) или 'ru' (русский).", COLOR_NORMAL));
        // BLOCKBOT page
        put("panel.field.discordBridgeMode",
                hex("Discord bridge mode: 'auto' (auto-detect BlockBot) or 'none' (disabled).", COLOR_NORMAL),
                hex("Режим Discord-моста: 'auto' (автоопределение) или 'none' (отключено).", COLOR_NORMAL));
        put("panel.field.blockbotGlobalChannel",
                hex("BlockBot channel name used for global chat relay (default: 'chat').", COLOR_NORMAL),
                hex("Имя канала BlockBot для глобального чата (по умолч.: 'chat').", COLOR_NORMAL));
        put("panel.field.discordFormat",
                hex("Format for Discord→MC messages. Placeholders: {message}, {channel}.", COLOR_NORMAL),
                hex("Формат сообщений Discord→MC. Плейсхолдеры: {message}, {channel}.", COLOR_NORMAL));
        put("panel.field.discordPassthrough",
                hex("ON (default): BlockBot handles Discord→MC formatting natively. OFF: viaStyle applies discord_format instead.", COLOR_NORMAL),
                hex("ВКЛ (по умолчанию): BlockBot сам форматирует сообщения Discord→MC. ВЫКЛ: viaStyle применяет discord_format.", COLOR_NORMAL));
        put("panel.field.discordMentionPing",
                hex("Ping an in-game player with sound when @theirName appears in any broadcast message (including Discord).", COLOR_NORMAL),
                hex("Уведомить игрока звуком, если @имя упомянуто в любом сообщении (в том числе из Discord).", COLOR_NORMAL));
        put("panel.field.discordMentionMappings",
                hex("Manual MC-name to Discord ID mappings. Format: \"Name:DiscordId,Name2:Id2\"", COLOR_NORMAL),
                hex("Маппинг имён из игры в Discord ID. Формат: \"MCНик:ДискордID,Ник2:ID2\"", COLOR_NORMAL));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  API
    // ═══════════════════════════════════════════════════════════════════════

    public static Text get(String key) {
        Map<String, Text> messages = currentLang.equals("ru") ? ruMessages : enMessages;
        Text defaultText = enMessages.getOrDefault(key, Text.literal(key).formatted(Formatting.RED));
        return messages.getOrDefault(key, defaultText);
    }

    public static MutableText getMutable(String key) {
        return get(key).copy();
    }

    public static boolean setLang(String lang) {
        if (lang.equalsIgnoreCase("en") || lang.equalsIgnoreCase("ru")) {
            currentLang = lang.toLowerCase();
            viaStyle.LOGGER.info("[viaStyle] Language set to: {}", currentLang);
            return true;
        }
        return false;
    }

    public static String getCurrentLang() {
        return currentLang;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static void put(String key, Text en, Text ru) {
        enMessages.put(key, en);
        ruMessages.put(key, ru);
    }

    private static MutableText styled(String text, Formatting... formats) {
        return Text.literal(text).formatted(formats);
    }

    /** Creates a MutableText with a hex TextColor (no bold). */
    private static MutableText hex(String text, TextColor color) {
        return Text.literal(text).styled(s -> s.withColor(color));
    }

    /** Public accessors for the palette — used by AdminPanelCommand. */
    public static TextColor colorTop()    { return COLOR_TOP; }
    public static TextColor colorMid()    { return COLOR_MID; }
    public static TextColor colorNormal() { return COLOR_NORMAL; }
    public static TextColor colorGreen()  { return COLOR_GREEN; }
    public static TextColor colorRed()    { return COLOR_RED; }
}
