package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.IgnoreManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.SocialSpyManager;
import com.viameowts.viastyle.ChatHandler;
import com.viameowts.viastyle.ChatSharePlaceholders;
import com.viameowts.viastyle.BanHammerHelper;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.MentionHandler;
import com.viameowts.viastyle.VanishHelper;
import com.viameowts.viastyle.viaStyle;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * /msg <player> <message>  — send a private message
 * /reply <message>          — reply to the last sender
 *
 * Format strings are read from ViaStyleConfig and support tokens:
 *   {sender}, {receiver}, {message}
 */
public class PrivateMsgCommand {

    /**
     * Maps each player's UUID to the UUID of the last person who messaged them.
     * Used by /reply.
     */
    private static final Map<UUID, UUID> lastMsgFrom = new ConcurrentHashMap<>();

    /** Sentinel UUID used to identify console in the reply tracking map. */
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /** Tracks the last player the console sent a PM to, for /reply from console. */
    private static UUID consoleReplyTarget = null;

    /**
     * Cleans up per-player state when a player disconnects.
     * Prevents unbounded map growth on long-running servers.
     */
    public static void clearPlayer(UUID uuid) {
        lastMsgFrom.remove(uuid);
        lastMsgFrom.values().removeIf(v -> v.equals(uuid));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        // Remove vanilla msg/tell/w/reply nodes so our version takes over
        dispatcher.getRoot().getChildren().removeIf(node -> {
            String n = node.getName();
            return n.equals("msg") || n.equals("tell") || n.equals("w")
                    || n.equals("reply") || n.equals("m") || n.equals("r");
        });

        // /msg <player> <message>  (also registered as /m and /w)
        var msgNode = Commands.literal("msg")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.msg"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            ServerPlayer sender =
                                    ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
                            for (ServerPlayer p : ctx.getSource().getServer()
                                    .getPlayerList().getPlayers()) {
                                // Hide vanished players from PM suggestions
                                if (sender != null && !VanishHelper.canSeePlayer(p, sender)) continue;
                                String name = p.getName().getString();
                                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(PrivateMsgCommand::sendMsg)))
                .build();
        dispatcher.getRoot().addChild(msgNode);
        dispatcher.getRoot().addChild(buildAlias("m",    msgNode));
        dispatcher.getRoot().addChild(buildAlias("w",    msgNode));
        dispatcher.getRoot().addChild(buildAlias("tell", msgNode));

