package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.*;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Field;

/**
 * In-game clickable config panel for admins.
 *
 * <pre>
 * /viapanel                          — open the main panel
 * /viapanel &lt;page&gt;                  — open a specific page
 * /viapanel toggle &lt;field&gt;          — toggle a boolean config value
 * /viapanel set &lt;field&gt; &lt;value&gt;    — set a string/number config value
 * /viapanel reload                   — reload config from disk
 * </pre>
 *
 * Permission: viastyle.panel (or OP level 2)
 */
public class AdminPanelCommand {

    private static final String CMD = "/viapanel";

    /** Config pages and their fields. */
    private enum Page {
        MAIN("panel.title", null),
        CHAT("panel.page.chat", new String[]{"localChatRadius", "localPrefix", "localPrefixColor", "localNameColor", "localMessageColor",
                "localNooneHeard", "localNooneHeardMessage",
                "globalTrigger", "globalPrefix", "globalPrefixColor", "globalNameColor", "globalMessageColor",
                "staffTrigger", "staffPrefix", "staffPrefixColor", "staffNameColor", "staffMessageColor"}),
        TIMESTAMP("panel.page.timestamp", new String[]{"showTimestamp", "timestampFormat", "timestampColor"}),
        INTEGRATIONS("panel.page.integrations", new String[]{"usePlaceholderApi", "useBanHammer", "useLuckPerms", "useScarpetEvents"}),
        NICKCOLOR("panel.page.nickcolor", new String[]{"nickColorEnabled", "nickColorInChat", "nickColorInTab", "nickColorInNametag",
                "nametagShowLpPrefix", "nametagMode", "nametagColorStrategy",
                "nametagOrphanScanEnabled", "nametagOrphanScanIntervalTicks"}),
        TABLIST("panel.page.tablist", new String[]{"tabSortMode", "tabSortSpectatorsToBottom"}),
        PM("panel.page.pm", new String[]{"pmAllowSelfMessage", "pmSenderFormat", "pmReceiverFormat", "pmColor"}),
        MENTIONS("panel.page.mentions", new String[]{"mentionsEnabled", "mentionSound", "mentionBold", "mentionColor"}),
        VIASUPER("panel.page.viasuper", new String[]{"viaSuperWordSound", "viaSuperSubtitleLength"}),
        JOINLEAVE("panel.page.joinleave", new String[]{"joinFormat", "leaveFormat", "firstJoinFormat"}),
        CONSOLE("panel.page.console", new String[]{"logGlobalToConsole", "logLocalToConsole", "logStaffToConsole", "logPrivatesToConsole"}),
        LANGUAGE("panel.page.language", new String[]{"defaultLanguage"}),
        BLOCKBOT("panel.page.blockbot", new String[]{"discordBridgeMode", "blockbotGlobalChannel", "discordFormat", "discordPassthrough", "discordMentionPing"});

        /** Lang key for the page title. */
        final String langKey;
        final String[] fields;

