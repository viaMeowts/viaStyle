package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.PlaceholderHelper;
import com.viameowts.viastyle.TickScheduler;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("viaSuper")
                        .requires(source -> LuckPermsHelper.checkPermission(source, "viastyle.command.viasuper", 2))
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(ViaSuperCommand::execute))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");
        String[] words = message.split("\\s+");
        if (words.length == 0) return 0;

        MinecraftServer server = context.getSource().getServer();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        boolean wordSound = viaStyle.CONFIG != null && viaStyle.CONFIG.viaSuperWordSound;

        // Play ding sound immediately for all players
        for (ServerPlayerEntity player : players) {
            ((ServerWorld) player.getEntityWorld()).playSound(
                    null,
                    player.getBlockPos(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.MASTER,
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

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    // Clear previous title
                    player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
                    player.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 17, 5));

                    if (isLong) {
                        // Long word → show as subtitle (smaller font)
                        String subtitleFmt = viaStyle.CONFIG != null
                                ? viaStyle.CONFIG.viaSuperSubtitleFormat : "<bold><dark_red>{word}";
                        Text subtitleText = PlaceholderHelper.parseFormat(
                                subtitleFmt.replace("{word}", word), null);
                        player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
                        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleText));
                    } else {
                        // Short word → show as title (big font)
                        String titleFmt = viaStyle.CONFIG != null
                                ? viaStyle.CONFIG.viaSuperTitleFormat : "<bold><red>{word}";
                        Text titleText = PlaceholderHelper.parseFormat(
                                titleFmt.replace("{word}", word), null);
                        player.networkHandler.sendPacket(new TitleS2CPacket(titleText));
                    }

                    // Per-word sound effect
                    if (wordSound) {
                        ((ServerWorld) player.getEntityWorld()).playSound(
                                null,
                                player.getBlockPos(),
                                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                                SoundCategory.MASTER,
                                1.0f, 1.0f
                        );
                    }
                }
            });
        }

        viaStyle.LOGGER.info("[viaStyle] /viaSuper sent \"{}\" ({} word(s)) to {} player(s).",
                message, words.length, players.size());
        context.getSource().sendFeedback(
                () -> Text.literal("[viaStyle] Title sent (" + words.length + " words) to "
                                + server.getPlayerManager().getPlayerList().size() + " player(s).")
                        .formatted(Formatting.GREEN),
                true
        );
        return players.size();
    }


}
