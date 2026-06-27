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
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class AfkCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("afk")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.afk"))
            .then(CommandManager.literal("bypass")
                .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.afk.bypass.manage", 2))
                .then(CommandManager.literal("list")
                    .executes(AfkCommand::bypassList))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(AfkCommand::bypassToggle)))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.afk.others", 2))
                .executes(AfkCommand::toggleOther))
            .executes(AfkCommand::toggleSelf));
    }

    private static int toggleSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        boolean nowAfk = AfkManager.toggleAfk(player);
        ViaStyleConfig cfg = viaStyle.CONFIG;
        TextColor color = parseHexColor(nowAfk ? cfg.afkEnabledColor : cfg.afkDisabledColor);
        source.sendFeedback(() -> Lang.getColored(nowAfk ? "afk.self_enabled" : "afk.self_disabled", color), true);
        return 1;
    }

    private static int toggleOther(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        boolean nowAfk = AfkManager.toggleAfk(target);
        ServerCommandSource source = context.getSource();
        ViaStyleConfig cfg = viaStyle.CONFIG;
        TextColor color = parseHexColor(nowAfk ? cfg.afkEnabledColor : cfg.afkDisabledColor);
        TextColor nameColor = parseHexColor(nowAfk ? cfg.afkEnabledColor : cfg.afkDisabledColor);
        Text msg = Lang.getColored(nowAfk ? "afk.other_set" : "afk.other_unset", color)
                .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(nameColor)));
        source.sendFeedback(() -> msg, true);
        return 1;
    }

    private static int bypassList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String exempt = viaStyle.CONFIG != null ? viaStyle.CONFIG.afkExemptPlayers : "";
        if (exempt == null || exempt.isBlank()) {
            source.sendFeedback(() -> Text.literal("No exempt players configured.").formatted(Formatting.GRAY), false);
            return 1;
        }
        Text list = Text.literal("AFK exempt players: ").formatted(Formatting.YELLOW)
                .append(Text.literal(exempt).formatted(Formatting.GRAY));
        source.sendFeedback(() -> list, false);
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

    private static int bypassToggle(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        ServerCommandSource source = context.getSource();
        ViaStyleConfig cfg = viaStyle.CONFIG;

        UUID targetUuid = target.getUuid();
        String uuidStr = targetUuid.toString();
        String exempt = cfg.afkExemptPlayers != null ? cfg.afkExemptPlayers : "";

        TextColor removedColor = parseHexColor(cfg.afkDisabledColor);
        TextColor addedColor = parseHexColor(cfg.afkEnabledColor);
        if (exempt.contains(uuidStr)) {
            cfg.afkExemptPlayers = exempt.replace(uuidStr, "").replace(",,", ",")
                    .replaceAll("^,|,$", "").trim();
            source.sendFeedback(() -> Text.literal("Removed ")
                    .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(removedColor)))
                    .append(Text.literal(" from AFK exempt list.").formatted(Formatting.GREEN)), true);
        } else {
            if (!exempt.isEmpty() && !exempt.endsWith(",")) exempt += ",";
            exempt += uuidStr;
            cfg.afkExemptPlayers = exempt;
            source.sendFeedback(() -> Text.literal("Added ")
                    .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(addedColor)))
                    .append(Text.literal(" to AFK exempt list.").formatted(Formatting.GREEN)), true);
        }
        cfg.save();
        return 1;
    }
}
