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
import net.minecraft.text.TextColor;

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

    private static final TextColor COLOR_ACCENT = TextColor.fromRgb(0xFFC64C);
    private static final TextColor COLOR_TEXT = TextColor.fromRgb(0xD9D0D5);
    private static final TextColor COLOR_ON = TextColor.fromRgb(0x98FB98);
    private static final TextColor COLOR_OFF = TextColor.fromRgb(0xFF5555);

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

    private static boolean hasPermission(ServerCommandSource source) {
        if (LuckPermsHelper.checkPermission(source, "viastyle.command.socialspy", 2)) return true;
        if (source.getEntity() instanceof ServerPlayerEntity p) {
            return LuckPermsHelper.checkPlayerPermission(p, "viastyle.socialspy", 2);
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

        MutableText header = Text.literal("▸ ").styled(s -> s.withColor(COLOR_ACCENT))
            .append(Lang.get("spy.header"));
        ctx.getSource().sendFeedback(() -> header, false);

        // Master toggle
        MutableText masterLine = Lang.getMutable("spy.master");
        if (anyActive) {
            masterLine.append(Lang.getMutable("spy.state_on_tag").styled(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy off"))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_disable_all")))));
        } else {
            masterLine.append(Lang.getMutable("spy.state_off_tag").styled(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy on"))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_enable_all")))));
        }
        ctx.getSource().sendFeedback(() -> masterLine, false);

        // Per-channel toggles
        for (Channel ch : Channel.values()) {
            boolean on = channels.contains(ch);
            String chName = ch.name().toLowerCase();
                MutableText line = Text.literal("  " + capitalize(chName) + ": ").styled(s -> s.withColor(COLOR_TEXT));

                MutableText toggle = (on ? Lang.getMutable("spy.state_on_tag") : Lang.getMutable("spy.state_off_tag")).styled(s -> s
                    .withColor(on ? COLOR_ON : COLOR_OFF)
                    .withClickEvent(new ClickEvent.RunCommand("/socialspy " + chName))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Lang.get("spy.click_toggle"))));
            line.append(toggle);
            ctx.getSource().sendFeedback(() -> line, false);
        }
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
            () -> Lang.getMutable("spy.toggle_prefix").styled(s -> s.withColor(COLOR_ACCENT))
                    .append(Text.literal(capitalize(chName)).styled(s -> s.withColor(COLOR_TEXT)))
                    .append(Text.literal(": ").styled(s -> s.withColor(COLOR_ACCENT)))
                    .append(nowOn
                ? Lang.get("spy.state_on").copy().styled(s -> s.withColor(COLOR_ON))
                : Lang.get("spy.state_off").copy().styled(s -> s.withColor(COLOR_OFF))),
                false);
        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
