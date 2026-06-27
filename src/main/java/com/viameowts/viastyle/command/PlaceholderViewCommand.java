package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.ChatSharePlaceholders;
import com.viameowts.viastyle.Lang;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PlaceholderViewCommand {

    private PlaceholderViewCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("vsview")
            .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
            .then(CommandManager.argument("id", StringArgumentType.word())
                .executes(PlaceholderViewCommand::openView)));

        dispatcher.register(CommandManager.literal("viastyle_view")
            .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
            .then(CommandManager.argument("id", StringArgumentType.word())
                .executes(PlaceholderViewCommand::openView)));
    }

    private static int openView(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String id = StringArgumentType.getString(ctx, "id");
        boolean opened = ChatSharePlaceholders.openSharedView(player, id);
        if (!opened) {
            player.sendMessage(Lang.get("chat.placeholder.view_expired"), false);
            return 0;
        }
        return 1;
    }
}
