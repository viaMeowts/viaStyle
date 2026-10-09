package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.SocialSpyManager;
import com.viameowts.viastyle.SocialSpyManager.Channel;
import java.util.Set;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

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

    private static final TextColor COLOR_ACCENT = TextColor.fromRgb(0xFFC64C);
    private static final TextColor COLOR_TEXT = TextColor.fromRgb(0xD9D0D5);
    private static final TextColor COLOR_ON = TextColor.fromRgb(0x98FB98);
    private static final TextColor COLOR_OFF = TextColor.fromRgb(0xFF5555);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("socialspy")
                .requires(SocialSpyCommand::hasPermission)
                .executes(SocialSpyCommand::showStatus)
                .then(Commands.literal("on")
                        .executes(SocialSpyCommand::enableAll))
                .then(Commands.literal("off")
                        .executes(SocialSpyCommand::disableAll))
                .then(Commands.argument("channel", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            for (Channel ch : Channel.values()) {
                                String channel = ch.name().toLowerCase();
                                if (channel.startsWith(remaining)) {
                                    builder.suggest(channel);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(SocialSpyCommand::toggleChannel))
        );
    }

    private static boolean hasPermission(CommandSourceStack source) {
        if (LuckPermsHelper.checkPermission(source, "viastyle.command.socialspy", 2)) return true;
        if (source.getEntity() instanceof ServerPlayer p) {
            return LuckPermsHelper.checkPlayerPermission(p, "viastyle.socialspy", 2);
        }
        return false;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Lang.get("error.player_only"));
            return 0;
        }

        Set<Channel> channels = SocialSpyManager.getChannels(player.getUUID());
        boolean anyActive = !channels.isEmpty();

        MutableComponent header = Component.literal("▸ ").withStyle(s -> s.withColor(COLOR_ACCENT))
            .append(Lang.get("spy.header"));
        ctx.getSource().sendSuccess(() -> header, false);

        // Master toggle
        MutableComponent masterLine = Lang.getMutable("spy.master");
        if (anyActive) {
            masterLine.append(Lang.getMutable("spy.state_on_tag").withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy off"))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_disable_all")))));
        } else {
            masterLine.append(Lang.getMutable("spy.state_off_tag").withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy on"))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_enable_all")))));
        }
        ctx.getSource().sendSuccess(() -> masterLine, false);

        // Per-channel toggles
        for (Channel ch : Channel.values()) {
            boolean on = channels.contains(ch);
            String chName = ch.name().toLowerCase();
                MutableComponent line = Component.literal("  " + capitalize(chName) + ": ").withStyle(s -> s.withColor(COLOR_TEXT));

                MutableComponent toggle = (on ? Lang.getMutable("spy.state_on_tag") : Lang.getMutable("spy.state_off_tag")).withStyle(s -> s
                    .withColor(on ? COLOR_ON : COLOR_OFF)
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy " + chName))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_toggle"))));
            line.append(toggle);
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int enableAll(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        SocialSpyManager.enableAll(player.getUUID());
        com.viameowts.viastyle.MeridianaAudit.action(player.getName().getString(), "/socialspy on: включил просмотр чужих личных сообщений");
        ctx.getSource().sendSuccess(() -> Lang.get("spy.enabled_all"), false);
        return 1;
    }

    private static int disableAll(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        SocialSpyManager.disableAll(player.getUUID());
        ctx.getSource().sendSuccess(() -> Lang.get("spy.disabled_all"), false);
        return 1;
    }

    private static int toggleChannel(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        String chName = StringArgumentType.getString(ctx, "channel");
        Channel ch = Channel.fromString(chName);
        if (ch == null) {
            ctx.getSource().sendFailure(Lang.get("spy.channel_unknown"));
            return 0;
        }

        boolean nowOn = SocialSpyManager.toggleChannel(player.getUUID(), ch);
        com.viameowts.viastyle.MeridianaAudit.action(player.getName().getString(), "/socialspy " + chName + (nowOn ? " on" : " off"));
        ctx.getSource().sendSuccess(
            () -> Lang.getMutable("spy.toggle_prefix").withStyle(s -> s.withColor(COLOR_ACCENT))
                    .append(Component.literal(capitalize(chName)).withStyle(s -> s.withColor(COLOR_TEXT)))
                    .append(Component.literal(": ").withStyle(s -> s.withColor(COLOR_ACCENT)))
                    .append(nowOn
                ? Lang.get("spy.state_on").copy().withStyle(s -> s.withColor(COLOR_ON))
                : Lang.get("spy.state_off").copy().withStyle(s -> s.withColor(COLOR_OFF))),
                false);
        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
