package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.ChatSharePlaceholders;
import com.viameowts.viastyle.Lang;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class PlaceholderViewCommand {

    private PlaceholderViewCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("vsview")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(PlaceholderViewCommand::openView)));

        dispatcher.register(Commands.literal("viastyle_view")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(PlaceholderViewCommand::openView)));
    }

    private static int openView(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String id = StringArgumentType.getString(ctx, "id");
        boolean opened = ChatSharePlaceholders.openSharedView(player, id);
        if (!opened) {
            player.sendSystemMessage(Lang.get("chat.placeholder.view_expired"));
            return 0;
        }
        return 1;
    }
}
