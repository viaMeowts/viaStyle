package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.IgnoreManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * <pre>
 * /ignore &lt;player&gt;    — toggle ignore on a player (blocks PMs)
 * /ignore list          — show your ignore list
 * /unignore &lt;player&gt;  — alias for toggling off
 * </pre>
 */
public class IgnoreCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("ignore")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.ignore"))
                .then(Commands.literal("list")
                        .executes(IgnoreCommand::listIgnored))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            for (ServerPlayer p : ctx.getSource().getServer()
                                    .getPlayerList().getPlayers()) {
                                String name = p.getName().getString();
                                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(IgnoreCommand::toggleIgnore))
        );

        dispatcher.register(Commands.literal("unignore")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.ignore"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
                                Set<UUID> ignored = IgnoreManager.getIgnored(p.getUUID());
                                for (ServerPlayer online : ctx.getSource().getServer()
                                        .getPlayerList().getPlayers()) {
                                    if (ignored.contains(online.getUUID())) {
                                        String name = online.getName().getString();
                                        if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                            builder.suggest(name);
                                        }
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(IgnoreCommand::unignore))
        );
    }

    private static int toggleIgnore(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sender)) {
            ctx.getSource().sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        String targetName = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = ctx.getSource().getServer()
                .getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(Lang.get("error.player_not_found"));
            return 0;
        }
        if (target == sender) {
            ctx.getSource().sendFailure(Lang.get("ignore.self"));
            return 0;
        }

        UUID senderUuid = sender.getUUID();
        UUID targetUuid = target.getUUID();

        if (IgnoreManager.isIgnoring(senderUuid, targetUuid)) {
            IgnoreManager.remove(senderUuid, targetUuid);
            ctx.getSource().sendSuccess(
                    () -> Lang.getMutable("ignore.removed")
                            .append(Component.literal(targetName).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(".")),
                    false);
        } else {
            IgnoreManager.add(senderUuid, targetUuid);
            ctx.getSource().sendSuccess(
                    () -> Lang.getMutable("ignore.added")
                            .append(Component.literal(targetName).withStyle(ChatFormatting.WHITE))
                            .append(Lang.get("ignore.added_suffix")),
                    false);
        }
        return 1;
    }

    private static int unignore(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sender)) {
            ctx.getSource().sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        String targetName = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = ctx.getSource().getServer()
                .getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(Lang.get("error.player_not_found"));
            return 0;
        }

        if (IgnoreManager.remove(sender.getUUID(), target.getUUID())) {
            ctx.getSource().sendSuccess(
                    () -> Lang.getMutable("ignore.removed")
                            .append(Component.literal(targetName).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(".")),
                    false);
        } else {
            ctx.getSource().sendSuccess(
                    () -> Lang.getMutable("ignore.not_ignoring")
                            .append(Component.literal(targetName).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(".")),
                    false);
        }
        return 1;
    }

    private static int listIgnored(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sender)) {
            ctx.getSource().sendFailure(Lang.get("error.player_only"));
            return 0;
        }

        Set<UUID> ignored = IgnoreManager.getIgnored(sender.getUUID());
        if (ignored.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Lang.get("ignore.list_empty"),
                    false);
            return 1;
        }

        ctx.getSource().sendSuccess(
                () -> Lang.getMutable("ignore.list_header")
                        .append(Component.literal(" (" + ignored.size() + "):").withStyle(ChatFormatting.YELLOW)),
                false);

        for (UUID uuid : ignored) {
            ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
            String name = p != null ? p.getName().getString() : uuid.toString();
            boolean online = p != null;
            ctx.getSource().sendSuccess(
                    () -> Component.literal("  - ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(name).withStyle(online ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
                            .append(online ? Component.empty() : Lang.get("ignore.offline")),
                    false);
        }
        return 1;
    }
}
