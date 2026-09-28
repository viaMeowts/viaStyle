package com.viameowts.viastyle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles @player mentions in chat messages.
 *
 * <p>When a chat message contains {@code @PlayerName}, the matching player
 * receives a highlighted message and a notification sound.</p>
 */
public final class MentionHandler {

    /** Pattern that finds @Name tokens (word-boundary after @). */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w{1,16})");

    /**
     * Dedup cache: UUID → timestamp of last ping (ms).
     * Prevents double-pinging when the same mention is processed both by the
     * in-game ChatHandler path AND the GAME_MESSAGE broadcast scanner.
     */
    private static final Map<UUID, Long> recentPings = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 2_000L;

    private MentionHandler() {}

    /**
     * Checks if the message contains any @mentions and notifies mentioned players.
     *
     * @param server  the MinecraftServer instance
     * @param sender  the player who sent the message
     * @param message the raw message text
     */
    public static void processMentions(MinecraftServer server,
                                       ServerPlayer sender,
                                       String message) {
        processMentions(server, sender, message, null);
    }

    /**
     * Checks if the message contains any @mentions and notifies mentioned players.
     * If {@code allowedRecipients} is provided, only players in that collection
     * can be notified (used for local chat radius delivery).
     */
    public static void processMentions(MinecraftServer server,
                                       ServerPlayer sender,
                                       String message,
                                       Collection<ServerPlayer> allowedRecipients) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.mentionsEnabled) return;

        Matcher matcher = MENTION_PATTERN.matcher(message);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Set<UUID> allowed = null;
        if (allowedRecipients != null) {
            allowed = ConcurrentHashMap.newKeySet();
            for (ServerPlayer recipient : allowedRecipients) {
                allowed.add(recipient.getUUID());
            }
        }

        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            for (ServerPlayer target : players) {
                if (target.getName().getString().equalsIgnoreCase(mentionedName)) {
                    if (allowed != null && !allowed.contains(target.getUUID())) break;
                    // Skip if sender cannot see the vanished target
                    if (!VanishHelper.canSeePlayer(target, sender)) break;
                    // Stamp dedup cache first so GAME_MESSAGE scanner won't double-ping
                    recentPings.put(target.getUUID(), System.currentTimeMillis());
                    notifyMention(target, sender);
                    break;
                }
            }
        }
    }

    /**
     * Returns a styled version of the message with @mentions highlighted.
     * Should be applied to the {message} token before assembly.
     */
    public static Component highlightMentions(String message, TextColor baseColor,
                                         MinecraftServer server,
                                         ServerPlayer sender) {
        return highlightMentions(message, baseColor, server, sender, false);
    }

    public static Component highlightMentions(String message, TextColor baseColor,
                                         MinecraftServer server,
                                         ServerPlayer sender,
                                         boolean useMiniMessage) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.mentionsEnabled) {
            return parseMiniOrPlain(message, baseColor, useMiniMessage);
        }

        TextColor mentionColor = cfg.getMentionColor();
        Matcher matcher = MENTION_PATTERN.matcher(message);
        MutableComponent result = Component.empty();
        int last = 0;

        while (matcher.find()) {
            // Check if the mentioned name matches an online player visible to the sender
            String mentionedName = matcher.group(1);
            boolean isValidMention = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getName().getString().equalsIgnoreCase(mentionedName)
                        && VanishHelper.canSeePlayer(p, sender)) {
                    isValidMention = true;
                    break;
                }
            }

            if (isValidMention) {
                if (matcher.start() > last) {
                    String segment = message.substring(last, matcher.start());
                    result.append(parseMiniOrPlain(segment, baseColor, useMiniMessage));
                }
                result.append(Component.literal(matcher.group())
                    .withStyle(s -> s.withColor(mentionColor)));
                last = matcher.end();
            }
        }

        if (last < message.length()) {
            String segment = message.substring(last);
            result.append(parseMiniOrPlain(segment, baseColor, useMiniMessage));
        }

        return last == 0
                ? parseMiniOrPlain(message, baseColor, useMiniMessage)
                : result;
    }

    private static Component parseMiniOrPlain(String text, TextColor baseColor, boolean useMiniMessage) {
        if (useMiniMessage) {
            try {
                if (ChatMiniMessageParser.containsTags(text)) {
                    return ChatMiniMessageParser.parse(text, baseColor);
                }
            } catch (Throwable t) {
                viaStyle.LOGGER.debug("[viaStyle] MiniMessage parse error: {}", t.getMessage());
            }
        }
        return Component.literal(text).withStyle(s -> s.withColor(baseColor));
    }

    /**
     * Scans a Discord-sourced message for {@code @PlayerName} mentions and notifies
     * matching online players.  This is used when BlockBot handles Discord→MC formatting
     * itself (passthrough mode) and we only want the mention side-effect.
     *
     * @param server          the MinecraftServer instance
     * @param rawMessage      the raw text of the Discord message
     * @param discordSender   display name of the Discord user, or {@code null} if unknown
     */
    public static void processDiscordMentions(MinecraftServer server,
                                              String rawMessage,
                                              String discordSender) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.mentionsEnabled || !cfg.discordMentionPing) return;
        if (server == null || rawMessage == null || rawMessage.isBlank()) return;

        long now = System.currentTimeMillis();
        Matcher matcher = MENTION_PATTERN.matcher(rawMessage);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            for (ServerPlayer target : players) {
                if (target.getName().getString().equalsIgnoreCase(mentionedName)) {
                    // Dedup: skip if already pinged within the last 2 seconds
                    Long last = recentPings.get(target.getUUID());
                    if (last != null && now - last < DEDUP_WINDOW_MS) break;
                    recentPings.put(target.getUUID(), now);
                    notifyMentionFromDiscord(target, discordSender);
                    break;
                }
            }
        }
    }

    /**
     * Mentions inside a network chat message that came from another server: pings the
     * mentioned players on this server (sound + action bar, like a local mention).
     */
    public static void processRemoteMentions(MinecraftServer server, String senderName, String rawMessage) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.mentionsEnabled) return;
        if (server == null || rawMessage == null || rawMessage.isBlank()) return;

        long now = System.currentTimeMillis();
        Matcher matcher = MENTION_PATTERN.matcher(rawMessage);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            for (ServerPlayer target : players) {
                if (!target.getName().getString().equalsIgnoreCase(mentionedName)) continue;
                Long last = recentPings.get(target.getUUID());
                if (last != null && now - last < DEDUP_WINDOW_MS) break;
                recentPings.put(target.getUUID(), now);
                if (cfg.mentionSound) {
                    BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace("entity.experience_orb.pickup"))
                            .ifPresent(entry -> target.connection.send(new ClientboundSoundPacket(
                                    entry, SoundSource.PLAYERS,
                                    target.getX(), target.getY(), target.getZ(),
                                    1.0f, 1.0f, target.getRandom().nextLong())));
                }
                target.sendOverlayMessage(Lang.getMutable("mention.notify")
                        .append(Component.literal(senderName).withStyle(s -> s.withColor(TextColor.fromRgb(0xFCDE9D))))
                        .append(Component.literal("!").withStyle(s -> s.withColor(TextColor.fromRgb(0xFF5555)))));
                break;
            }
        }
    }

    /**
     * Notifies a player that they were mentioned from Discord.
     * Plays the configured sound and shows an action-bar message.
     *
     * @param target      the player to notify
     * @param senderName  Discord display name of the sender (may be {@code null})
     */
    private static void notifyMentionFromDiscord(ServerPlayer target, String senderName) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) return;

        if (cfg.mentionSound) {
            BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace("entity.experience_orb.pickup"))
                    .ifPresent(entry -> target.connection.send(new ClientboundSoundPacket(
                            entry, SoundSource.PLAYERS,
                            target.getX(), target.getY(), target.getZ(),
                            1.0f, 1.0f, target.getRandom().nextLong())));
        }

        String from = (senderName != null && !senderName.isBlank()) ? senderName : "Discord";
        target.sendOverlayMessage(
                Lang.getMutable("mention.notify")
                .append(Component.literal(from).withStyle(s -> s.withColor(TextColor.fromRgb(0xFCDE9D))))
                .append(Component.literal(" (Discord)").withStyle(s -> s.withColor(TextColor.fromRgb(0xB0C4DE))))
                .append(Component.literal("!").withStyle(s -> s.withColor(TextColor.fromRgb(0xFF5555)))));
    }

    private static void notifyMention(ServerPlayer target, ServerPlayer sender) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg != null && cfg.mentionSound) {
            BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace("entity.experience_orb.pickup"))
                    .ifPresent(entry -> target.connection.send(new ClientboundSoundPacket(
                            entry, SoundSource.PLAYERS,
                            target.getX(), target.getY(), target.getZ(),
                            1.0f, 1.0f, target.getRandom().nextLong())));
        }

        // Action bar notification
        target.sendOverlayMessage(
                Lang.getMutable("mention.notify")
                .append(Component.literal(sender.getName().getString()).withStyle(s -> s.withColor(TextColor.fromRgb(0xFCDE9D))))
                .append(Component.literal("!").withStyle(s -> s.withColor(TextColor.fromRgb(0xFF5555)))));
    }
}
