package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.IgnoreManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;
import java.util.UUID;

/**
 * <pre>
 * /ignore &lt;player&gt;    — toggle ignore on a player (blocks PMs)
 * /ignore list          — show your ignore list
 * /unignore &lt;player&gt;  — alias for toggling off
 * </pre>
 */
public class IgnoreCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("ignore")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.ignore"))
                .then(CommandManager.literal("list")
                        .executes(IgnoreCommand::listIgnored))
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (ServerPlayerEntity p : ctx.getSource().getServer()
                                    .getPlayerManager().getPlayerList()) {
                                builder.suggest(p.getName().getString());
                            }
                            return builder.buildFuture();
                        })
                        .executes(IgnoreCommand::toggleIgnore))
        );

        dispatcher.register(CommandManager.literal("unignore")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.ignore"))
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity p) {
                                Set<UUID> ignored = IgnoreManager.getIgnored(p.getUuid());
                                for (ServerPlayerEntity online : ctx.getSource().getServer()
                                        .getPlayerManager().getPlayerList()) {
                                    if (ignored.contains(online.getUuid())) {
                                        builder.suggest(online.getName().getString());
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(IgnoreCommand::unignore))
        );
    }

    private static int toggleIgnore(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity sender)) {
            ctx.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }
        String targetName = StringArgumentType.getString(ctx, "player");
        ServerPlayerEntity target = ctx.getSource().getServer()
                .getPlayerManager().getPlayer(targetName);
        if (target == null) {
            ctx.getSource().sendError(Lang.get("error.player_not_found"));
            return 0;
        }
        if (target == sender) {
            ctx.getSource().sendError(Lang.get("ignore.self"));
            return 0;
        }

        UUID senderUuid = sender.getUuid();
        UUID targetUuid = target.getUuid();

        if (IgnoreManager.isIgnoring(senderUuid, targetUuid)) {
            IgnoreManager.remove(senderUuid, targetUuid);
            ctx.getSource().sendFeedback(
                    () -> Lang.getMutable("ignore.removed")
                            .append(Text.literal(targetName).formatted(Formatting.WHITE))
                            .append(Text.literal(".")),
                    false);
        } else {
            IgnoreManager.add(senderUuid, targetUuid);
            ctx.getSource().sendFeedback(
                    () -> Lang.getMutable("ignore.added")
                            .append(Text.literal(targetName).formatted(Formatting.WHITE))
                            .append(Lang.get("ignore.added_suffix")),
                    false);
        }
        return 1;
    }

    private static int unignore(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity sender)) {
            ctx.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }
        String targetName = StringArgumentType.getString(ctx, "player");
        ServerPlayerEntity target = ctx.getSource().getServer()
                .getPlayerManager().getPlayer(targetName);
        if (target == null) {
            ctx.getSource().sendError(Lang.get("error.player_not_found"));
            return 0;
        }

        if (IgnoreManager.remove(sender.getUuid(), target.getUuid())) {
            ctx.getSource().sendFeedback(
                    () -> Lang.getMutable("ignore.removed")
                            .append(Text.literal(targetName).formatted(Formatting.WHITE))
                            .append(Text.literal(".")),
                    false);
        } else {
            ctx.getSource().sendFeedback(
                    () -> Lang.getMutable("ignore.not_ignoring")
                            .append(Text.literal(targetName).formatted(Formatting.WHITE))
                            .append(Text.literal(".")),
                    false);
        }
        return 1;
    }

    private static int listIgnored(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity sender)) {
            ctx.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }

        Set<UUID> ignored = IgnoreManager.getIgnored(sender.getUuid());
        if (ignored.isEmpty()) {
            ctx.getSource().sendFeedback(
                    () -> Lang.get("ignore.list_empty"),
                    false);
            return 1;
        }

        ctx.getSource().sendFeedback(
                () -> Lang.getMutable("ignore.list_header")
                        .append(Text.literal(" (" + ignored.size() + "):").formatted(Formatting.YELLOW)),
                false);

        for (UUID uuid : ignored) {
            ServerPlayerEntity p = ctx.getSource().getServer().getPlayerManager().getPlayer(uuid);
            String name = p != null ? p.getName().getString() : uuid.toString();
            boolean online = p != null;
            ctx.getSource().sendFeedback(
                    () -> Text.literal("  - ").formatted(Formatting.GRAY)
                            .append(Text.literal(name).formatted(online ? Formatting.WHITE : Formatting.DARK_GRAY))
                            .append(online ? Text.empty() : Lang.get("ignore.offline")),
                    false);
        }
        return 1;
    }
}
