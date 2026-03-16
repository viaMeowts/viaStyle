package com.viameowts.viastyle;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    private static void onChatMessage(SignedMessage message, ServerPlayerEntity sender,
                                      net.minecraft.network.message.MessageType.Parameters params) {
        String rawMessage = message.getContent().getString();
        MinecraftServer server = sender.getEntityWorld().getServer();
        if (server == null) return;

        // ── BanHammer mute check ───────────────────────────────────────────
        if (BanHammerHelper.isMuted(sender)) {
            sender.sendMessage(Lang.get("chat.muted"), false);
            return;
        }

        ViaStyleConfig cfg = viaStyle.CONFIG;
        String staffTrigger  = (cfg != null && cfg.staffTrigger  != null) ? cfg.staffTrigger  : "\\";
        String globalTrigger = (cfg != null && cfg.globalTrigger != null) ? cfg.globalTrigger : "!";

        // ── Staff chat ─────────────────────────────────────────────────────
        if (!staffTrigger.isEmpty() && rawMessage.startsWith(staffTrigger)) {
            if (hasStaffPermission(sender)) {
                String content = rawMessage.substring(staffTrigger.length()).trim();
                if (!content.isEmpty()) {
                    handleStaffMessage(server, sender, content);
                    ScarpetHelper.firePlayerMessage(sender, content);
                }
            } else {
                sender.sendMessage(Lang.get("chat.staff_no_permission"), false);
            }
            return;
        }

        // ── Global / Local routing ─────────────────────────────────────────
        boolean hasGlobalPrefix = !globalTrigger.isEmpty() && rawMessage.startsWith(globalTrigger);
        boolean prefersPrefixForGlobal = viaStyle.getPlayerPrefersPrefixForGlobal(sender.getUuid());

        boolean isEffectivelyGlobal;
        String messageContent;

        if (hasGlobalPrefix) {
            isEffectivelyGlobal = prefersPrefixForGlobal;
            messageContent = rawMessage.substring(globalTrigger.length()).trim();
        } else {
            isEffectivelyGlobal = !prefersPrefixForGlobal;
            messageContent = rawMessage;
        }

        if (messageContent.isEmpty()) return;

        if (isEffectivelyGlobal) {
            handleGlobalMessage(server, sender, messageContent);
        } else {
            handleLocalMessage(server, sender, messageContent);
        }

        // ── Scarpet event ──────────────────────────────────────────────────
        ScarpetHelper.firePlayerMessage(sender, messageContent);
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

    private static boolean hasStaffPermission(ServerPlayerEntity player) {
        if (LuckPermsHelper.hasPermission(player.getUuid(), "viastyle.staff")) return true;
        return LuckPermsHelper.hasOpLevel(player, 2);
    }

    private static void handleStaffMessage(MinecraftServer server,
                                           ServerPlayerEntity sender,
                                           String content) {
        ViaStyleConfig cfg = viaStyle.CONFIG;

        Map<String, Text> tokens = buildTokens(cfg, sender, content,
                cfg.staffPrefix, cfg.getStaffPrefixColor(),
                cfg.getStaffNameColor(), cfg.getStaffMessageColor(), server);

        MutableText assembled = parseTemplate(cfg.staffFormat, tokens);
        Text finalMsg = PlaceholderHelper.process(assembled, sender);

        // Deliver only to players with staff permission (or OP) + the sender
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (hasStaffPermission(p)) {
                p.sendMessage(finalMsg, false);
            }
        }

        // ── Console log ────────────────────────────────────────────────────
        if (viaStyle.CONFIG != null && viaStyle.CONFIG.logStaffToConsole) {
            logToConsole("Staff", sender.getName().getString(), content);
        }

        // SocialSpy relay for staff chat
        relaySocialSpy(server, sender, content, SocialSpyManager.Channel.STAFF, "Staff");
    }

    // ── Component builders ─────────────────────────────────────────────────────

    /** Returns an empty Text when timestamps are disabled, otherwise a styled "[HH:mm] " component. */
    private static Text buildTimestamp(ViaStyleConfig cfg) {
        if (!cfg.showTimestamp) return Text.empty();
        try {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern(cfg.timestampFormat));
            return colored("[" + time + "] ", cfg.getTimestampColor());
        } catch (DateTimeParseException | IllegalArgumentException e) {
            viaStyle.LOGGER.warn("[viaStyle] Invalid timestampFormat '{}': {}", cfg.timestampFormat, e.getMessage());
            return Text.empty();
        }
    }

    /** Wraps a string literal with a single {@link TextColor}. */
    private static MutableText colored(String str, TextColor color) {
        return Text.literal(str).styled(s -> s.withColor(color));
    }

    /**
     * Parses a legacy-colour-coded string ({@code §} and {@code &} codes) into a
     * styled {@link MutableText}. Useful for LuckPerms prefixes/suffixes that may
     * contain legacy formatting codes.
     */
    private static MutableText parseLegacyColors(String input) {
        if (input == null || input.isEmpty()) return Text.empty();
        // Full Patbox format pipeline (falls back to built-in parser if PAPI absent).
        // Pass null as player — LP prefixes don't need per-player placeholder resolution.
        return Text.empty().append(PlaceholderHelper.parseFormat(input, null));
    }

    // ── Format template engine ─────────────────────────────────────────────────

    /**
     * Builds the final message {@link Text} from a format template string and a
     * map of token values.
     *
     * <p>Template example: {@code "{timestamp}{prefix} {lp_prefix}{name}: {message}"}</p>
     *
     * <p>Any token not present in the map is kept as literal text in the output.</p>
     */
    private static MutableText parseTemplate(String template, Map<String, Text> tokens) {
        MutableText result = Text.empty();
        Matcher m = TOKEN.matcher(template);
        int last = 0;

        while (m.find()) {
            if (m.start() > last) {
                result.append(Text.literal(template.substring(last, m.start())));
            }
            String key = m.group(1);
            Text value = tokens.get(key);
            if (value != null) {
                result.append(value);
            } else {
                // Unknown token — keep as literal
                result.append(Text.literal(m.group(0)));
            }
            last = m.end();
        }
        if (last < template.length()) {
            result.append(Text.literal(template.substring(last)));
        }
        return result;
    }

    // ── Chat handlers ──────────────────────────────────────────────────────────

    private static void handleGlobalMessage(MinecraftServer server,
                                            ServerPlayerEntity sender,
                                            String messageContent) {
        ViaStyleConfig cfg = viaStyle.CONFIG;

        Map<String, Text> tokens = buildTokens(cfg, sender, messageContent,
                cfg.globalPrefix, cfg.getGlobalPrefixColor(),
                cfg.getGlobalNameColor(), cfg.getGlobalMessageColor(), server);

        MutableText assembled = parseTemplate(cfg.globalFormat, tokens);
        Text finalMsg = PlaceholderHelper.process(assembled, sender);

        for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            // Skip if recipient ignores sender
            if (recipient != sender && IgnoreManager.isIgnoring(recipient.getUuid(), sender.getUuid())) {
                continue;
            }
            recipient.sendMessage(finalMsg, false);
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
        if (BlockBotHelper.isAvailable()) {
            String channel = cfg.blockbotGlobalChannel;
            if (channel != null && !channel.isEmpty()) {
                BlockBotHelper.relayToDiscord(sender, messageContent, channel);
            }
        }
    }

    private static void handleLocalMessage(MinecraftServer server,
                                           ServerPlayerEntity sender,
                                           String messageContent) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        double radiusSquared = cfg.localChatRadius * cfg.localChatRadius;

        Map<String, Text> tokens = buildTokens(cfg, sender, messageContent,
                cfg.localPrefix, cfg.getLocalPrefixColor(),
                cfg.getLocalNameColor(), cfg.getLocalMessageColor(), server);

        MutableText assembled = parseTemplate(cfg.localFormat, tokens);
        Text finalMsg = PlaceholderHelper.process(assembled, sender);

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        ServerWorld senderWorld = sender.getEntityWorld();
        int recipientCount = 0;

        for (ServerPlayerEntity recipient : players) {
            if (recipient == sender) {
                recipient.sendMessage(finalMsg, false);
                continue;
            }
            // Skip if recipient ignores sender
            if (IgnoreManager.isIgnoring(recipient.getUuid(), sender.getUuid())) {
                continue;
            }
            if (recipient.getEntityWorld() == senderWorld
                    && sender.squaredDistanceTo(recipient) <= radiusSquared) {
                recipient.sendMessage(finalMsg, false);
                recipientCount++;
            }
        }

        // Notify sender if nobody was in range to receive the message
        if (cfg.localNooneHeard && recipientCount == 0) {
            String hint = (cfg.localNooneHeardMessage != null && !cfg.localNooneHeardMessage.isBlank())
                    ? cfg.localNooneHeardMessage : "Nobody heard you.";
            sender.sendMessage(Text.literal(hint).formatted(Formatting.GRAY, Formatting.ITALIC), false);
        }

        // @mentions
        MentionHandler.processMentions(server, sender, messageContent);

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

    private static Map<String, Text> buildTokens(ViaStyleConfig cfg,
                                                  ServerPlayerEntity sender,
                                                  String messageContent,
                                                  String prefix,
                                                  TextColor prefixColor,
                                                  TextColor nameColor,
                                                  TextColor messageColor,
                                                  MinecraftServer server) {
        Map<String, Text> tokens = new LinkedHashMap<>();
        tokens.put("timestamp",  buildTimestamp(cfg));
        tokens.put("prefix",     colored(prefix, prefixColor));
        tokens.put("lp_prefix",  parseLegacyColors(LuckPermsHelper.getPrefix(sender.getUuid())));
        tokens.put("lp_suffix",  parseLegacyColors(LuckPermsHelper.getSuffix(sender.getUuid())));

        // Nick colour from permission / file overrides the section default
        MutableText nickColored = viaStyle.CONFIG.nickColorInChat
                ? NickColorManager.getColoredName(sender) : null;
        MutableText nameText = nickColored != null
                ? nickColored
                : colored(sender.getName().getString(), nameColor);

        // Click name → suggest /m <player>  |  Hover → tooltip
        String playerName = sender.getName().getString();
        nameText = nameText.styled(s -> s
                .withClickEvent(new ClickEvent.SuggestCommand("/m " + playerName + " "))
                .withHoverEvent(new HoverEvent.ShowText(
                        Text.literal("/m " + playerName).formatted(Formatting.GRAY))));
        tokens.put("name", nameText);

        // Message with @mention highlighting
        tokens.put("message", MentionHandler.highlightMentions(messageContent, messageColor, server, sender));
        return tokens;
    }

    // ── SocialSpy relay ────────────────────────────────────────────────────────

    /**
     * Sends a spy copy of a chat message to all online socialspy listeners
     * for the given channel, excluding the sender and anyone who already
     * received the message normally.
     */
    public static void relaySocialSpy(MinecraftServer server,
                                       ServerPlayerEntity sender,
                                       String content,
                                       SocialSpyManager.Channel channel,
                                       String channelLabel) {
        if (viaStyle.CONFIG == null) return;

        Set<java.util.UUID> spies = SocialSpyManager.getSpiesForChannel(channel);
        if (spies.isEmpty()) return;

        Text spyMsg = Text.literal("[Spy/" + channelLabel + "] ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(sender.getName().getString()).formatted(Formatting.GRAY))
                .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(content).formatted(Formatting.GRAY));

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p == sender) continue;
            if (spies.contains(p.getUuid())) {
                // Re-check permission — it may have been revoked since spy was enabled
                boolean hasSpyPerm = LuckPermsHelper.hasOpLevel(p, 2)
                        || LuckPermsHelper.hasPermission(p.getUuid(), "viastyle.socialspy");
                if (!hasSpyPerm) {
                    // Auto-disable spy for this player so the state stays clean
                    SocialSpyManager.disableAll(p.getUuid());
                    continue;
                }
                // For staff channel, don't duplicate if they already see it as staff
                if (channel == SocialSpyManager.Channel.STAFF && hasStaffPermission(p)) continue;
                p.sendMessage(spyMsg, false);
            }
        }
    }
}