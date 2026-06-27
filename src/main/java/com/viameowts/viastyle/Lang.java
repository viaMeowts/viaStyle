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
        private static final TextColor COLOR_TOP    = TextColor.fromRgb(0xFFC64C);
    /** Mid-level headings (page titles, labels) */
        private static final TextColor COLOR_MID    = TextColor.fromRgb(0xFCDE9D);
    /** Normal text (descriptions, field names) */
        private static final TextColor COLOR_NORMAL = TextColor.fromRgb(0xD9D0D5);
    /** Positive / ON */
        private static final TextColor COLOR_GREEN  = TextColor.fromRgb(0x98FB98);
    /** Negative / OFF / errors */
        private static final TextColor COLOR_RED    = TextColor.fromRgb(0xFF5555);

    private static final Map<String, Text> enMessages = new HashMap<>();
    private static final Map<String, Text> ruMessages = new HashMap<>();
    private static final Map<String, String> enRaw = new HashMap<>();
    private static final Map<String, String> ruRaw = new HashMap<>();

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
        put("chat.placeholder.cooldown",
                styled("You can use chat placeholders again in ", Formatting.RED),
                styled("Вы сможете снова использовать чат-плейсхолдеры через ", Formatting.RED));
        put("chat.placeholder.no_permission",
                styled("You don't have permission to use this chat placeholder.", Formatting.RED),
                styled("У вас нет прав на использование этого чат-плейсхолдера.", Formatting.RED));
        put("chat.placeholder.no_item",
                styled("You have no item in hand to share.", Formatting.RED),
                styled("У вас нет предмета в руках для отправки.", Formatting.RED));
        put("chat.placeholder.view_expired",
                styled("This shared view has expired or does not exist.", Formatting.RED),
                styled("Этот общий просмотр истек или не существует.", Formatting.RED));

        // ── Broadcast (/bc) ──────────────────────────────────────────────
        put("broadcast.cooldown",
                styled("[viaStyle] Cooldown: ", Formatting.RED),
                styled("[viaStyle] Кулдаун: ", Formatting.RED));
        put("broadcast.cooldown_suffix",
                styled("s", Formatting.RED),
                styled("с", Formatting.RED));
        put("broadcast.feedback_prefix",
                styled("[viaStyle] Broadcast sent to ", Formatting.GREEN),
                styled("[viaStyle] Объявление отправлено ", Formatting.GREEN));
        put("broadcast.feedback_suffix",
                styled(" player(s).", Formatting.GREEN),
                styled(" игрок(ам).", Formatting.GREEN));

        // ── JoinLeave self command ───────────────────────────────────────
        put("joinleave.self.show_join",
                styled("[joinleave] join: ", Formatting.YELLOW),
                styled("[joinleave] вход: ", Formatting.YELLOW));
        put("joinleave.self.show_leave",
                styled("[joinleave] leave: ", Formatting.YELLOW),
                styled("[joinleave] выход: ", Formatting.YELLOW));
        put("joinleave.self.default_marker",
                styled("(default)", Formatting.DARK_GRAY),
                styled("(по умолчанию)", Formatting.DARK_GRAY));
        put("joinleave.self.saved_join",
                styled("[joinleave] Saved personal join format.", Formatting.GREEN),
                styled("[joinleave] Личный формат входа сохранен.", Formatting.GREEN));
        put("joinleave.self.saved_leave",
                styled("[joinleave] Saved personal leave format.", Formatting.GREEN),
                styled("[joinleave] Личный формат выхода сохранен.", Formatting.GREEN));
        put("joinleave.self.updated",
                styled("[joinleave] Personal override updated.", Formatting.GREEN),
                styled("[joinleave] Личные переопределения обновлены.", Formatting.GREEN));
        put("joinleave.admin.player_not_found",
                styled("[joinleave] Player not found online.", Formatting.RED),
                styled("[joinleave] Игрок не найден в онлайне.", Formatting.RED));
        put("joinleave.admin.show_player_join",
                styled("[joinleave] player join ", Formatting.YELLOW),
                styled("[joinleave] вход игрока ", Formatting.YELLOW));
        put("joinleave.admin.show_player_leave",
                styled("[joinleave] player leave ", Formatting.YELLOW),
                styled("[joinleave] выход игрока ", Formatting.YELLOW));
        put("joinleave.admin.show_group_join",
                styled("[joinleave] group join ", Formatting.YELLOW),
                styled("[joinleave] вход группы ", Formatting.YELLOW));
        put("joinleave.admin.show_group_leave",
                styled("[joinleave] group leave ", Formatting.YELLOW),
                styled("[joinleave] выход группы ", Formatting.YELLOW));
        put("joinleave.admin.saved_player_join",
                styled("[joinleave] Saved player join format for ", Formatting.GREEN),
                styled("[joinleave] Формат входа сохранен для игрока ", Formatting.GREEN));
        put("joinleave.admin.saved_player_leave",
                styled("[joinleave] Saved player leave format for ", Formatting.GREEN),
                styled("[joinleave] Формат выхода сохранен для игрока ", Formatting.GREEN));
        put("joinleave.admin.saved_group_join",
                styled("[joinleave] Saved group join format for ", Formatting.GREEN),
                styled("[joinleave] Формат входа сохранен для группы ", Formatting.GREEN));
        put("joinleave.admin.saved_group_leave",
                styled("[joinleave] Saved group leave format for ", Formatting.GREEN),
                styled("[joinleave] Формат выхода сохранен для группы ", Formatting.GREEN));
        put("joinleave.admin.removed_player",
                styled("[joinleave] Removed player overrides for ", Formatting.GREEN),
                styled("[joinleave] Переопределения игрока удалены для ", Formatting.GREEN));
        put("joinleave.admin.removed_group",
                styled("[joinleave] Removed group overrides for ", Formatting.GREEN),
                styled("[joinleave] Переопределения группы удалены для ", Formatting.GREEN));

        // ── ViaSuper ─────────────────────────────────────────────────────
        put("viasuper.sent_prefix",
                styled("[viaStyle] Title sent (", Formatting.GREEN),
                styled("[viaStyle] Заголовок отправлен (", Formatting.GREEN));
        put("viasuper.sent_words_suffix",
                styled(" words) to ", Formatting.GREEN),
                styled(" слов) для ", Formatting.GREEN));
        put("viasuper.sent_players_suffix",
                styled(" player(s).", Formatting.GREEN),
                styled(" игрок(ов).", Formatting.GREEN));

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
        put("pm.console_name",
                Text.literal("Console"),
                Text.literal("Консоль"));

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
        put("spy.state_on_tag",
                styled("[ON]", Formatting.GREEN),
                styled("[ВКЛ]", Formatting.GREEN));
        put("spy.state_off_tag",
                styled("[OFF]", Formatting.RED),
                styled("[ВЫКЛ]", Formatting.RED));
        put("spy.state_on",
                styled("ON", Formatting.GREEN),
                styled("ВКЛ", Formatting.GREEN));
        put("spy.state_off",
                styled("OFF", Formatting.RED),
                styled("ВЫКЛ", Formatting.RED));
        put("spy.toggle_prefix",
                styled("SocialSpy ", Formatting.GOLD),
                styled("SocialSpy ", Formatting.GOLD));

        // ── Admin Panel ────────────────────────────────────────────────────
        put("panel.title",
                hex("viaStyle Admin Panel", COLOR_TOP),
                hex("Панель управления viaStyle", COLOR_TOP));
        put("panel.page.local",
                hex("Local Chat", COLOR_MID),
                hex("Локальный чат", COLOR_MID));
        put("panel.page.global",
                hex("Global Chat", COLOR_MID),
                hex("Глобальный чат", COLOR_MID));
        put("panel.page.staff",
                hex("Staff Chat", COLOR_MID),
                hex("Стаф-чат", COLOR_MID));
        put("panel.page.chat_format",
                hex("Chat Formatting", COLOR_MID),
                hex("Форматирование чата", COLOR_MID));
        put("panel.page.timestamp",
                hex("Timestamp", COLOR_MID),
                hex("Метка времени", COLOR_MID));
        put("panel.page.integrations",
                hex("Integrations", COLOR_MID),
                hex("Интеграции", COLOR_MID));
        put("panel.page.nickcolor",
                hex("Nick Colors", COLOR_MID),
                hex("Цвета ников", COLOR_MID));
        put("panel.page.nametag",
                hex("Nametag", COLOR_MID),
                hex("Неймтег", COLOR_MID));
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
        put("panel.page.afk",
                hex("AFK", COLOR_MID),
                hex("AFK", COLOR_MID));
        put("panel.page.joinleave",
                hex("Join / Leave", COLOR_MID),
                hex("Вход / Выход", COLOR_MID));
        put("panel.page.joinleave_overrides",
                hex("Join/Leave Overrides", COLOR_MID),
                hex("Переопределения входа/выхода", COLOR_MID));
        put("panel.page.console",
                hex("Console Logging", COLOR_MID),
                hex("Логирование в консоль", COLOR_MID));
        put("panel.page.language",
                hex("Language", COLOR_MID),
                hex("Язык", COLOR_MID));
        put("panel.page.blockbot",
                hex("BlockBot / Discord", COLOR_MID),
                hex("BlockBot / Discord", COLOR_MID));
        put("panel.page.broadcast",
                hex("Broadcast", COLOR_MID),
                hex("Объявления", COLOR_MID));
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

        // ── AFK (raw strings — color applied from config) ────────────────────
        putRaw("afk.self_enabled", "You are now AFK.", "Теперь вы AFK.");
        putRaw("afk.self_disabled", "You are no longer AFK.", "Вы больше не AFK.");
        putRaw("afk.other_set", "Set ", "Теперь AFK: ");
        putRaw("afk.other_unset", "Removed AFK status from ", "Статус AFK снят с ");

        // ── PM Sound ─────────────────────────────────────────────────────────
        putRaw("msound.enabled", "PM sound enabled.", "Звук ЛС включён.");
        putRaw("msound.disabled", "PM sound disabled.", "Звук ЛС выключен.");
        putRaw("msound.status_on", "PM sound is currently enabled.", "Звук ЛС сейчас включён.");
        putRaw("msound.status_off", "PM sound is currently disabled.", "Звук ЛС сейчас выключен.");

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
        put("panel.field.pmBanHammerMute",
                hex("Check BanHammer mute status on private messages.", COLOR_NORMAL),
                hex("Проверять статус мута BanHammer в личных сообщениях.", COLOR_NORMAL));
        put("panel.field.pmSenderFormat",
                hex("PM format shown to the sender. Placeholders: {receiver}, {message}.", COLOR_NORMAL),
                hex("Формат ЛС для отправителя. Плейсхолдеры: {receiver}, {message}.", COLOR_NORMAL));
        put("panel.field.pmReceiverFormat",
                hex("PM format shown to the receiver. Placeholders: {sender}, {message}.", COLOR_NORMAL),
                hex("Формат ЛС для получателя. Плейсхолдеры: {sender}, {message}.", COLOR_NORMAL));
        put("panel.field.pmColor",
                hex("Default color applied to private messages.", COLOR_NORMAL),
                hex("Цвет личных сообщений по умолчанию.", COLOR_NORMAL));
        put("panel.field.pmSoundEnabled",
                hex("Play a sound to the receiver when they get a PM.", COLOR_NORMAL),
                hex("Проигрывать звук получателю при получении ЛС.", COLOR_NORMAL));
        put("panel.field.pmSoundId",
                hex("Sound ID for incoming PM notification.", COLOR_NORMAL),
                hex("ID звука для уведомления о ЛС.", COLOR_NORMAL));
        put("panel.field.pmSoundVolume",
                hex("Volume of the PM notification sound.", COLOR_NORMAL),
                hex("Громкость звука ЛС.", COLOR_NORMAL));
        put("panel.field.pmSoundPitch",
                hex("Pitch of the PM notification sound.", COLOR_NORMAL),
                hex("Высота звука ЛС.", COLOR_NORMAL));
        // MENTIONS page
        put("panel.field.mentionsEnabled",
                hex("Enable @player mentions in chat (sound + action-bar notification).", COLOR_NORMAL),
                hex("Включить упоминания @игрок в чате (звук + уведомление на экране).", COLOR_NORMAL));
        put("panel.field.mentionSound",
                hex("Play a ping sound when a player is mentioned.", COLOR_NORMAL),
                hex("Воспроизводить звук при упоминании игрока.", COLOR_NORMAL));
        put("panel.field.mentionBold",
                hex("Legacy toggle for bold @mentions (style guide keeps this off).", COLOR_NORMAL),
                hex("Устаревший переключатель жирных @упоминаний (по стилю лучше держать выключенным).", COLOR_NORMAL));
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
                hex("Join message format. Placeholder: {name}. Use MiniMessage tags (e.g. <#98FB98>, <bold>).", COLOR_NORMAL),
                hex("Формат сообщения входа. Плейсхолдер: {name}. Используйте MiniMessage-теги (например <#98FB98>, <bold>).", COLOR_NORMAL));
        put("panel.field.leaveFormat",
                hex("Leave message format. Placeholder: {name}. Use MiniMessage tags (e.g. <#FF9292>, <italic>).", COLOR_NORMAL),
                hex("Формат сообщения выхода. Плейсхолдер: {name}. Используйте MiniMessage-теги (например <#FF9292>, <italic>).", COLOR_NORMAL));
        put("panel.field.firstJoinFormat",
                hex("Format for a player's very first join. Placeholder: {name}.", COLOR_NORMAL),
                hex("Формат первого входа игрока. Плейсхолдер: {name}.", COLOR_NORMAL));
        put("panel.field.joinLeavePanelPlayerTarget",
                hex("Target player (online name or UUID) for per-player join/leave override editing.", COLOR_NORMAL),
                hex("Целевой игрок (онлайн-ник или UUID) для редактирования личных форматов входа/выхода.", COLOR_NORMAL));
        put("panel.field.joinLeavePanelPlayerJoinFormat",
                hex("Per-player join format for selected player. Empty value removes player join override.", COLOR_NORMAL),
                hex("Формат входа для выбранного игрока. Пустое значение удаляет личное переопределение входа.", COLOR_NORMAL));
        put("panel.field.joinLeavePanelPlayerLeaveFormat",
                hex("Per-player leave format for selected player. Empty value removes player leave override.", COLOR_NORMAL),
                hex("Формат выхода для выбранного игрока. Пустое значение удаляет личное переопределение выхода.", COLOR_NORMAL));
        put("panel.field.joinLeavePanelGroupTarget",
                hex("LuckPerms group name for group-based join/leave override editing.", COLOR_NORMAL),
                hex("Имя группы LuckPerms для редактирования групповых форматов входа/выхода.", COLOR_NORMAL));
        put("panel.field.joinLeavePanelGroupJoinFormat",
                hex("Per-group join format for selected group. Empty value removes group join override.", COLOR_NORMAL),
                hex("Формат входа для выбранной группы. Пустое значение удаляет групповое переопределение входа.", COLOR_NORMAL));
        put("panel.field.joinLeavePanelGroupLeaveFormat",
                hex("Per-group leave format for selected group. Empty value removes group leave override.", COLOR_NORMAL),
                hex("Формат выхода для выбранной группы. Пустое значение удаляет групповое переопределение выхода.", COLOR_NORMAL));
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
        // BROADCAST page
        put("panel.field.broadcastEnabled",
                hex("Enable or disable /bc broadcast command.", COLOR_NORMAL),
                hex("Включить или отключить команду рассылки /bc.", COLOR_NORMAL));
        put("panel.field.broadcastPermission",
                hex("Permission node required to use /bc.", COLOR_NORMAL),
                hex("Права (permission), необходимые для использования /bc.", COLOR_NORMAL));
        put("panel.field.broadcastCooldownSeconds",
                hex("Cooldown in seconds between broadcasts per player.", COLOR_NORMAL),
                hex("Кулдаун в секундах между рассылками для каждого игрока.", COLOR_NORMAL));
        put("panel.field.broadcastHeaderFormat",
                hex("Broadcast header format. Tokens: {sender}, {message}.", COLOR_NORMAL),
                hex("Формат заголовка рассылки. Токены: {sender}, {message}.", COLOR_NORMAL));
        put("panel.field.broadcastMessageFormat",
                hex("Broadcast body format. Tokens: {sender}, {message}.", COLOR_NORMAL),
                hex("Формат текста рассылки. Токены: {sender}, {message}.", COLOR_NORMAL));
        put("panel.field.broadcastConsoleSenderName",
                hex("Display name used as {sender} when /bc is executed from console.", COLOR_NORMAL),
                hex("Имя, используемое как {sender}, когда /bc выполняется из консоли.", COLOR_NORMAL));
        put("panel.field.broadcastCooldownFormat",
                hex("Cooldown warning format. Token: {seconds}.", COLOR_NORMAL),
                hex("Формат предупреждения о кулдауне. Токен: {seconds}.", COLOR_NORMAL));
        put("panel.field.broadcastFeedbackFormat",
                hex("Feedback format after successful /bc. Tokens: {count}, {sender}, {message}.", COLOR_NORMAL),
                hex("Формат подтверждения после успешного /bc. Токены: {count}, {sender}, {message}.", COLOR_NORMAL));
        put("panel.field.broadcastLogFormat",
                hex("Server log message format. Tokens: {count}, {sender}, {message}.", COLOR_NORMAL),
                hex("Формат сообщения в лог сервера. Токены: {count}, {sender}, {message}.", COLOR_NORMAL));
        put("panel.field.broadcastSoundEnabled",
                hex("Play configured sound for recipients of /bc.", COLOR_NORMAL),
                hex("Воспроизводить настроенный звук получателям /bc.", COLOR_NORMAL));
        put("panel.field.broadcastSoundId",
                hex("Sound ID used by /bc (example: minecraft:block.note_block.bell).", COLOR_NORMAL),
                hex("ID звука для /bc (пример: minecraft:block.note_block.bell).", COLOR_NORMAL));
        put("panel.field.broadcastSoundVolume",
                hex("Volume of /bc sound.", COLOR_NORMAL),
                hex("Громкость звука /bc.", COLOR_NORMAL));
        put("panel.field.broadcastSoundPitch",
                hex("Pitch of /bc sound.", COLOR_NORMAL),
                hex("Высота тона звука /bc.", COLOR_NORMAL));
        put("panel.field.broadcastSendFeedback",
                hex("Send feedback message to command source after /bc.", COLOR_NORMAL),
                hex("Отправлять подтверждение источнику команды после /bc.", COLOR_NORMAL));
        // Viasuper page
        put("panel.field.viaSuperTitleFormat",
                hex("MiniMessage format for short words shown as big title. {word}", COLOR_NORMAL),
                hex("MiniMessage-формат коротких слов (большой заголовок). {word}", COLOR_NORMAL));
        put("panel.field.viaSuperSubtitleFormat",
                hex("MiniMessage format for long words shown as subtitle. {word}", COLOR_NORMAL),
                hex("MiniMessage-формат длинных слов (субтитр). {word}", COLOR_NORMAL));
        // AFK page
        put("panel.field.afkEnabled",
                hex("Enable automatic AFK detection for inactive players.", COLOR_NORMAL),
                hex("Включить автоматическое определение AFK.", COLOR_NORMAL));
        put("panel.field.afkTimeout",
                hex("Seconds of inactivity before a player is marked AFK.", COLOR_NORMAL),
                hex("Секунд бездействия до маркировки игрока AFK.", COLOR_NORMAL));
        put("panel.field.afkSuffix",
                hex("Suffix appended to player name when AFK. Supports MiniMessage tags.", COLOR_NORMAL),
                hex("Суффикс к имени в AFK. Поддерживает MiniMessage.", COLOR_NORMAL));
        put("panel.field.afkSuffixColor",
                hex("Color override for the AFK suffix (if not specified in suffix field).", COLOR_NORMAL),
                hex("Цвет суффикса AFK (если не указан в самом суффиксе).", COLOR_NORMAL));
        put("panel.field.afkNameColor",
                hex("Override player name colour when AFK. Empty = keep existing nick colour.", COLOR_NORMAL),
                hex("Переопределить цвет ника в AFK. Пусто = оставить текущий цвет.", COLOR_NORMAL));
        put("panel.field.afkPermission",
                hex("Permission node required to use /afk command.", COLOR_NORMAL),
                hex("Права для использования /afk.", COLOR_NORMAL));
        put("panel.field.afkBypassPermission",
                hex("Players with this permission are exempt from automatic AFK detection.", COLOR_NORMAL),
                hex("Игроки с этим правом не получают AFK автоматически.", COLOR_NORMAL));
        put("panel.field.afkExemptPlayers",
                hex("Comma-separated UUIDs of players exempt from auto-AFK.", COLOR_NORMAL),
                hex("UUID игроков через запятую, исключённых из авто-AFK.", COLOR_NORMAL));
        put("panel.field.afkEnabledColor",
                hex("Text color for AFK enabled/joined messages (hex).", COLOR_NORMAL),
                hex("Цвет текста для сообщений о включении AFK (hex).", COLOR_NORMAL));
        put("panel.field.afkDisabledColor",
                hex("Text color for AFK disabled/left messages (hex).", COLOR_NORMAL),
                hex("Цвет текста для сообщений о выходе из AFK (hex).", COLOR_NORMAL));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  API
    // ═══════════════════════════════════════════════════════════════════════

    public static Text get(String key) {
        Map<String, Text> messages = currentLang.equals("ru") ? ruMessages : enMessages;
        Text defaultText = enMessages.getOrDefault(key, hex(key, COLOR_RED));
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

    /** Stores a raw (unformatted) string, used for messages whose color is applied at runtime. */
    private static void putRaw(String key, String en, String ru) {
        enRaw.put(key, en);
        ruRaw.put(key, ru);
    }

    /** Returns the raw string for a key (fallback: the key itself). */
    public static String getRaw(String key) {
        Map<String, String> msgs = currentLang.equals("ru") ? ruRaw : enRaw;
        String defaultText = enRaw.getOrDefault(key, key);
        return msgs.getOrDefault(key, defaultText);
    }

    /** Returns a colored {@code MutableText} from a raw string entry. */
    public static MutableText getColored(String key, TextColor color) {
        return Text.literal(getRaw(key)).styled(s -> s.withColor(color));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static void put(String key, Text en, Text ru) {
        enMessages.put(key, en);
        ruMessages.put(key, ru);
    }

        private static MutableText styled(String text, Formatting... formats) {
                TextColor color = COLOR_NORMAL;
                for (Formatting fmt : formats) {
                        if (fmt == null) continue;
                        switch (fmt) {
                                case RED, DARK_RED -> color = COLOR_RED;
                                case GREEN -> color = COLOR_GREEN;
                                case YELLOW, GOLD -> color = COLOR_MID;
                                case DARK_GRAY -> color = TextColor.fromRgb(0xB0C4DE);
                                case AQUA -> color = TextColor.fromRgb(0xC9E8F5);
                                case LIGHT_PURPLE -> color = TextColor.fromRgb(0xC8A2C8);
                                case WHITE, GRAY -> color = COLOR_NORMAL;
                                default -> {
                                }
                        }
                }
                TextColor selectedColor = color;
                return Text.literal(text).styled(s -> s.withColor(selectedColor));
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
