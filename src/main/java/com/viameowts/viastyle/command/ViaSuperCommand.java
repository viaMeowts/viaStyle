package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.PlaceholderHelper;
import com.viameowts.viastyle.TickScheduler;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.List;

/**
 * /viaSuper [message]
 *
 * Broadcasts a red title message to every online player word-by-word.
 * Words of 7+ characters are displayed as subtitle (smaller font).
 * Requires permission {@code viaStyle.command.viasuper} or OP level 2.
 */
public class ViaSuperCommand {

    /** Ticks each word is shown (fade-in + stay + fade-out). */
    private static final int WORD_TICKS = 25;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("viaSuper")
                        .requires(source -> LuckPermsHelper.checkPermission(source, "viastyle.command.viasuper", 2))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ViaSuperCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        String message = StringArgumentType.getString(context, "message");
        String[] words = message.split("\\s+");
        if (words.length == 0) return 0;

        MinecraftServer server = context.getSource().getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean wordSound = viaStyle.CONFIG != null && viaStyle.CONFIG.viaSuperWordSound;

        // Play ding sound immediately for all players
        for (ServerPlayer player : players) {
            ((ServerLevel) player.level()).playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.MASTER,
                    1.0f, 1.0f
            );
        }

        // Schedule each word to display sequentially
        for (int i = 0; i < words.length; i++) {
            final String word = words[i];
            final int delay = i * WORD_TICKS;

            TickScheduler.schedule(delay, () -> {
                int subtitleLen = viaStyle.CONFIG != null ? viaStyle.CONFIG.viaSuperSubtitleLength : 7;
                boolean isLong = word.length() >= subtitleLen;

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    // Clear previous title
                    player.connection.send(new ClientboundClearTitlesPacket(false));
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(3, 17, 5));

                    if (isLong) {
                        // Long word → show as subtitle (smaller font)
                        String subtitleFmt = viaStyle.CONFIG != null
                                ? viaStyle.CONFIG.viaSuperSubtitleFormat : "<bold><dark_red>{word}";
                        Component subtitleText = PlaceholderHelper.parseFormat(
                                subtitleFmt.replace("{word}", word), null);
                        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleText));
                    } else {
                        // Short word → show as title (big font)
                        String titleFmt = viaStyle.CONFIG != null
                                ? viaStyle.CONFIG.viaSuperTitleFormat : "<bold><red>{word}";
                        Component titleText = PlaceholderHelper.parseFormat(
                                titleFmt.replace("{word}", word), null);
                        player.connection.send(new ClientboundSetTitleTextPacket(titleText));
                    }

                    // Per-word sound effect
                    if (wordSound) {
                        ((ServerLevel) player.level()).playSound(
                                null,
                                player.blockPosition(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP,
                                SoundSource.MASTER,
                                1.0f, 1.0f
                        );
                    }
                }
            });
        }

        viaStyle.LOGGER.info("[viaStyle] /viaSuper sent \"{}\" ({} word(s)) to {} player(s).",
                message, words.length, players.size());

        MutableComponent feedback = Lang.getMutable("viasuper.sent_prefix")
                .append(Component.literal(String.valueOf(words.length)).withStyle(s -> s.withColor(Lang.colorGreen())))
                .append(Lang.get("viasuper.sent_words_suffix"))
                .append(Component.literal(String.valueOf(server.getPlayerList().getPlayers().size())).withStyle(s -> s.withColor(Lang.colorGreen())))
                .append(Lang.get("viasuper.sent_players_suffix"));

        context.getSource().sendSuccess(
                () -> feedback,
                true
        );
        return players.size();
    }


}
