package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

public class PmSoundCommand {

    private static final TextColor COLOR_ON  = TextColor.fromRgb(0x98FB98);
    private static final TextColor COLOR_OFF = TextColor.fromRgb(0xFCDE9D);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("msound")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.msound", 0))
                .then(Commands.literal("on")
                        .executes(PmSoundCommand::enable))
                .then(Commands.literal("off")
                        .executes(PmSoundCommand::disable))
                .then(Commands.literal("status")
                        .executes(PmSoundCommand::status))
                .executes(PmSoundCommand::toggle)
        );
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        boolean nowEnabled = viaStyle.togglePmSound(player.getUUID());
        TextColor color = nowEnabled ? COLOR_ON : COLOR_OFF;
        source.sendSuccess(() -> Lang.getColored(nowEnabled ? "msound.enabled" : "msound.disabled", color), false);
        return 1;
    }

    private static int enable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        viaStyle.enablePmSound(player.getUUID());
        source.sendSuccess(() -> Lang.getColored("msound.enabled", COLOR_ON), false);
        return 1;
    }

    private static int disable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        viaStyle.disablePmSound(player.getUUID());
        source.sendSuccess(() -> Lang.getColored("msound.disabled", COLOR_OFF), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        boolean enabled = viaStyle.isPmSoundEnabled(player.getUUID());
        TextColor color = enabled ? COLOR_ON : COLOR_OFF;
        source.sendSuccess(() -> Lang.getColored("msound.status_" + (enabled ? "on" : "off"), color), false);
        return 1;
    }
}
