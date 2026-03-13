package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.SocialSpyManager;
import com.viameowts.viastyle.SocialSpyManager.Channel;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;

/**
 * <pre>
 * /socialspy              — show current status with clickable toggles
 * /socialspy on           — enable all channels
 * /socialspy off          — disable all channels
 * /socialspy &lt;channel&gt;   — toggle a specific channel (local/global/staff/pm)
 * </pre>
 *
 * Permission: viaStyle.socialspy (or OP level 2)
 */
public class SocialSpyCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("socialspy")
                .requires(SocialSpyCommand::hasPermission)
                .executes(SocialSpyCommand::showStatus)
                .then(CommandManager.literal("on")
                        .executes(SocialSpyCommand::enableAll))
                .then(CommandManager.literal("off")
                        .executes(SocialSpyCommand::disableAll))
                .then(CommandManager.argument("channel", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Channel ch : Channel.values()) {
                                builder.suggest(ch.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(SocialSpyCommand::toggleChannel))
        );
    }

    private static boolean hasPermission(ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) return true;
        if (source.getEntity() instanceof ServerPlayerEntity p) {
            return LuckPermsHelper.hasPermission(p.getUuid(), "viastyle.socialspy");
        }
        return false;
    }

    private static int showStatus(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }

        Set<Channel> channels = SocialSpyManager.getChannels(player.getUuid());
        boolean anyActive = !channels.isEmpty();

        MutableText header = Text.literal("═══ ").formatted(Formatting.GOLD)
                .append(Lang.get("spy.header"))
                .append(Text.literal(" ═══").formatted(Formatting.GOLD));
        ctx.getSource().sendFeedback(() -> header, false);

        // Master toggle
        MutableText masterLine = Lang.getMutable("spy.master");
        if (anyActive) {
            masterLine.append(Text.literal("[ON]").styled(s -> s
                    .withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy off"))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_disable_all")))));
        } else {
            masterLine.append(Text.literal("[OFF]").styled(s -> s
                    .withColor(Formatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy on"))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_enable_all")))));
        }
        ctx.getSource().sendFeedback(() -> masterLine, false);

        // Per-channel toggles
        for (Channel ch : Channel.values()) {
            boolean on = channels.contains(ch);
            String chName = ch.name().toLowerCase();
            MutableText line = Text.literal("  " + capitalize(chName) + ": ").formatted(Formatting.GRAY);

            MutableText toggle = Text.literal(on ? "[ON]" : "[OFF]").styled(s -> s
                    .withColor(on ? Formatting.GREEN : Formatting.RED)
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy " + chName))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_toggle"))));
            line.append(toggle);
            ctx.getSource().sendFeedback(() -> line, false);
        }

        MutableText footer = Text.literal("═════════════════").formatted(Formatting.GOLD);
        ctx.getSource().sendFeedback(() -> footer, false);
        return 1;
    }

    private static int enableAll(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }
        SocialSpyManager.enableAll(player.getUuid());
        ctx.getSource().sendFeedback(() -> Lang.get("spy.enabled_all"), false);
        return 1;
    }

    private static int disableAll(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }
        SocialSpyManager.disableAll(player.getUuid());
        ctx.getSource().sendFeedback(() -> Lang.get("spy.disabled_all"), false);
        return 1;
    }

    private static int toggleChannel(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }
        String chName = StringArgumentType.getString(ctx, "channel");
        Channel ch = Channel.fromString(chName);
        if (ch == null) {
            ctx.getSource().sendError(Lang.get("spy.channel_unknown"));
            return 0;
        }

        boolean nowOn = SocialSpyManager.toggleChannel(player.getUuid(), ch);
        ctx.getSource().sendFeedback(
                () -> Text.literal("SocialSpy ").formatted(Formatting.GOLD)
                        .append(Text.literal(capitalize(chName)).formatted(Formatting.WHITE))
                        .append(Text.literal(": ").formatted(Formatting.GOLD))
                        .append(nowOn
                                ? Text.literal("ON").formatted(Formatting.GREEN)
                                : Text.literal("OFF").formatted(Formatting.RED)),
                false);
        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
