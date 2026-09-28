package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ChatModeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("viaStyle")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.chatmode"))
                .then(Commands.literal("local")
                        .then(Commands.literal("!")
                                .executes(ChatModeCommand::setModeLocal)
                        )
                )
                .then(Commands.literal("global")
                        .then(Commands.literal("!")
                                .executes(ChatModeCommand::setModeGlobal)
                        )
                )
                .then(Commands.literal("lang")
                    .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.lang"))
                        .then(Commands.argument("language", StringArgumentType.word())
                                .suggests((context, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            if ("en".startsWith(remaining)) builder.suggest("en");
                            if ("ru".startsWith(remaining)) builder.suggest("ru");
                                    return builder.buildFuture();
                                })
                                .executes(ChatModeCommand::setLanguage)
                        )
                        .executes(ChatModeCommand::showCurrentLanguage)
                )
                .then(Commands.literal("reload")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.reload", 2))
                        .executes(ChatModeCommand::reloadConfig)
                )
                .executes(ChatModeCommand::showCurrentMode)
        );
    }

    private static int setModeLocal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }

        viaStyle.playerChatModePref.put(player.getUUID(), false);

        source.sendSuccess(() -> Lang.get("command.set.prefix_local"), false);

        return 1;
    }

    private static int setModeGlobal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }

        viaStyle.playerChatModePref.put(player.getUUID(), true);

        source.sendSuccess(() -> Lang.get("command.set.prefix_global"), false);

        return 1;
    }

    private static int showCurrentMode(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        boolean currentPref = viaStyle.getPlayerPrefersPrefixForGlobal(player.getUUID());
        Component feedback;
        if (currentPref) {
            feedback = Lang.get("command.current.prefix_global");
        } else {
            feedback = Lang.get("command.current.prefix_local");
        }
        source.sendSuccess(() -> feedback, false);
        return 1;
    }

    private static int setLanguage(CommandContext<CommandSourceStack> context) {
        String langArg = StringArgumentType.getString(context, "language");
        CommandSourceStack source = context.getSource();

        if (Lang.setLang(langArg)) {
            // Persist language choice to config
            viaStyle.CONFIG.defaultLanguage = langArg.toLowerCase();
            viaStyle.CONFIG.applyLocalizedPlaceholderDefaults(viaStyle.CONFIG.defaultLanguage);
            viaStyle.CONFIG.save();

            Component feedback = Lang.getMutable("command.lang.set")
                    .append(Component.literal(langArg).withStyle(ChatFormatting.AQUA));
            source.sendSuccess(() -> feedback, true);
            return 1;
        } else {
            source.sendFailure(Lang.get("command.lang.invalid"));
            return 0;
        }
    }

    private static int showCurrentLanguage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Component feedback = Lang.getMutable("command.lang.current")
                .append(Component.literal(Lang.getCurrentLang()).withStyle(ChatFormatting.AQUA));
        source.sendSuccess(() -> feedback, false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Reload main config
        viaStyle.CONFIG = ViaStyleConfig.load();

        // Reload tab list config
        com.viameowts.viastyle.TabListManager.reloadConfig();

        // Reload nick colours
        com.viameowts.viastyle.NickColorManager.reload();

        // Re-apply to all online players
        net.minecraft.server.MinecraftServer server = source.getServer();
        com.viameowts.viastyle.TabListManager.updateAll(server);
        com.viameowts.viastyle.NametagManager.updateAll(server);

        // Re-set language
        if (viaStyle.CONFIG.defaultLanguage != null && !viaStyle.CONFIG.defaultLanguage.isBlank()) {
            Lang.setLang(viaStyle.CONFIG.defaultLanguage);
            if (viaStyle.CONFIG.applyLocalizedPlaceholderDefaults(viaStyle.CONFIG.defaultLanguage)) {
                viaStyle.CONFIG.save();
            }
        }

        source.sendSuccess(
                () -> Lang.get("reload.done"),
                true);
        return 1;
    }
}