        // /reply <message>  (also /r)
        var replyNode = Commands.literal("reply")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.reply"))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(PrivateMsgCommand::reply))
                .build();
        dispatcher.getRoot().addChild(replyNode);
        dispatcher.getRoot().addChild(buildAlias("r", replyNode));
    }

    /** Creates a redirect alias pointing to {@code target}. */
    private static CommandNode<CommandSourceStack> buildAlias(
            String name,
            CommandNode<CommandSourceStack> target) {
        return Commands.literal(name).redirect(target).build();
    }

    // ── /msg ──────────────────────────────────────────────────────────────────

    private static int sendMsg(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "player");
        String message    = StringArgumentType.getString(context, "message");

        ServerPlayer target = context.getSource().getServer()
                .getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            context.getSource().sendFailure(Lang.get("error.player_not_found"));
            return 0;
        }

        if (context.getSource().getEntity() instanceof ServerPlayer sender) {
            ViaStyleConfig cfg = viaStyle.CONFIG;
            if (target == sender && (cfg == null || !cfg.pmAllowSelfMessage)) {
                context.getSource().sendFailure(Lang.get("pm.error.self"));
                return 0;
            }
            return deliver(sender, target, message) ? 1 : 0;
        }

        return deliverFromConsole(context.getSource(), target, message) ? 1 : 0;
    }

    // ── /reply ─────────────────────────────────────────────────────────────────

    private static int reply(CommandContext<CommandSourceStack> context) {
        String message = StringArgumentType.getString(context, "message");

        if (context.getSource().getEntity() instanceof ServerPlayer sender) {
            UUID targetUuid = lastMsgFrom.get(sender.getUUID());
            if (targetUuid == null) {
                context.getSource().sendFailure(Lang.get("pm.error.no_reply"));
                return 0;
            }

            ServerPlayer target = context.getSource().getServer()
                    .getPlayerList().getPlayer(targetUuid);
            if (target == null) {
                context.getSource().sendFailure(Lang.get("pm.error.offline"));
                return 0;
            }

            return deliver(sender, target, message) ? 1 : 0;
        }

        if (consoleReplyTarget == null) {
            context.getSource().sendFailure(Lang.get("pm.error.no_reply"));
            return 0;
        }

        ServerPlayer target = context.getSource().getServer()
                .getPlayerList().getPlayer(consoleReplyTarget);
        if (target == null) {
            context.getSource().sendFailure(Lang.get("pm.error.offline"));
            return 0;
        }

        return deliverFromConsole(context.getSource(), target, message) ? 1 : 0;
    }

    // ── Core delivery ──────────────────────────────────────────────────────────

    private static boolean deliver(ServerPlayer sender, ServerPlayer receiver, String message) {
        // ── BanHammer mute check ───────────────────────────────────────────
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg != null && cfg.pmBanHammerMute && BanHammerHelper.isMuted(sender)) {
            sender.sendSystemMessage(Lang.get("chat.muted"));
            return false;
        }

        // ── Ignore check ───────────────────────────────────────────────────
        if (IgnoreManager.isIgnoring(receiver.getUUID(), sender.getUUID())) {
            sender.sendSystemMessage(Lang.get("pm.error.ignored"));
            return false;
        }

        // ── Vanish check — block PM to vanished players ────────────────────
        if (VanishHelper.isVanished(receiver)
                && !LuckPermsHelper.checkPlayerPermission(sender, "viastyle.pm.vanished", 2)) {
            sender.sendSystemMessage(Lang.get("error.player_not_found"));
            return false;
        }

        String senderFmt   = cfg != null ? cfg.pmSenderFormat   : "[PM -> {receiver}] {message}";
        String receiverFmt = cfg != null ? cfg.pmReceiverFormat : "[PM <- {sender}] {message}";
        String colorStr    = cfg != null ? cfg.pmColor          : "LIGHT_PURPLE";

        TextColor color = cfg != null
                ? cfg.resolveColor(colorStr, TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE))
                : TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE);

        String senderName   = sender.getName().getString();
        String receiverName = receiver.getName().getString();

        net.minecraft.server.MinecraftServer server = sender.level().getServer();
        ChatSharePlaceholders.ProcessedMessage processed = ChatSharePlaceholders.processMessage(
            message,
            sender,
            server,
            color);

        Component senderNameText = buildClickableName(senderName);
        Component receiverNameText = buildClickableName(receiverName);

        Component senderMsg = formatPmMessage(senderFmt, color,
            senderNameText, receiverNameText, processed.component());
        Component receiverMsg = formatPmMessage(receiverFmt, color,
            senderNameText, receiverNameText, processed.component());

        sender.sendSystemMessage(senderMsg);
        receiver.sendSystemMessage(receiverMsg);

        // Track for /reply in both directions
        lastMsgFrom.put(receiver.getUUID(), sender.getUUID());
        lastMsgFrom.put(sender.getUUID(), receiver.getUUID());

        // SocialSpy relay for private messages
        if (server != null) {
            ChatHandler.relaySocialSpy(server, sender,
                    "[→ " + receiverName + "] " + message,
                    SocialSpyManager.Channel.PM, "PM");
        }

        if (viaStyle.CONFIG == null || viaStyle.CONFIG.logPrivatesToConsole) {
            viaStyle.LOGGER.info("[PM] {} -> {}: {}", senderName, receiverName, message);
        }

        // ── Per-player PM sound ────────────────────────────────────────────
        if (cfg != null && cfg.pmSoundEnabled && viaStyle.isPmSoundEnabled(receiver.getUUID())) {
            Identifier soundId = Identifier.tryParse(cfg.pmSoundId);
            if (soundId != null) {
                BuiltInRegistries.SOUND_EVENT.get(soundId)
                        .ifPresent(entry -> receiver.connection.send(new ClientboundSoundPacket(
                                entry, SoundSource.PLAYERS,
                                receiver.getX(), receiver.getY(), receiver.getZ(),
                                (float) cfg.pmSoundVolume, (float) cfg.pmSoundPitch,
                                receiver.getRandom().nextLong())));
            }
        }

        return true;
    }

    // ── Console delivery ────────────────────────────────────────────────────────

    private static boolean deliverFromConsole(CommandSourceStack source,
                                               ServerPlayer receiver,
                                               String message) {
        ViaStyleConfig cfg = viaStyle.CONFIG;

        String senderFmt   = cfg != null ? cfg.pmSenderFormat   : "[PM -> {receiver}] {message}";
        String receiverFmt = cfg != null ? cfg.pmReceiverFormat : "[PM <- {sender}] {message}";
        String colorStr    = cfg != null ? cfg.pmColor          : "LIGHT_PURPLE";

        TextColor color = cfg != null
                ? cfg.resolveColor(colorStr, TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE))
                : TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE);

        String senderName   = Lang.get("pm.console_name").getString();
        String receiverName = receiver.getName().getString();

        net.minecraft.server.MinecraftServer server = source.getServer();

        // Console messages skip [item]/[pos]/[inv]/[ec] expansion, only highlight @mentions
        Component processedMessage = MentionHandler.highlightMentions(
                message, color, server, null, false);

        Component senderNameText = buildClickableName(senderName);
        Component receiverNameText = buildClickableName(receiverName);

        Component senderMsg = formatPmMessage(senderFmt, color,
                senderNameText, receiverNameText, processedMessage);
        Component receiverMsg = formatPmMessage(receiverFmt, color,
                senderNameText, receiverNameText, processedMessage);

        receiver.sendSystemMessage(receiverMsg);
        source.sendSuccess(() -> senderMsg, false);

        // Track for /reply from console only (players cannot reply to console)
        consoleReplyTarget = receiver.getUUID();

        // SocialSpy relay for private messages
        if (server != null) {
            ChatHandler.relaySocialSpy(server, senderName,
                    "[→ " + receiverName + "] " + message,
                    SocialSpyManager.Channel.PM, "PM");
        }

        if (viaStyle.CONFIG == null || viaStyle.CONFIG.logPrivatesToConsole) {
            viaStyle.LOGGER.info("[PM] {} -> {}: {}", senderName, receiverName, message);
        }

        // ── Per-player PM sound ────────────────────────────────────────────
        if (cfg != null && cfg.pmSoundEnabled && viaStyle.isPmSoundEnabled(receiver.getUUID())) {
            Identifier soundId = Identifier.tryParse(cfg.pmSoundId);
            if (soundId != null) {
                BuiltInRegistries.SOUND_EVENT.get(soundId)
                        .ifPresent(entry -> receiver.connection.send(new ClientboundSoundPacket(
                                entry, SoundSource.PLAYERS,
                                receiver.getX(), receiver.getY(), receiver.getZ(),
                                (float) cfg.pmSoundVolume, (float) cfg.pmSoundPitch,
                                receiver.getRandom().nextLong())));
            }
        }

        return true;
    }

    private static Component buildClickableName(String playerName) {
        return Component.literal(playerName).withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand("/m " + playerName + " "))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                        Component.literal("/m " + playerName).withStyle(ChatFormatting.GRAY))));
    }

    private static Component formatPmMessage(String format,
                                        TextColor baseColor,
                                        Component senderName,
                                        Component receiverName,
                                        Component messageText) {
        if (format == null || format.isBlank()) {
            return Component.empty();
        }

        MutableComponent out = Component.empty();
        int cursor = 0;
        while (cursor < format.length()) {
            int next = findNextToken(format, cursor);
            if (next < 0) {
                String tail = format.substring(cursor);
                out.append(Component.literal(tail));
                break;
            }

            if (next > cursor) {
                String literal = format.substring(cursor, next);
                out.append(Component.literal(literal));
            }

            if (format.startsWith("{sender}", next)) {
                out.append(senderName);
                cursor = next + "{sender}".length();
            } else if (format.startsWith("{receiver}", next)) {
                out.append(receiverName);
                cursor = next + "{receiver}".length();
            } else if (format.startsWith("{message}", next)) {
                out.append(messageText);
                cursor = next + "{message}".length();
            } else {
                out.append(Component.literal(format.substring(next, next + 1)));
                cursor = next + 1;
            }
        }

        return out.withStyle(s -> s.withColor(baseColor));
    }

    private static int findNextToken(String format, int startIndex) {
        int senderIdx = format.indexOf("{sender}", startIndex);
        int receiverIdx = format.indexOf("{receiver}", startIndex);
        int messageIdx = format.indexOf("{message}", startIndex);

        int next = -1;
        if (senderIdx >= 0) next = senderIdx;
        if (receiverIdx >= 0 && (next < 0 || receiverIdx < next)) next = receiverIdx;
        if (messageIdx >= 0 && (next < 0 || messageIdx < next)) next = messageIdx;
        return next;
    }
}
