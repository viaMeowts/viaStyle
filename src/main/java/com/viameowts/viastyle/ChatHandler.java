package com.viameowts.viastyle;

import com.viameowts.viastyle.network.ChatChannel;
import com.viameowts.viastyle.network.Network;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {

    /**
     * Matches any {@code {token_name}} placeholder inside a format string.
     * Known tokens: timestamp, prefix, name, message, lp_prefix, lp_suffix.
     * Unknown tokens are kept as literal text.
     */
    private static final Pattern TOKEN = Pattern.compile("\\{(\\w+)\\}");

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            onChatMessage(message, sender, params);
            return false;
        });
    }

    private static void onChatMessage(PlayerChatMessage message, ServerPlayer sender,
                                      net.minecraft.network.chat.ChatType.Bound params) {
        String rawMessage = message.decoratedContent().getString();
        MinecraftServer server = sender.level().getServer();
        if (server == null) return;

        // ── BanHammer mute check ───────────────────────────────────────────
        if (BanHammerHelper.isMuted(sender)) {
            sender.sendSystemMessage(Lang.get("chat.muted"));
            return;
        }

        ViaStyleConfig cfg = viaStyle.CONFIG;
        boolean network = Network.enabled();
        String staffTrigger  = (cfg != null && cfg.staffTrigger  != null) ? cfg.staffTrigger  : "\\";
        String globalTrigger = (cfg != null && cfg.globalTrigger != null) ? cfg.globalTrigger : "!";
        String planetTrigger = (cfg != null && cfg.planetTrigger != null && network) ? cfg.planetTrigger : "";
        String localTrigger  = (cfg != null && cfg.localTrigger  != null) ? cfg.localTrigger  : "";

        // ── Pick the channel: trigger first, then the player's default ─────
        ChatChannel defaultChannel = viaStyle.getDefaultChannel(sender.getUUID());
        ChatChannel channel;
        String messageContent;
        if (!staffTrigger.isEmpty() && rawMessage.startsWith(staffTrigger)) {
            channel = ChatChannel.STAFF;
            messageContent = rawMessage.substring(staffTrigger.length());
        } else if (!globalTrigger.isEmpty() && rawMessage.startsWith(globalTrigger)) {
            messageContent = rawMessage.substring(globalTrigger.length());
            if (network) {
                channel = ChatChannel.NETWORK;
            } else {
                // Standalone: the trigger flips between local and server-wide chat.
                channel = defaultChannel == ChatChannel.LOCAL ? ChatChannel.PLANET : ChatChannel.LOCAL;
            }
        } else if (!planetTrigger.isEmpty() && rawMessage.startsWith(planetTrigger)) {
            channel = ChatChannel.PLANET;
            messageContent = rawMessage.substring(planetTrigger.length());
        } else if (!localTrigger.isEmpty() && rawMessage.startsWith(localTrigger)) {
            channel = ChatChannel.LOCAL;
            messageContent = rawMessage.substring(localTrigger.length());
        } else {
            channel = defaultChannel;
            messageContent = rawMessage;
        }
        if (channel == ChatChannel.NETWORK && !network) channel = ChatChannel.PLANET;
        messageContent = messageContent.trim();
        if (messageContent.isEmpty()) return;

        switch (channel) {
            case STAFF -> {
                if (!hasStaffPermission(sender)) {
                    sender.sendSystemMessage(Lang.get("chat.staff_no_permission"));
                    return;
                }
                handleStaffMessage(server, sender, messageContent);
            }
            case NETWORK -> handleNetworkMessage(server, sender, messageContent);
            case PLANET -> handleGlobalMessage(server, sender, messageContent);
            case LOCAL -> handleLocalMessage(server, sender, messageContent);
        }

        // ── Reset AFK timer ────────────────────────────────────────────────
        AfkManager.onActivity(sender.getUUID());
    }

    // ── Console logging helper ──────────────────────────────────────────────────

    /**
     * Logs a chat message to the server console in plain text.
     * Format: {@code [Channel] senderName: content}
     */
    private static void logToConsole(String channel, String senderName, String content) {
        viaStyle.LOGGER.info("[{}] {}: {}", channel, senderName, content);
    }

    // ── Staff permission / handler ─────────────────────────────────────────────

    public static boolean hasStaffPermission(ServerPlayer player) {
        return LuckPermsHelper.checkPlayerPermission(player, "viastyle.staff", 2);
    }

    private static void handleStaffMessage(MinecraftServer server,
                                           ServerPlayer sender,
                                           String content) {
        ViaStyleConfig cfg = viaStyle.CONFIG;

        Map<String, Component> tokens = buildTokens(cfg, sender, content,
                cfg.staffPrefix, cfg.getStaffPrefixColor(),
                cfg.getStaffNameColor(), cfg.getStaffMessageColor(), server);

        MutableComponent assembled = parseTemplate(cfg.staffFormat, tokens);
        Component finalMsg = PlaceholderHelper.process(assembled, sender);

        // Deliver only to players with staff permission (or OP) + the sender
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (hasStaffPermission(p)) {
                p.sendSystemMessage(finalMsg);
            }
        }

        // ── Console log ────────────────────────────────────────────────────
        if (viaStyle.CONFIG != null && viaStyle.CONFIG.logStaffToConsole) {
            logToConsole("Staff", sender.getName().getString(), content);
        }

        // SocialSpy relay for staff chat
        relaySocialSpy(server, sender, content, SocialSpyManager.Channel.STAFF, "Staff");

        // Staff chat is network-wide in network mode
        Network.sendChat(sender, ChatChannel.STAFF, finalMsg, content);
    }

    // ── Network chat ───────────────────────────────────────────────────────────

    private static void handleNetworkMessage(MinecraftServer server,
                                             ServerPlayer sender,
                                             String messageContent) {
        ViaStyleConfig cfg = viaStyle.CONFIG;

        Map<String, Component> tokens = buildTokens(cfg, sender, messageContent,
                cfg.networkPrefix, cfg.getNetworkPrefixColor(),
                cfg.getNetworkNameColor(), cfg.getNetworkMessageColor(), server);

        MutableComponent assembled = parseTemplate(cfg.networkFormat, tokens);
        Component finalMsg = PlaceholderHelper.process(assembled, sender);

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient != sender && IgnoreManager.isIgnoring(recipient.getUUID(), sender.getUUID())) {
                continue;
            }
            recipient.sendSystemMessage(finalMsg);
        }
        Network.sendChat(sender, ChatChannel.NETWORK, finalMsg, messageContent);

        MentionHandler.processMentions(server, sender, messageContent);

        if (cfg.logNetworkToConsole) {
            logToConsole("Network", sender.getName().getString(), messageContent);
        }
        Network.relayNetworkToDiscord(sender.getName().getString(), messageContent);
    }

    // ── Component builders ─────────────────────────────────────────────────────

    /** Returns an empty Text when timestamps are disabled, otherwise a styled "[HH:mm] " component. */
    private static Component buildTimestamp(ViaStyleConfig cfg) {
        if (!cfg.showTimestamp) return Component.empty();
        try {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern(cfg.timestampFormat));
            return colored("[" + time + "] ", cfg.getTimestampColor());
        } catch (DateTimeParseException | IllegalArgumentException e) {
            viaStyle.LOGGER.warn("[viaStyle] Invalid timestampFormat '{}': {}", cfg.timestampFormat, e.getMessage());
            return Component.empty();
        }
    }

    /** Wraps a string literal with a single {@link TextColor}. */
    private static MutableComponent colored(String str, TextColor color) {
        return Component.literal(str).withStyle(s -> s.withColor(color));
    }

    /**
     * Parses a formatting string into a styled {@link MutableComponent}.
     * Used for LuckPerms prefixes/suffixes and supports MiniMessage tags.
     */
    private static MutableComponent parseLegacyColors(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        // Full Patbox format pipeline (falls back to built-in parser if PAPI absent).
        // Pass null as player — LP prefixes don't need per-player placeholder resolution.
        return Component.empty().append(PlaceholderHelper.parseFormat(input, null));
    }

    // ── Format template engine ─────────────────────────────────────────────────

    /**
     * Builds the final message {@link Component} from a format template string and a
     * map of token values.
     *
     * <p>Template example: {@code "{timestamp}{prefix} {lp_prefix}{name}: {message}"}</p>
     *
     * <p>Any token not present in the map is kept as literal text in the output.</p>
     */
    private static MutableComponent parseTemplate(String template, Map<String, Component> tokens) {
        MutableComponent result = Component.empty();
        Matcher m = TOKEN.matcher(template);
        int last = 0;

        while (m.find()) {
            if (m.start() > last) {
                result.append(Component.literal(template.substring(last, m.start())));
            }
            String key = m.group(1);
            Component value = tokens.get(key);
            if (value != null) {
                result.append(value);
            } else {
                // Unknown token — keep as literal
                result.append(Component.literal(m.group(0)));
            }
            last = m.end();
        }
        if (last < template.length()) {
            result.append(Component.literal(template.substring(last)));
        }
        return result;
    }

    // ── Chat handlers ──────────────────────────────────────────────────────────

    private static void handleGlobalMessage(MinecraftServer server,
                                            ServerPlayer sender,
                                            String messageContent) {
        ViaStyleConfig cfg = viaStyle.CONFIG;

        Map<String, Component> tokens = buildTokens(cfg, sender, messageContent,
                cfg.globalPrefix, cfg.getGlobalPrefixColor(),
                cfg.getGlobalNameColor(), cfg.getGlobalMessageColor(), server);

        MutableComponent assembled = parseTemplate(cfg.globalFormat, tokens);
        Component finalMsg = PlaceholderHelper.process(assembled, sender);

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            // Skip if recipient ignores sender
            if (recipient != sender && IgnoreManager.isIgnoring(recipient.getUUID(), sender.getUUID())) {
                continue;
            }
            recipient.sendSystemMessage(finalMsg);
        }

        // @mentions
        MentionHandler.processMentions(server, sender, messageContent);

        // SocialSpy relay for global
        relaySocialSpy(server, sender, messageContent, SocialSpyManager.Channel.GLOBAL, "Global");

        // ── Console log ────────────────────────────────────────────────────
        if (cfg.logGlobalToConsole) {
            logToConsole("Global", sender.getName().getString(), messageContent);
        }

        // ── BlockBot relay (global) ────────────────────────────────────────
        // In network mode Discord gets the network chat instead (see Network).
        if (BlockBotHelper.isAvailable() && !Network.enabled()) {
            String channel = cfg.blockbotGlobalChannel;
            if (channel != null && !channel.isEmpty()) {
                BlockBotHelper.relayToDiscord(sender, messageContent, channel);
            }
        }
    }

    private static void handleLocalMessage(MinecraftServer server,
                                           ServerPlayer sender,
                                           String messageContent) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        double radiusSquared = cfg.localChatRadius * cfg.localChatRadius;

        Map<String, Component> tokens = buildTokens(cfg, sender, messageContent,
                cfg.localPrefix, cfg.getLocalPrefixColor(),
                cfg.getLocalNameColor(), cfg.getLocalMessageColor(), server);

        MutableComponent assembled = parseTemplate(cfg.localFormat, tokens);
        Component finalMsg = PlaceholderHelper.process(assembled, sender);

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<ServerPlayer> deliveredRecipients = new ArrayList<>();
        ServerLevel senderWorld = sender.level();
        int recipientCount = 0;

        for (ServerPlayer recipient : players) {
            if (recipient == sender) {
                recipient.sendSystemMessage(finalMsg);
                deliveredRecipients.add(recipient);
                continue;
            }
            // Skip if recipient ignores sender
            if (IgnoreManager.isIgnoring(recipient.getUUID(), sender.getUUID())) {
                continue;
            }
            if (recipient.level() == senderWorld
                    && sender.distanceToSqr(recipient) <= radiusSquared) {
                recipient.sendSystemMessage(finalMsg);
                deliveredRecipients.add(recipient);
                recipientCount++;
            }
        }

        // Notify sender if nobody was in range to receive the message
        if (cfg.localNooneHeard && recipientCount == 0) {
            String hint = (cfg.localNooneHeardMessage != null && !cfg.localNooneHeardMessage.isBlank())
                    ? cfg.localNooneHeardMessage : "Nobody heard you.";
            sender.sendSystemMessage(Component.literal(hint).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        // @mentions
        MentionHandler.processMentions(server, sender, messageContent, deliveredRecipients);

        // SocialSpy relay for local
        relaySocialSpy(server, sender, messageContent, SocialSpyManager.Channel.LOCAL, "Local");

        // ── Console log ────────────────────────────────────────────────────
        if (cfg.logLocalToConsole) {
            logToConsole("Local", sender.getName().getString(), messageContent);
        }

        // ── BlockBot relay (local) ─────────────────────────────────────────
        if (BlockBotHelper.isAvailable()) {
            String channel = cfg.blockbotLocalChannel;
            if (channel != null && !channel.isEmpty()) {
                BlockBotHelper.relayToDiscord(sender, messageContent, channel);
            }
        }
    }

    // ── Shared token builder ───────────────────────────────────────────────────

    private static Map<String, Component> buildTokens(ViaStyleConfig cfg,
                                                  ServerPlayer sender,
                                                  String messageContent,
                                                  String prefix,
                                                  TextColor prefixColor,
                                                  TextColor nameColor,
                                                  TextColor messageColor,
                                                  MinecraftServer server) {
        Map<String, Component> tokens = new LinkedHashMap<>();
        tokens.put("timestamp",  buildTimestamp(cfg));
        tokens.put("prefix",     colored(prefix, prefixColor));
        tokens.put("lp_prefix",  parseLegacyColors(LuckPermsHelper.getPrefix(sender.getUUID())));
        tokens.put("lp_suffix",  parseLegacyColors(LuckPermsHelper.getSuffix(sender.getUUID())));
        tokens.put("server",     colored(Network.serverDisplayName(), cfg.getServerTagColor()));
        tokens.put("server_tag", Network.enabled()
                ? colored(" (" + Network.serverDisplayName() + ")", cfg.getServerTagColor())
                : Component.empty());

        // Nick colour from permission / file overrides the section default
        MutableComponent nickColored = viaStyle.CONFIG.nickColorInChat
                ? NickColorManager.getColoredName(sender) : null;
        MutableComponent nameText = nickColored != null
                ? nickColored
                : colored(sender.getName().getString(), nameColor);

        // Click name → suggest /m <player>  |  Hover → tooltip
        String playerName = sender.getName().getString();
        nameText = nameText.withStyle(s -> s
                .withClickEvent(new ClickEvent.SuggestCommand("/m " + playerName + " "))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("/m " + playerName).withStyle(ChatFormatting.GRAY))));
        tokens.put("name", nameText);

        ChatSharePlaceholders.ProcessedMessage processed =
            ChatSharePlaceholders.processMessage(messageContent, sender, server, messageColor);

        // Message with [item]/[pos]/[inv]/[ec] replacements and mention highlighting
        tokens.put("message", processed.component());
        return tokens;
    }

    // ── SocialSpy relay ────────────────────────────────────────────────────────

    /**
     * Sends a spy copy of a chat message to all online socialspy listeners
     * for the given channel, excluding the sender and anyone who already
     * received the message normally.
     */
    public static void relaySocialSpy(MinecraftServer server,
                                       ServerPlayer sender,
                                       String content,
                                       SocialSpyManager.Channel channel,
                                       String channelLabel) {
        relaySocialSpy(server, sender != null ? sender.getName().getString() : "?", content, channel, channelLabel, sender);
    }

    public static void relaySocialSpy(MinecraftServer server,
                                       String senderName,
                                       String content,
                                       SocialSpyManager.Channel channel,
                                       String channelLabel) {
        relaySocialSpy(server, senderName, content, channel, channelLabel, null);
    }

    private static void relaySocialSpy(MinecraftServer server,
                                        String senderName,
                                        String content,
                                        SocialSpyManager.Channel channel,
                                        String channelLabel,
                                        ServerPlayer excludeSender) {
        if (viaStyle.CONFIG == null) return;

        Set<java.util.UUID> spies = SocialSpyManager.getSpiesForChannel(channel);
        if (spies.isEmpty()) return;

        Component spyMsg = Component.literal("[Spy/" + channelLabel + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(senderName).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(content).withStyle(ChatFormatting.GRAY));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == excludeSender) continue;
            if (spies.contains(p.getUUID())) {
                // Re-check permission — it may have been revoked since spy was enabled
                boolean hasSpyPerm = LuckPermsHelper.checkPlayerPermission(p, "viastyle.socialspy", 2);
                if (!hasSpyPerm) {
                    // Auto-disable spy for this player so the state stays clean
                    SocialSpyManager.disableAll(p.getUUID());
                    continue;
                }
                // For staff channel, don't duplicate if they already see it as staff
                if (channel == SocialSpyManager.Channel.STAFF && hasStaffPermission(p)) continue;
                p.sendSystemMessage(spyMsg);
            }
        }
    }
}