package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;

/**
 * <pre>
 * /nickcolor set &lt;player&gt; &lt;spec&gt;   — set nick colour (OP 2)
 * /nickcolor remove &lt;player&gt;        — remove nick colour (OP 2)
 * /nickcolor reload                   — reload overrides file (OP 2)
 * /nickcolor preview &lt;spec&gt;         — preview a colour on your own name
 * </pre>
 *
 * <p>Colour spec examples: {@code #ff5555}, {@code gradient:#ff0000:#00ff00},
 * {@code gold}</p>
 */
public class NickColorCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {

        dispatcher.register(Commands.literal("nickcolor")
                // /nickcolor set <player> <spec>
                .then(Commands.literal("set")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.nickcolor", 2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                                                        String remaining = b.getRemainingLowerCase();
                                    for (ServerPlayer p : ctx.getSource().getServer()
                                            .getPlayerList().getPlayers()) {
                                                                                String name = p.getName().getString();
                                                                                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                                                                        b.suggest(name);
                                                                                }
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                        .executes(NickColorCommand::setColor))))
                // /nickcolor remove <player>
                .then(Commands.literal("remove")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.nickcolor", 2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                                                        String remaining = b.getRemainingLowerCase();
                                    for (ServerPlayer p : ctx.getSource().getServer()
                                            .getPlayerList().getPlayers()) {
                                                                                String name = p.getName().getString();
                                                                                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                                                                        b.suggest(name);
                                                                                }
                                    }
                                    return b.buildFuture();
                                })
                                .executes(NickColorCommand::removeColor)))
                // /nickcolor reload
                .then(Commands.literal("reload")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.nickcolor", 2))
                        .executes(NickColorCommand::reload))
                // /nickcolor preview <spec>
                .then(Commands.literal("preview")
                        .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.nickcolor.preview"))
                        .then(Commands.argument("spec", StringArgumentType.greedyString())
                                .executes(NickColorCommand::preview)))
        );
    }

    // ── /nickcolor set ────────────────────────────────────────────────────

    private static int setColor(CommandContext<CommandSourceStack> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");
        String spec       = StringArgumentType.getString(ctx, "spec");

        ServerPlayer target = ctx.getSource().getServer()
                .getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(Lang.get("error.player_not_found"));
            return 0;
        }

        // Validate spec
        MutableComponent preview = MiniMessageParser.colorize(target.getName().getString(), spec);
        if (preview == null) {
            ctx.getSource().sendFailure(Lang.get("nickcolor.invalid_spec"));
            return 0;
        }

        NickColorManager.setOverride(target.getUUID(), spec);
        TabListManager.updatePlayer(target);
        NametagManager.updatePlayer(target);

        ctx.getSource().sendSuccess(
                () -> Component.empty()
                        .append(Lang.get("nickcolor.set"))
                        .append(preview)
                        .append(Component.literal(" => ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(spec).withStyle(ChatFormatting.AQUA)),
                true);
        return 1;
    }

    // ── /nickcolor remove ─────────────────────────────────────────────────

    private static int removeColor(CommandContext<CommandSourceStack> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");

        ServerPlayer target = ctx.getSource().getServer()
                .getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(Lang.get("error.player_not_found"));
            return 0;
        }

        NickColorManager.removeOverride(target.getUUID());
        NickColorManager.invalidate(target.getUUID());
        TabListManager.updatePlayer(target);
        NametagManager.updatePlayer(target);

        ctx.getSource().sendSuccess(
                () -> Lang.getMutable("nickcolor.removed")
                        .append(Component.literal(targetName).withStyle(ChatFormatting.WHITE)),
                true);
        return 1;
    }

    // ── /nickcolor reload ─────────────────────────────────────────────────

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        NickColorManager.reload();

        MinecraftServer server = ctx.getSource().getServer();
        TabListManager.updateAll(server);
        NametagManager.updateAll(server);

        ctx.getSource().sendSuccess(
                () -> Lang.get("nickcolor.reloaded"),
                true);
        return 1;
    }

    // ── /nickcolor preview ────────────────────────────────────────────────

    private static int preview(CommandContext<CommandSourceStack> ctx) {
        String spec = StringArgumentType.getString(ctx, "spec");

        String name = "Player";
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
            name = p.getName().getString();
        }

        MutableComponent preview = MiniMessageParser.colorize(name, spec);
        if (preview == null) {
            ctx.getSource().sendFailure(Lang.get("nickcolor.invalid_spec"));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.empty()
                        .append(Component.literal("Preview: ").withStyle(ChatFormatting.GRAY))
                        .append(preview),
                false);
        return 1;
    }
}