        Page(String langKey, String[] fields) {
            this.langKey = langKey;
            this.fields = fields;
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("viapanel")
                .requires(AdminPanelCommand::hasPermission)
                .executes(ctx -> showPage(ctx, Page.MAIN))
                .then(CommandManager.literal("chat")
                        .executes(ctx -> showPage(ctx, Page.CHAT)))
                .then(CommandManager.literal("timestamp")
                        .executes(ctx -> showPage(ctx, Page.TIMESTAMP)))
                .then(CommandManager.literal("integrations")
                        .executes(ctx -> showPage(ctx, Page.INTEGRATIONS)))
                .then(CommandManager.literal("nickcolor")
                        .executes(ctx -> showPage(ctx, Page.NICKCOLOR)))
                .then(CommandManager.literal("tablist")
                        .executes(ctx -> showPage(ctx, Page.TABLIST)))
                .then(CommandManager.literal("pm")
                        .executes(ctx -> showPage(ctx, Page.PM)))
                .then(CommandManager.literal("mentions")
                        .executes(ctx -> showPage(ctx, Page.MENTIONS)))
                .then(CommandManager.literal("viasuper")
                        .executes(ctx -> showPage(ctx, Page.VIASUPER)))
                .then(CommandManager.literal("joinleave")
                        .executes(ctx -> showPage(ctx, Page.JOINLEAVE)))
                .then(CommandManager.literal("console")
                        .executes(ctx -> showPage(ctx, Page.CONSOLE)))
                .then(CommandManager.literal("language")
                        .executes(ctx -> showPage(ctx, Page.LANGUAGE)))
                .then(CommandManager.literal("blockbot")
                        .executes(ctx -> showPage(ctx, Page.BLOCKBOT)))
                .then(CommandManager.literal("toggle")
                        .then(CommandManager.argument("field", StringArgumentType.word())
                                .executes(AdminPanelCommand::toggleField)))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("field", StringArgumentType.word())
                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(AdminPanelCommand::setField))))
                .then(CommandManager.literal("reload")
                        .executes(AdminPanelCommand::reloadConfig))
        );
    }

    private static boolean hasPermission(ServerCommandSource source) {
        if (LuckPermsHelper.hasOpLevel(source, 2)) return true;
        if (source.getEntity() instanceof ServerPlayerEntity p) {
            return LuckPermsHelper.hasPermission(p.getUuid(), "viastyle.panel");
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Pages
    // ═══════════════════════════════════════════════════════════════════════

    private static int showPage(CommandContext<ServerCommandSource> ctx, Page page) {
        if (page == Page.MAIN) return showMainPage(ctx);
        return showConfigPage(ctx, page);
    }

    private static int showMainPage(CommandContext<ServerCommandSource> ctx) {
        send(ctx, Text.literal(""));
        send(ctx, Text.literal("  ").append(Lang.get("panel.title")));
        send(ctx, Text.literal(""));

        Page[] subPages = {Page.CHAT, Page.TIMESTAMP, Page.INTEGRATIONS,
                Page.NICKCOLOR, Page.TABLIST, Page.PM, Page.MENTIONS,
                Page.VIASUPER, Page.JOINLEAVE, Page.CONSOLE, Page.LANGUAGE, Page.BLOCKBOT};

        for (Page p : subPages) {
            MutableText line = Text.literal("  ▸ ").styled(s -> s.withColor(Lang.colorNormal()));
            Text pageTitle = Lang.get(p.langKey);
            line.append(pageTitle.copy().styled(s -> s
                    .withColor(Lang.colorMid())
                    .withClickEvent(new ClickEvent.RunCommand(CMD + " " + p.name().toLowerCase()))
                    .withHoverEvent(new HoverEvent.ShowText(
                            pageTitle.copy().styled(st -> st.withColor(Lang.colorNormal()))))));
            send(ctx, line);
        }

        send(ctx, Text.literal(""));
        send(ctx, Text.literal("  ").append(
                Lang.getMutable("panel.reload").styled(s -> s
                        .withClickEvent(new ClickEvent.RunCommand(CMD + " reload"))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Lang.get("panel.reload_hover"))))));
        send(ctx, Text.literal(""));
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Config sub-page
    // ═══════════════════════════════════════════════════════════════════════

    private static int showConfigPage(CommandContext<ServerCommandSource> ctx, Page page) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) {
            ctx.getSource().sendError(Lang.get("error.config_not_loaded"));
            return 0;
        }

        send(ctx, Text.literal(""));
        send(ctx, Text.literal("  ").append(Lang.get(page.langKey)));
        send(ctx, Text.literal(""));

        if (page.fields != null) {
            for (String fieldName : page.fields) {
                try {
                    Field field = ViaStyleConfig.class.getField(fieldName);
                    Object value = field.get(cfg);
                    String displayName = toDisplayName(fieldName);
                    MutableText line = Text.literal("  ");

                    if (value instanceof Boolean bool) {
                        line.append(Text.literal(displayName + ": ").styled(s -> s.withColor(Lang.colorNormal())));
                        line.append(Text.literal(bool ? "[ON]" : "[OFF]").styled(s -> s
                                .withColor(bool ? Lang.colorGreen() : Lang.colorRed())
                                .withClickEvent(new ClickEvent.RunCommand(CMD + " toggle " + fieldName))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        fieldHover(fieldName, "panel.click_toggle", null)))));
                    } else if (value instanceof Double d) {
                        line.append(Text.literal(displayName + ": ").styled(s -> s.withColor(Lang.colorNormal())));
                        line.append(Text.literal(String.valueOf(d)).styled(s -> s
                                .withColor(Formatting.WHITE)
                                .withClickEvent(new ClickEvent.SuggestCommand(CMD + " set " + fieldName + " " + d))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        fieldHover(fieldName, "panel.click_edit", null)))));
                    } else if (value instanceof Integer i) {
                        line.append(Text.literal(displayName + ": ").styled(s -> s.withColor(Lang.colorNormal())));
                        line.append(Text.literal(String.valueOf(i)).styled(s -> s
                                .withColor(Formatting.WHITE)
                                .withClickEvent(new ClickEvent.SuggestCommand(CMD + " set " + fieldName + " " + i))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        fieldHover(fieldName, "panel.click_edit", null)))));
                    } else if (value instanceof String str) {
                        line.append(Text.literal(displayName + ": ").styled(s -> s.withColor(Lang.colorNormal())));
                        String display = str.length() > 25 ? str.substring(0, 22) + "..." : str;
                        line.append(Text.literal("\"" + display + "\"").styled(s -> s
                                .withColor(Formatting.WHITE)
                                .withClickEvent(new ClickEvent.SuggestCommand(CMD + " set " + fieldName + " " + str))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        fieldHover(fieldName, "panel.click_edit", displayName + ": " + str)))));
                    }

                    send(ctx, line);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    send(ctx, Text.literal("  " + fieldName + ": ").formatted(Formatting.GRAY)
                            .append(Text.literal("(error)").formatted(Formatting.RED)));
                }
            }
        }

        send(ctx, Text.literal(""));
        send(ctx, Lang.getMutable("panel.back").styled(s -> s
                .withClickEvent(new ClickEvent.RunCommand(CMD))
                .withHoverEvent(new HoverEvent.ShowText(
                        Lang.get("panel.back_hover")))));
        send(ctx, Text.literal(""));
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Toggle boolean field
    // ═══════════════════════════════════════════════════════════════════════

    private static int toggleField(CommandContext<ServerCommandSource> ctx) {
        String fieldName = StringArgumentType.getString(ctx, "field");
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) {
            ctx.getSource().sendError(Lang.get("error.config_not_loaded"));
            return 0;
        }

        try {
            Field field = ViaStyleConfig.class.getField(fieldName);
            if (field.getType() != boolean.class) {
                ctx.getSource().sendError(Lang.get("panel.field_not_boolean"));
                return 0;
            }

            boolean current = field.getBoolean(cfg);
            field.setBoolean(cfg, !current);
            cfg.save();

            boolean newVal = !current;
            ctx.getSource().sendFeedback(
                    () -> Text.literal("  " + toDisplayName(fieldName) + ": ").styled(s -> s.withColor(Lang.colorNormal()))
                            .append(newVal
                                    ? Text.literal("ON").styled(s -> s.withColor(Lang.colorGreen()))
                                    : Text.literal("OFF").styled(s -> s.withColor(Lang.colorRed())))
                            .append(Lang.get("panel.saved")),
                    false);

            applyVisualRefreshIfNeeded(fieldName, ctx);
            return 1;
        } catch (NoSuchFieldException e) {
            ctx.getSource().sendError(Lang.get("panel.unknown_field"));
        } catch (IllegalAccessException e) {
            ctx.getSource().sendError(
                    Text.literal("Cannot access field: " + fieldName).formatted(Formatting.RED));
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Set string / number field
    // ═══════════════════════════════════════════════════════════════════════

    private static int setField(CommandContext<ServerCommandSource> ctx) {
        String fieldName = StringArgumentType.getString(ctx, "field");
        String rawValue = StringArgumentType.getString(ctx, "value");
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) {
            ctx.getSource().sendError(Lang.get("error.config_not_loaded"));
            return 0;
        }

        try {
            Field field = ViaStyleConfig.class.getField(fieldName);
            Class<?> type = field.getType();

            if (type == String.class) {
                field.set(cfg, rawValue);
            } else if (type == double.class) {
                field.setDouble(cfg, Double.parseDouble(rawValue));
            } else if (type == int.class) {
                field.setInt(cfg, Integer.parseInt(rawValue));
            } else if (type == boolean.class) {
                field.setBoolean(cfg, Boolean.parseBoolean(rawValue));
            } else {
                ctx.getSource().sendError(
                        Text.literal("Unsupported field type.").formatted(Formatting.RED));
                return 0;
            }

            cfg.save();

            // If language was changed, apply immediately
            if (fieldName.equals("defaultLanguage")) {
                Lang.setLang(rawValue);
            }

            ctx.getSource().sendFeedback(
                    () -> Text.literal("  " + toDisplayName(fieldName) + " = ").styled(s -> s.withColor(Lang.colorNormal()))
                            .append(Text.literal(rawValue).formatted(Formatting.WHITE))
                            .append(Lang.get("panel.saved")),
                    false);

            applyVisualRefreshIfNeeded(fieldName, ctx);
            return 1;
        } catch (NoSuchFieldException e) {
            ctx.getSource().sendError(Lang.get("panel.unknown_field"));
        } catch (NumberFormatException e) {
            ctx.getSource().sendError(Lang.get("panel.invalid_number"));
        } catch (IllegalAccessException e) {
            ctx.getSource().sendError(
                    Text.literal("Cannot access field: " + fieldName).formatted(Formatting.RED));
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Reload
    // ═══════════════════════════════════════════════════════════════════════

    private static int reloadConfig(CommandContext<ServerCommandSource> ctx) {
        viaStyle.CONFIG = ViaStyleConfig.load();
        Lang.setLang(viaStyle.CONFIG.defaultLanguage);
        TabListConfig.load();
        NickColorManager.reload();

        var server = ctx.getSource().getServer();
        TabListManager.updateAll(server);
        NametagManager.updateAll(server);

        ctx.getSource().sendFeedback(
                () -> Lang.get("panel.reload_done"),
                false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Utilities
    // ═══════════════════════════════════════════════════════════════════════

    /** Refreshes visuals when a nametag/tab/nickcolor related field changes. */
    private static void applyVisualRefreshIfNeeded(String fieldName, CommandContext<ServerCommandSource> ctx) {
        if (fieldName.contains("nickColor") || fieldName.contains("nametag")
                || fieldName.contains("tab") || fieldName.contains("Tab")
                || fieldName.contains("Nametag") || fieldName.contains("NickColor")
                || fieldName.contains("Spectator") || fieldName.contains("spectator")) {
            var server = ctx.getSource().getServer();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                NickColorManager.invalidate(p.getUuid());
            }
            TabListManager.updateAll(server);
            NametagManager.updateAll(server);
        }
    }

    /** Converts camelCase field names to human-readable display names. */
    private static String toDisplayName(String fieldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append(' ');
            }
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    /**
     * Builds a hover tooltip for a config field.
     * Shows the per-field description (from {@code panel.field.<fieldName>}) on the first line,
     * then the action hint (e.g. "Click to toggle") on the second line,
     * and optionally the raw value on a third line.
     *
     * @param fieldName    camelCase config field name
     * @param actionKey    Lang key for the action hint ("panel.click_toggle" / "panel.click_edit")
     * @param rawValue     optional full raw value to show (for strings), or {@code null}
     */
    private static MutableText fieldHover(String fieldName, String actionKey, String rawValue) {
        String descKey = "panel.field." + fieldName;
        MutableText hover = Lang.getMutable(descKey);
        hover.append(Text.literal("\n")).append(Lang.get(actionKey));
        if (rawValue != null && !rawValue.isBlank()) {
            hover.append(Text.literal("\n" + rawValue).formatted(Formatting.GRAY));
        }
        return hover;
    }

    private static void send(CommandContext<ServerCommandSource> ctx, Text text) {
        ctx.getSource().sendFeedback(() -> text, false);
    }
}
