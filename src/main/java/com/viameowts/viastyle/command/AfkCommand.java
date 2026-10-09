package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.viameowts.viastyle.AfkManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.viaStyle;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

public class AfkCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("afk")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.afk", 0))
            .then(Commands.literal("bypass")
                .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.afk.bypass.manage", 2))
                .then(Commands.literal("list")
                    .executes(AfkCommand::bypassList))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(AfkCommand::bypassToggle)))
            .then(Commands.argument("player", EntityArgument.player())
                .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.afk.others", 2))
                .executes(AfkCommand::toggleOther))
            .executes(AfkCommand::toggleSelf));
    }

    private static int toggleSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        boolean nowAfk = AfkManager.toggleAfk(player);
        ViaStyleConfig cfg = viaStyle.CONFIG;
        TextColor color = parseHexColor(nowAfk ? cfg.afkEnabledColor : cfg.afkDisabledColor);
        source.sendSuccess(() -> Lang.getColored(nowAfk ? "afk.self_enabled" : "afk.self_disabled", color), true);
        return 1;
    }

    private static int toggleOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        boolean nowAfk = AfkManager.toggleAfk(target);
        CommandSourceStack source = context.getSource();
        com.viameowts.viastyle.MeridianaAudit.action(source.getTextName(), "/afk: игроку " + target.getName().getString() + (nowAfk ? " включён AFK" : " выключен AFK"));
        ViaStyleConfig cfg = viaStyle.CONFIG;
        TextColor color = parseHexColor(nowAfk ? cfg.afkEnabledColor : cfg.afkDisabledColor);
        TextColor nameColor = parseHexColor(nowAfk ? cfg.afkEnabledColor : cfg.afkDisabledColor);
        Component msg = Lang.getColored(nowAfk ? "afk.other_set" : "afk.other_unset", color)
                .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(nameColor)));
        source.sendSuccess(() -> msg, true);
        return 1;
    }

    private static int bypassList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String exempt = viaStyle.CONFIG != null ? viaStyle.CONFIG.afkExemptPlayers : "";
        if (exempt == null || exempt.isBlank()) {
            source.sendSuccess(() -> Component.literal("No exempt players configured.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        Component list = Component.literal("AFK exempt players: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(exempt).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> list, false);
        return 1;
    }

    private static TextColor parseHexColor(String hex) {
        if (hex == null || hex.isBlank()) return TextColor.fromRgb(0x98FB98);
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return TextColor.fromRgb((int) Long.parseLong(hex, 16));
        } catch (Exception e) {
            return TextColor.fromRgb(0x98FB98);
        }
    }

    private static int bypassToggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();
        ViaStyleConfig cfg = viaStyle.CONFIG;

        UUID targetUuid = target.getUUID();
        String uuidStr = targetUuid.toString();
        String exempt = cfg.afkExemptPlayers != null ? cfg.afkExemptPlayers : "";

        TextColor removedColor = parseHexColor(cfg.afkDisabledColor);
        TextColor addedColor = parseHexColor(cfg.afkEnabledColor);
        if (exempt.contains(uuidStr)) {
            cfg.afkExemptPlayers = exempt.replace(uuidStr, "").replace(",,", ",")
                    .replaceAll("^,|,$", "").trim();
            source.sendSuccess(() -> Component.literal("Removed ")
                    .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(removedColor)))
                    .append(Component.literal(" from AFK exempt list.").withStyle(ChatFormatting.GREEN)), true);
        } else {
            if (!exempt.isEmpty() && !exempt.endsWith(",")) exempt += ",";
            exempt += uuidStr;
            cfg.afkExemptPlayers = exempt;
            source.sendSuccess(() -> Component.literal("Added ")
                    .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(addedColor)))
                    .append(Component.literal(" to AFK exempt list.").withStyle(ChatFormatting.GREEN)), true);
        }
        cfg.save();
        com.viameowts.viastyle.MeridianaAudit.event(source.getTextName(), "action", "WARN", "/afk bypass: список исключений AFK изменён для " + target.getName().getString());
        return 1;
    }
}
