package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.*;
import com.viameowts.viastyle.LuckPermsHelper;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {

        dispatcher.register(CommandManager.literal("nickcolor")
                // /nickcolor set <player> <spec>
                .then(CommandManager.literal("set")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.nickcolor", 2))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (ServerPlayerEntity p : ctx.getSource().getServer()
                                            .getPlayerManager().getPlayerList()) {
                                        b.suggest(p.getName().getString());
                                    }
                                    return b.buildFuture();
                                })
                                .then(CommandManager.argument("spec", StringArgumentType.greedyString())
                                        .executes(NickColorCommand::setColor))))
                // /nickcolor remove <player>
                .then(CommandManager.literal("remove")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.nickcolor", 2))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (ServerPlayerEntity p : ctx.getSource().getServer()
                                            .getPlayerManager().getPlayerList()) {
                                        b.suggest(p.getName().getString());
                                    }
                                    return b.buildFuture();
                                })
                                .executes(NickColorCommand::removeColor)))
                // /nickcolor reload
                .then(CommandManager.literal("reload")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.nickcolor", 2))
                        .executes(NickColorCommand::reload))
                // /nickcolor preview <spec>
                .then(CommandManager.literal("preview")
                        .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.nickcolor.preview"))
                        .then(CommandManager.argument("spec", StringArgumentType.greedyString())
                                .executes(NickColorCommand::preview)))
        );
    }

    // ── /nickcolor set ────────────────────────────────────────────────────

    private static int setColor(CommandContext<ServerCommandSource> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");
        String spec       = StringArgumentType.getString(ctx, "spec");

        ServerPlayerEntity target = ctx.getSource().getServer()
                .getPlayerManager().getPlayer(targetName);
        if (target == null) {
            ctx.getSource().sendError(Lang.get("error.player_not_found"));
            return 0;
        }

        // Validate spec
        MutableText preview = MiniMessageParser.colorize(target.getName().getString(), spec);
        if (preview == null) {
            ctx.getSource().sendError(Lang.get("nickcolor.invalid_spec"));
            return 0;
        }

        NickColorManager.setOverride(target.getUuid(), spec);
        TabListManager.updatePlayer(target);
        NametagManager.updatePlayer(target);

        ctx.getSource().sendFeedback(
                () -> Text.empty()
                        .append(Lang.get("nickcolor.set"))
                        .append(preview)
                        .append(Text.literal(" => ").formatted(Formatting.GRAY))
                        .append(Text.literal(spec).formatted(Formatting.AQUA)),
                true);
        return 1;
    }

    // ── /nickcolor remove ─────────────────────────────────────────────────

    private static int removeColor(CommandContext<ServerCommandSource> ctx) {
        String targetName = StringArgumentType.getString(ctx, "player");

        ServerPlayerEntity target = ctx.getSource().getServer()
                .getPlayerManager().getPlayer(targetName);
        if (target == null) {
            ctx.getSource().sendError(Lang.get("error.player_not_found"));
            return 0;
        }

        NickColorManager.removeOverride(target.getUuid());
        NickColorManager.invalidate(target.getUuid());
        TabListManager.updatePlayer(target);
        NametagManager.updatePlayer(target);

        ctx.getSource().sendFeedback(
                () -> Lang.getMutable("nickcolor.removed")
                        .append(Text.literal(targetName).formatted(Formatting.WHITE)),
                true);
        return 1;
    }

    // ── /nickcolor reload ─────────────────────────────────────────────────

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        NickColorManager.reload();

        MinecraftServer server = ctx.getSource().getServer();
        TabListManager.updateAll(server);
        NametagManager.updateAll(server);

        ctx.getSource().sendFeedback(
                () -> Lang.get("nickcolor.reloaded"),
                true);
        return 1;
    }

    // ── /nickcolor preview ────────────────────────────────────────────────

    private static int preview(CommandContext<ServerCommandSource> ctx) {
        String spec = StringArgumentType.getString(ctx, "spec");

        String name = "Player";
        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity p) {
            name = p.getName().getString();
        }

        MutableText preview = MiniMessageParser.colorize(name, spec);
        if (preview == null) {
            ctx.getSource().sendError(Lang.get("nickcolor.invalid_spec"));
            return 0;
        }

        ctx.getSource().sendFeedback(
                () -> Text.empty()
                        .append(Text.literal("Preview: ").formatted(Formatting.GRAY))
                        .append(preview),
                false);
        return 1;
    }
}
