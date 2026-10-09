package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.viameowts.viastyle.viaStyle;
import java.util.Map;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Russian names for the player chat commands, the same on every server of the
 * network as the core's ({@code /гид команды} lists them). Each is a full copy
 * of the English node – same permission, same arguments – not a redirect, so
 * {@code /афк} works without arguments too. Registered last, after every
 * viaStyle command; a name another mod already took is left alone.
 */
public final class RussianNames {

    private static final Map<String, String> NAMES = Map.of(
            "лс", "msg",
            "ответ", "reply",
            "игнор", "ignore",
            "разыгнор", "unignore",
            "афк", "afk",
            "канал", "ch",
            "онлайн", "online",
            "цветник", "nickcolor",
            "звуклс", "msound");

    private RussianNames() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        NAMES.forEach((ru, en) -> {
            CommandNode<CommandSourceStack> target = root.getChild(en);
            if (target == null || root.getChild(ru) != null) {
                if (target == null) {
                    viaStyle.LOGGER.warn("viaStyle: /{} not registered, no /{}", en, ru);
                }
                return;
            }
            LiteralArgumentBuilder<CommandSourceStack> copy = Commands.literal(ru).requires(target.getRequirement());
            if (target.getCommand() != null) {
                copy.executes(target.getCommand());
            }
            for (CommandNode<CommandSourceStack> child : target.getChildren()) {
                copy.then(child);
            }
            dispatcher.register(copy);
        });
    }
}
