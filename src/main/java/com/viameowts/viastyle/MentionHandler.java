package com.viameowts.viastyle;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

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
                                       ServerPlayerEntity sender,
                                       String message) {
        processMentions(server, sender, message, null);
    }

    /**
     * Checks if the message contains any @mentions and notifies mentioned players.
     * If {@code allowedRecipients} is provided, only players in that collection
     * can be notified (used for local chat radius delivery).
     */
    public static void processMentions(MinecraftServer server,
                                       ServerPlayerEntity sender,
                                       String message,
                                       Collection<ServerPlayerEntity> allowedRecipients) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.mentionsEnabled) return;

        Matcher matcher = MENTION_PATTERN.matcher(message);
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        Set<UUID> allowed = null;
        if (allowedRecipients != null) {
            allowed = ConcurrentHashMap.newKeySet();
            for (ServerPlayerEntity recipient : allowedRecipients) {
                allowed.add(recipient.getUuid());
            }
        }

        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            for (ServerPlayerEntity target : players) {
                if (target.getName().getString().equalsIgnoreCase(mentionedName)) {
                    if (allowed != null && !allowed.contains(target.getUuid())) break;
                    // Skip if sender cannot see the vanished target
                    if (!VanishHelper.canSeePlayer(target, sender)) break;
                    // Stamp dedup cache first so GAME_MESSAGE scanner won't double-ping
                    recentPings.put(target.getUuid(), System.currentTimeMillis());
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
    public static Text highlightMentions(String message, TextColor baseColor,
                                         MinecraftServer server,
                                         ServerPlayerEntity sender) {
        return highlightMentions(message, baseColor, server, sender, false);
    }

    public static Text highlightMentions(String message, TextColor baseColor,
                                         MinecraftServer server,
                                         ServerPlayerEntity sender,
                                         boolean useMiniMessage) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.mentionsEnabled) {
            return parseMiniOrPlain(message, baseColor, useMiniMessage);
        }

        TextColor mentionColor = cfg.getMentionColor();
        Matcher matcher = MENTION_PATTERN.matcher(message);
        MutableText result = Text.empty();
        int last = 0;

        while (matcher.find()) {
            // Check if the mentioned name matches an online player visible to the sender
            String mentionedName = matcher.group(1);
            boolean isValidMention = false;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
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
                result.append(Text.literal(matcher.group())
                    .styled(s -> s.withColor(mentionColor)));
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

    private static Text parseMiniOrPlain(String text, TextColor baseColor, boolean useMiniMessage) {
        if (useMiniMessage) {
            try {
                if (ChatMiniMessageParser.containsTags(text)) {
                    return ChatMiniMessageParser.parse(text, baseColor);
                }
            } catch (Throwable t) {
                viaStyle.LOGGER.debug("[viaStyle] MiniMessage parse error: {}", t.getMessage());
            }
        }
        return Text.literal(text).styled(s -> s.withColor(baseColor));
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
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            for (ServerPlayerEntity target : players) {
                if (target.getName().getString().equalsIgnoreCase(mentionedName)) {
                    // Dedup: skip if already pinged within the last 2 seconds
                    Long last = recentPings.get(target.getUuid());
                    if (last != null && now - last < DEDUP_WINDOW_MS) break;
                    recentPings.put(target.getUuid(), now);
                    notifyMentionFromDiscord(target, discordSender);
                    break;
                }
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
    private static void notifyMentionFromDiscord(ServerPlayerEntity target, String senderName) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) return;

        if (cfg.mentionSound) {
            Registries.SOUND_EVENT.getEntry(Identifier.ofVanilla("entity.experience_orb.pickup"))
                    .ifPresent(entry -> target.networkHandler.sendPacket(new PlaySoundS2CPacket(
                            entry, SoundCategory.PLAYERS,
                            target.getX(), target.getY(), target.getZ(),
                            1.0f, 1.0f, target.getRandom().nextLong())));
        }

        String from = (senderName != null && !senderName.isBlank()) ? senderName : "Discord";
        target.sendMessage(
                Lang.getMutable("mention.notify")
                .append(Text.literal(from).styled(s -> s.withColor(TextColor.fromRgb(0xFCDE9D))))
                .append(Text.literal(" (Discord)").styled(s -> s.withColor(TextColor.fromRgb(0xB0C4DE))))
                .append(Text.literal("!").styled(s -> s.withColor(TextColor.fromRgb(0xFF5555)))),
                true); // actionBar = true
    }

    private static void notifyMention(ServerPlayerEntity target, ServerPlayerEntity sender) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg != null && cfg.mentionSound) {
            Registries.SOUND_EVENT.getEntry(Identifier.ofVanilla("entity.experience_orb.pickup"))
                    .ifPresent(entry -> target.networkHandler.sendPacket(new PlaySoundS2CPacket(
                            entry, SoundCategory.PLAYERS,
                            target.getX(), target.getY(), target.getZ(),
                            1.0f, 1.0f, target.getRandom().nextLong())));
        }

        // Action bar notification
        target.sendMessage(
                Lang.getMutable("mention.notify")
                .append(Text.literal(sender.getName().getString()).styled(s -> s.withColor(TextColor.fromRgb(0xFCDE9D))))
                .append(Text.literal("!").styled(s -> s.withColor(TextColor.fromRgb(0xFF5555)))),
                true); // actionBar = true
    }
}
