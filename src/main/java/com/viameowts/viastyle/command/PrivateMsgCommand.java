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
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        // Remove vanilla msg/tell/w/reply nodes so our version takes over
        dispatcher.getRoot().getChildren().removeIf(node -> {
            String n = node.getName();
            return n.equals("msg") || n.equals("tell") || n.equals("w")
                    || n.equals("reply") || n.equals("m") || n.equals("r");
        });

        // /msg <player> <message>  (also registered as /m and /w)
        var msgNode = CommandManager.literal("msg")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.msg"))
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            ServerPlayerEntity sender =
                                    ctx.getSource().getEntity() instanceof ServerPlayerEntity sp ? sp : null;
                            for (ServerPlayerEntity p : ctx.getSource().getServer()
                                    .getPlayerManager().getPlayerList()) {
                                // Hide vanished players from PM suggestions
                                if (sender != null && !VanishHelper.canSeePlayer(p, sender)) continue;
                                String name = p.getName().getString();
                                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(PrivateMsgCommand::sendMsg)))
                .build();
        dispatcher.getRoot().addChild(msgNode);
        dispatcher.getRoot().addChild(buildAlias("m",    msgNode));
        dispatcher.getRoot().addChild(buildAlias("w",    msgNode));
        dispatcher.getRoot().addChild(buildAlias("tell", msgNode));

        // /reply <message>  (also /r)
        var replyNode = CommandManager.literal("reply")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.reply"))
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(PrivateMsgCommand::reply))
                .build();
        dispatcher.getRoot().addChild(replyNode);
        dispatcher.getRoot().addChild(buildAlias("r", replyNode));
    }

    /** Creates a redirect alias pointing to {@code target}. */
    private static CommandNode<ServerCommandSource> buildAlias(
            String name,
            CommandNode<ServerCommandSource> target) {
        return CommandManager.literal(name).redirect(target).build();
    }

    // ── /msg ──────────────────────────────────────────────────────────────────

    private static int sendMsg(CommandContext<ServerCommandSource> context) {
        String targetName = StringArgumentType.getString(context, "player");
        String message    = StringArgumentType.getString(context, "message");

        ServerPlayerEntity target = context.getSource().getServer()
                .getPlayerManager().getPlayer(targetName);
        if (target == null) {
            context.getSource().sendError(Lang.get("error.player_not_found"));
            return 0;
        }

        if (context.getSource().getEntity() instanceof ServerPlayerEntity sender) {
            ViaStyleConfig cfg = viaStyle.CONFIG;
            if (target == sender && (cfg == null || !cfg.pmAllowSelfMessage)) {
                context.getSource().sendError(Lang.get("pm.error.self"));
                return 0;
            }
            return deliver(sender, target, message) ? 1 : 0;
        }

        return deliverFromConsole(context.getSource(), target, message) ? 1 : 0;
    }

    // ── /reply ─────────────────────────────────────────────────────────────────

    private static int reply(CommandContext<ServerCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");

        if (context.getSource().getEntity() instanceof ServerPlayerEntity sender) {
            UUID targetUuid = lastMsgFrom.get(sender.getUuid());
            if (targetUuid == null) {
                context.getSource().sendError(Lang.get("pm.error.no_reply"));
                return 0;
            }

            ServerPlayerEntity target = context.getSource().getServer()
                    .getPlayerManager().getPlayer(targetUuid);
            if (target == null) {
                context.getSource().sendError(Lang.get("pm.error.offline"));
                return 0;
            }

            return deliver(sender, target, message) ? 1 : 0;
        }

        if (consoleReplyTarget == null) {
            context.getSource().sendError(Lang.get("pm.error.no_reply"));
            return 0;
        }

        ServerPlayerEntity target = context.getSource().getServer()
                .getPlayerManager().getPlayer(consoleReplyTarget);
        if (target == null) {
            context.getSource().sendError(Lang.get("pm.error.offline"));
            return 0;
        }

        return deliverFromConsole(context.getSource(), target, message) ? 1 : 0;
    }

    // ── Core delivery ──────────────────────────────────────────────────────────

    private static boolean deliver(ServerPlayerEntity sender, ServerPlayerEntity receiver, String message) {
        // ── BanHammer mute check ───────────────────────────────────────────
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg != null && cfg.pmBanHammerMute && BanHammerHelper.isMuted(sender)) {
            sender.sendMessage(Lang.get("chat.muted"), false);
            return false;
        }

        // ── Ignore check ───────────────────────────────────────────────────
        if (IgnoreManager.isIgnoring(receiver.getUuid(), sender.getUuid())) {
            sender.sendMessage(Lang.get("pm.error.ignored"), false);
            return false;
        }

        // ── Vanish check — block PM to vanished players ────────────────────
        if (VanishHelper.isVanished(receiver)
                && !LuckPermsHelper.checkPlayerPermission(sender, "viastyle.pm.vanished", 2)) {
            sender.sendMessage(Lang.get("error.player_not_found"), false);
            return false;
        }

        String senderFmt   = cfg != null ? cfg.pmSenderFormat   : "[PM -> {receiver}] {message}";
        String receiverFmt = cfg != null ? cfg.pmReceiverFormat : "[PM <- {sender}] {message}";
        String colorStr    = cfg != null ? cfg.pmColor          : "LIGHT_PURPLE";

        TextColor color = cfg != null
                ? cfg.resolveColor(colorStr, TextColor.fromFormatting(Formatting.LIGHT_PURPLE))
                : TextColor.fromFormatting(Formatting.LIGHT_PURPLE);

        String senderName   = sender.getName().getString();
        String receiverName = receiver.getName().getString();

        net.minecraft.server.MinecraftServer server = sender.getEntityWorld().getServer();
        ChatSharePlaceholders.ProcessedMessage processed = ChatSharePlaceholders.processMessage(
            message,
            sender,
            server,
            color);

        Text senderNameText = buildClickableName(senderName);
        Text receiverNameText = buildClickableName(receiverName);

        Text senderMsg = formatPmMessage(senderFmt, color,
            senderNameText, receiverNameText, processed.component());
        Text receiverMsg = formatPmMessage(receiverFmt, color,
            senderNameText, receiverNameText, processed.component());

        sender.sendMessage(senderMsg, false);
        receiver.sendMessage(receiverMsg, false);

        // Track for /reply in both directions
        lastMsgFrom.put(receiver.getUuid(), sender.getUuid());
        lastMsgFrom.put(sender.getUuid(), receiver.getUuid());

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
        if (cfg != null && cfg.pmSoundEnabled && viaStyle.isPmSoundEnabled(receiver.getUuid())) {
            Identifier soundId = Identifier.tryParse(cfg.pmSoundId);
            if (soundId != null) {
                Registries.SOUND_EVENT.getEntry(soundId)
                        .ifPresent(entry -> receiver.networkHandler.sendPacket(new PlaySoundS2CPacket(
                                entry, SoundCategory.PLAYERS,
                                receiver.getX(), receiver.getY(), receiver.getZ(),
                                (float) cfg.pmSoundVolume, (float) cfg.pmSoundPitch,
                                receiver.getRandom().nextLong())));
            }
        }

        return true;
    }

    // ── Console delivery ────────────────────────────────────────────────────────

    private static boolean deliverFromConsole(ServerCommandSource source,
                                               ServerPlayerEntity receiver,
                                               String message) {
        ViaStyleConfig cfg = viaStyle.CONFIG;

        String senderFmt   = cfg != null ? cfg.pmSenderFormat   : "[PM -> {receiver}] {message}";
        String receiverFmt = cfg != null ? cfg.pmReceiverFormat : "[PM <- {sender}] {message}";
        String colorStr    = cfg != null ? cfg.pmColor          : "LIGHT_PURPLE";

        TextColor color = cfg != null
                ? cfg.resolveColor(colorStr, TextColor.fromFormatting(Formatting.LIGHT_PURPLE))
                : TextColor.fromFormatting(Formatting.LIGHT_PURPLE);

        String senderName   = Lang.get("pm.console_name").getString();
        String receiverName = receiver.getName().getString();

        net.minecraft.server.MinecraftServer server = source.getServer();

        // Console messages skip [item]/[pos]/[inv]/[ec] expansion, only highlight @mentions
        Text processedMessage = MentionHandler.highlightMentions(
                message, color, server, null, false);

        Text senderNameText = buildClickableName(senderName);
        Text receiverNameText = buildClickableName(receiverName);

        Text senderMsg = formatPmMessage(senderFmt, color,
                senderNameText, receiverNameText, processedMessage);
        Text receiverMsg = formatPmMessage(receiverFmt, color,
                senderNameText, receiverNameText, processedMessage);

        receiver.sendMessage(receiverMsg, false);
        source.sendFeedback(() -> senderMsg, false);

        // Track for /reply from console only (players cannot reply to console)
        consoleReplyTarget = receiver.getUuid();

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
        if (cfg != null && cfg.pmSoundEnabled && viaStyle.isPmSoundEnabled(receiver.getUuid())) {
            Identifier soundId = Identifier.tryParse(cfg.pmSoundId);
            if (soundId != null) {
                Registries.SOUND_EVENT.getEntry(soundId)
                        .ifPresent(entry -> receiver.networkHandler.sendPacket(new PlaySoundS2CPacket(
                                entry, SoundCategory.PLAYERS,
                                receiver.getX(), receiver.getY(), receiver.getZ(),
                                (float) cfg.pmSoundVolume, (float) cfg.pmSoundPitch,
                                receiver.getRandom().nextLong())));
            }
        }

        return true;
    }

    private static Text buildClickableName(String playerName) {
        return Text.literal(playerName).styled(s -> s
                .withClickEvent(new net.minecraft.text.ClickEvent.SuggestCommand("/m " + playerName + " "))
                .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(
                        Text.literal("/m " + playerName).formatted(Formatting.GRAY))));
    }

    private static Text formatPmMessage(String format,
                                        TextColor baseColor,
                                        Text senderName,
                                        Text receiverName,
                                        Text messageText) {
        if (format == null || format.isBlank()) {
            return Text.empty();
        }

        MutableText out = Text.empty();
        int cursor = 0;
        while (cursor < format.length()) {
            int next = findNextToken(format, cursor);
            if (next < 0) {
                String tail = format.substring(cursor);
                out.append(Text.literal(tail));
                break;
            }

            if (next > cursor) {
                String literal = format.substring(cursor, next);
                out.append(Text.literal(literal));
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
                out.append(Text.literal(format.substring(next, next + 1)));
                cursor = next + 1;
            }
        }

        return out.styled(s -> s.withColor(baseColor));
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
