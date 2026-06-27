package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TextColor;

public class PmSoundCommand {

    private static final TextColor COLOR_ON  = TextColor.fromRgb(0x98FB98);
    private static final TextColor COLOR_OFF = TextColor.fromRgb(0xFCDE9D);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("msound")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.msound"))
                .then(CommandManager.literal("on")
                        .executes(PmSoundCommand::enable))
                .then(CommandManager.literal("off")
                        .executes(PmSoundCommand::disable))
                .then(CommandManager.literal("status")
                        .executes(PmSoundCommand::status))
                .executes(PmSoundCommand::toggle)
        );
    }

    private static int toggle(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Lang.get("error.player_only"));
            return 0;
        }
        boolean nowEnabled = viaStyle.togglePmSound(player.getUuid());
        TextColor color = nowEnabled ? COLOR_ON : COLOR_OFF;
        source.sendFeedback(() -> Lang.getColored(nowEnabled ? "msound.enabled" : "msound.disabled", color), false);
        return 1;
    }

    private static int enable(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Lang.get("error.player_only"));
            return 0;
        }
        viaStyle.enablePmSound(player.getUuid());
        source.sendFeedback(() -> Lang.getColored("msound.enabled", COLOR_ON), false);
        return 1;
    }

    private static int disable(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Lang.get("error.player_only"));
            return 0;
        }
        viaStyle.disablePmSound(player.getUuid());
        source.sendFeedback(() -> Lang.getColored("msound.disabled", COLOR_OFF), false);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Lang.get("error.player_only"));
            return 0;
        }
        boolean enabled = viaStyle.isPmSoundEnabled(player.getUuid());
        TextColor color = enabled ? COLOR_ON : COLOR_OFF;
        source.sendFeedback(() -> Lang.getColored("msound.status_" + (enabled ? "on" : "off"), color), false);
        return 1;
    }
}
