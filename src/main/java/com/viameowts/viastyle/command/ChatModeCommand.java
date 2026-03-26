package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ChatModeCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("viaStyle")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.chatmode"))
                .then(CommandManager.literal("local")
                        .then(CommandManager.literal("!")
                                .executes(ChatModeCommand::setModeLocal)
                        )
                )
                .then(CommandManager.literal("global")
                        .then(CommandManager.literal("!")
                                .executes(ChatModeCommand::setModeGlobal)
                        )
                )
                .then(CommandManager.literal("lang")
                    .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.lang"))
                        .then(CommandManager.argument("language", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("en");
                                    builder.suggest("ru");
                                    return builder.buildFuture();
                                })
                                .executes(ChatModeCommand::setLanguage)
                        )
                        .executes(ChatModeCommand::showCurrentLanguage)
                )
                .then(CommandManager.literal("reload")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.reload", 2))
                        .executes(ChatModeCommand::reloadConfig)
                )
                .executes(ChatModeCommand::showCurrentMode)
        );
    }

    private static int setModeLocal(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Lang.get("error.player_only"));
            return 0;
        }

        viaStyle.playerChatModePref.put(player.getUuid(), false);

        source.sendFeedback(() -> Lang.get("command.set.prefix_local"), false);

        return 1;
    }

    private static int setModeGlobal(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Lang.get("error.player_only"));
            return 0;
        }

        viaStyle.playerChatModePref.put(player.getUuid(), true);

        source.sendFeedback(() -> Lang.get("command.set.prefix_global"), false);

        return 1;
    }

    private static int showCurrentMode(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Lang.get("error.player_only"));
            return 0;
        }
        boolean currentPref = viaStyle.getPlayerPrefersPrefixForGlobal(player.getUuid());
        Text feedback;
        if (currentPref) {
            feedback = Lang.get("command.current.prefix_global");
        } else {
            feedback = Lang.get("command.current.prefix_local");
        }
        source.sendFeedback(() -> feedback, false);
        return 1;
    }

    private static int setLanguage(CommandContext<ServerCommandSource> context) {
        String langArg = StringArgumentType.getString(context, "language");
        ServerCommandSource source = context.getSource();

        if (Lang.setLang(langArg)) {
            // Persist language choice to config
            viaStyle.CONFIG.defaultLanguage = langArg.toLowerCase();
            viaStyle.CONFIG.save();

            Text feedback = Lang.getMutable("command.lang.set")
                    .append(Text.literal(langArg).formatted(Formatting.AQUA));
            source.sendFeedback(() -> feedback, true);
            return 1;
        } else {
            source.sendError(Lang.get("command.lang.invalid"));
            return 0;
        }
    }

    private static int showCurrentLanguage(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Text feedback = Lang.getMutable("command.lang.current")
                .append(Text.literal(Lang.getCurrentLang()).formatted(Formatting.AQUA));
        source.sendFeedback(() -> feedback, false);
        return 1;
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

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
        }

        source.sendFeedback(
                () -> Lang.get("reload.done"),
                true);
        return 1;
    }
}
