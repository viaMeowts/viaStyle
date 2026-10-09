package com.viameowts.viastyle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import com.viameowts.viastyle.network.Network;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the player list (tab) — header, footer, and per-player display names.
 *
 * <p>Configuration is loaded from {@code config/viaStyle/tablist.json} via
 * {@link TabListConfig}. Supports placeholders, MiniMessage tags, and
 * hex colours ({@code #RRGGBB}), plus nick-colour integration.</p>
 *
 * <p>Tick-based updates are driven by {@code SERVER_TICK_END} event from
 * {@link viaStyleServer}.</p>
 */
public final class TabListManager {

    private static TabListConfig config;
    private static int tickCounter = 0;
    /** Set when LuckPerms data changed: the next tick re-sorts and refreshes everyone once. */
    private static volatile boolean fullRefreshRequested = false;

    private TabListManager() {}

    /** Initialise — call once from mod init. */
    public static void init() {
        config = TabListConfig.load();
    }

    /** Returns the current tab list config. */
    public static TabListConfig getConfig() {
        return config;
    }

    /** Asks for one full refresh (names, sort order, header and footer) on the next tick. */
    public static void requestFullRefresh() {
        fullRefreshRequested = true;
    }

    /** Reload config from disk. */
    public static void reloadConfig() {
        config = TabListConfig.load();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Tick-based update  (called from ServerTickEvents.END_SERVER_TICK)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Called every server tick.  Sends updates at the interval configured
     * in {@code tablist.json}.
     */
    public static void onTick(MinecraftServer server) {
        PlaceholderHelper.setServer(server);
        if (config == null || !config.enabled) return;
        if (fullRefreshRequested) {
            fullRefreshRequested = false;
            tickCounter = 0;
            updateAll(server);
            return;
        }
        if (config.updateIntervalTicks <= 0) return;

        tickCounter++;
        if (tickCounter < config.updateIntervalTicks) return;
        tickCounter = 0;

        updateAll(server);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Updates a single player's tab-list display name and sends them the
     * current header/footer.  Also broadcasts the name change to all clients.
     */
    public static void updatePlayer(ServerPlayer player) {
        if (config == null || !config.enabled) return;

        // 1) Update display name via duck interface
        if (config.modifyPlayerName) {
            Component formatted = formatPlayerName(player);
            if (player instanceof PlayerListNameAccess access) {
                access.viaStyle$setCustomListName(formatted);
            }
        }

        // 2) Send header/footer to this player
        sendHeaderFooter(player);

        // 3) Broadcast name update to all clients
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                            List.of(player)));
        }
    }

    /**
     * Updates tab-list entries for ALL online players (names + header/footer + sort order).
     */
    public static void updateAll(MinecraftServer server) {
        if (config == null || !config.enabled) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        // 1) Update all display names
        if (config.modifyPlayerName) {
            for (ServerPlayer p : players) {
                Component formatted = formatPlayerName(p);
                if (p instanceof PlayerListNameAccess access) {
                    access.viaStyle$setCustomListName(formatted);
                }
            }
        }

        // 2) Sort by LP group weight
        applyListOrder(players);

        // 3) Send header/footer to each player
        for (ServerPlayer p : players) {
            sendHeaderFooter(p);
        }

        // 4) Single bulk packet for name + order updates
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                                   ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER),
                        players));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  List order (sorting by LP group weight)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Assigns {@code listOrder} values to each player based on their
     * LuckPerms primary-group weight and the configured sort mode.
     *
     * <p>Minecraft uses {@code listOrder} as a sort key: <b>lower value = closer to the top</b>.
     *
     * <ul>
     *   <li><b>normal</b> — higher LP group weight → top of list (listOrder 0)</li>
     *   <li><b>reverse</b> — higher LP group weight → bottom of list (listOrder n-1)</li>
     *   <li><b>none</b> — no sorting, leave vanilla order intact</li>
     * </ul>
     */
    private static void applyListOrder(List<ServerPlayer> players) {
        String mode = viaStyle.CONFIG != null ? viaStyle.CONFIG.tabSortMode : "none";
        boolean spectatorsToBottom = viaStyle.CONFIG != null && viaStyle.CONFIG.tabSortSpectatorsToBottom;
        if ("none".equalsIgnoreCase(mode) && !spectatorsToBottom) return;

        // Pre-compute weight once per player — avoids O(n log n) LP reflection calls
        // and guarantees every comparison uses the exact same value.
        Map<UUID, Integer> weights = new HashMap<>(players.size() * 2);
        for (ServerPlayer p : players) {
            weights.put(p.getUUID(), LuckPermsHelper.info(p.getUUID()).weight());
        }

        // Sort: spectators last (if enabled), then weight descending, then name ascending.
        List<ServerPlayer> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> {
            // Spectator grouping (spectators always last)
            if (spectatorsToBottom) {
                boolean aSpec = a.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
                boolean bSpec = b.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
                if (aSpec != bSpec) return aSpec ? 1 : -1; // spectators after non-spectators
            }

            // Weight sorting (only if sort mode is not "none")
            if (!"none".equalsIgnoreCase(mode)) {
                int wa = weights.getOrDefault(a.getUUID(), 0);
                int wb = weights.getOrDefault(b.getUUID(), 0);
                if (wb != wa) return Integer.compare(wb, wa); // higher weight first
            }

            return a.getName().getString().compareToIgnoreCase(b.getName().getString());
        });

        boolean reverseMode = "reverse".equalsIgnoreCase(mode);

        // Assign listOrder (lower value = closer to top in MC tab list).
        //   normal  → index 0 (highest weight) gets order 0 (top of list)
        //   reverse → index 0 (highest weight) gets order n-1 (bottom of list)
        for (int i = 0; i < sorted.size(); i++) {
            ServerPlayer p = sorted.get(i);
            if (p instanceof PlayerListNameAccess access) {
                int order = reverseMode ? (sorted.size() - 1 - i) : i;
                access.viaStyle$setListOrder(order);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Header / Footer
    // ═══════════════════════════════════════════════════════════════════════

    private static void sendHeaderFooter(ServerPlayer player) {
        // Skip if the player has active Carpet HUD loggers (e.g. /log tps)
        // so that Carpet's data is not overwritten.
        if (CarpetHelper.hasActiveHud(player)) return;

        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        Component header = Component.empty();
        Component footer = Component.empty();

        if (config.showHeader && config.header != null) {
            header = buildMultiline(config.header, player, server);
        }
        if (config.showFooter && config.footer != null) {
            footer = buildMultiline(config.footer, player, server);
        }

        player.connection.send(
                new ClientboundTabListPacket(header, footer));
    }

    private static Component buildMultiline(List<String> lines, ServerPlayer player,
                                        MinecraftServer server) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result.append(Component.literal("\n"));
            String line = replacePlaceholders(lines.get(i), player, server);
            result.append(PlaceholderHelper.parseFormat(line, player));
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Player name formatting
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Formats a player's tab-list display name.
     *
     * <p>The {@code {player}} placeholder is special — if the player has a
     * nick colour, it is injected as gradient/coloured text.  All other
     * placeholders are simple string replacements.</p>
     */
    private static Component formatPlayerName(ServerPlayer player) {
        LuckPermsHelper.Info lp = LuckPermsHelper.info(player.getUUID());
        String format = config.formatFor(lp.group());
        if (format == null || format.isEmpty()) format = "{player}";

        // Replace simple placeholders first (not {player})
        String processed = format;
        processed = replaceToken(processed, "name", player.getName().getString());
        processed = replaceToken(processed, "ping", String.valueOf(getPlayerPing(player)));
        processed = replaceLuckPerms(processed, player, lp);
        processed = replaceToken(processed, "afk_suffix", getAfkSuffix(player));

        // Handle {player} — inject coloured text
        if (containsPlayerToken(processed)) {
            return buildWithPlayerPlaceholder(processed, player);
        }

        return PlaceholderHelper.parseFormat(processed, player);
    }

    private static final java.util.regex.Pattern LP_META = java.util.regex.Pattern.compile(
            "[{%]lp_meta:([A-Za-z0-9_.\\-]+)[}%]");

    /**
     * Fills the LuckPerms tokens: {@code {lp_prefix}}, {@code {lp_suffix}}, {@code {lp_group}},
     * {@code {lp_group_name}} (display name), {@code {lp_weight}} and {@code {lp_meta:key}}.
     */
    private static String replaceLuckPerms(String input, ServerPlayer player, LuckPermsHelper.Info lp) {
        String result = input;
        result = replaceToken(result, "lp_prefix", lp.prefix());
        result = replaceToken(result, "lp_suffix", lp.suffix());
        result = replaceToken(result, "lp_group_name", lp.groupName());
        result = replaceToken(result, "lp_group", lp.group());
        result = replaceToken(result, "lp_weight", String.valueOf(lp.weight()));
        if (result.contains("lp_meta:")) {
            result = LP_META.matcher(result).replaceAll(m -> {
                String value = LuckPermsHelper.getMetaValue(player.getUUID(), m.group(1));
                return java.util.regex.Matcher.quoteReplacement(
                        value == null ? "" : LegacyText.toMiniTags(value));
            });
        }
        return result;
    }

    private static String getAfkSuffix(ServerPlayer player) {
        if (AfkManager.isAfk(player.getUUID()) && viaStyle.CONFIG.afkSuffixEnabled && viaStyle.CONFIG.afkSuffix != null && !viaStyle.CONFIG.afkSuffix.isBlank()) {
            return viaStyle.CONFIG.afkSuffix;
        }
        return "";
    }

    /**
     * Splits on {@code {player}} and builds Text with the nick-coloured
     * name injected inline.
     */
    private static MutableComponent buildWithPlayerPlaceholder(String template,
                                                           ServerPlayer player) {
        MutableComponent result = Component.empty();
        String[] parts = template.replace("%player%", "{player}").split("\\{player\\}", -1);

        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(PlaceholderHelper.parseFormat(parts[i], player));
            }
            if (i < parts.length - 1) {
                // Insert the coloured player name
                MutableComponent coloredName = NickColorManager.getColoredName(player);
                if (coloredName != null) {
                    result.append(coloredName);
                } else {
                    result.append(Component.literal(player.getName().getString()));
                }
                // Append AFK suffix
                if (viaStyle.CONFIG.afkSuffixEnabled) {
                    String afkSuffix = getAfkSuffix(player);
                    if (!afkSuffix.isEmpty()) {
                        result.append(PlaceholderHelper.parseFormat(afkSuffix, player));
                    }
                }
            }
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Placeholder resolution (for header/footer)
    // ═══════════════════════════════════════════════════════════════════════

    private static String replacePlaceholders(String template, ServerPlayer player,
                                               MinecraftServer server) {
        if (template == null) return "";
        String result = template;

        result = replaceToken(result, "name", player.getName().getString());
        // Both our {online} token AND the PAPI %server:online% / %server:players% builtins
        // are replaced here with a per-viewer-aware vanish-aware count so the user doesn't
        // need to change their config to a custom placeholder.
        String visibleCount = String.valueOf(
                server != null ? VanishHelper.countVisiblePlayers(server, player) : 0);
        result = replaceToken(result, "online", visibleCount);
        result = result.replace("%server:online%", visibleCount);
        result = result.replace("%server:players%", visibleCount);
        result = result.replace("%viastyle:online%", visibleCount);
        result = replaceToken(result, "max", String.valueOf(
            server != null ? server.getPlayerList().getMaxPlayers() : 20));
        result = replaceToken(result, "ping", String.valueOf(getPlayerPing(player)));
        result = replaceToken(result, "tps", formatTps(server));
        result = replaceToken(result, "mspt", formatMspt(server));
        result = replaceToken(result, "server", Network.serverDisplayName());
        result = replaceToken(result, "server_id", Network.serverId());
        result = replaceLuckPerms(result, player, LuckPermsHelper.info(player.getUUID()));

        return result;
    }

        private static String replaceToken(String input, String token, String value) {
        String safeValue = value == null ? "" : value;
        return input
            .replace("{" + token + "}", safeValue)
            .replace("%" + token + "%", safeValue);
        }

        private static boolean containsPlayerToken(String input) {
        return input.contains("{player}") || input.contains("%player%");
        }

    private static int getPlayerPing(ServerPlayer player) {
        try {
            return player.connection.latency();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static String formatTps(MinecraftServer server) {
        if (server == null) return "N/A";
        double mspt = server.getCurrentSmoothedTickTime();
        double tps = mspt <= 50 ? 20.0 : 1000.0 / mspt;
        String colour;
        if (tps >= 18.0) colour = "<#98FB98>";
        else if (tps >= 15.0) colour = "<#FCDE9D>";
        else colour = "<#FF9292>";
        return colour + String.format("%.1f", tps);
    }

    private static String formatMspt(MinecraftServer server) {
        if (server == null) return "N/A";
        double mspt = server.getCurrentSmoothedTickTime();
        String colour;
        if (mspt <= 50.0) colour = "<#98FB98>";
        else if (mspt <= 75.0) colour = "<#FCDE9D>";
        else colour = "<#FF9292>";
        return colour + String.format("%.1f", mspt);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Colour + formatting parser  (#hex, §-codes, MiniMessage tags)
    // ═══════════════════════════════════════════════════════════════════════

    /**
    * Parses {@code §}-style codes, {@code #RRGGBB} hex colours,
    * and MiniMessage-style tags into
     * styled {@link Component}.
     *
     * <p>Supported MiniMessage tags:</p>
     * <ul>
     *   <li>{@code <gradient:#RRGGBB:#RRGGBB>text</gradient>} — per-char gradient</li>
     *   <li>{@code <shadow:#RRGGBB>text</shadow>} — text shadow colour</li>
     *   <li>{@code <shadow>text</shadow>} — default shadow (darker version of current colour)</li>
     *   <li>{@code <bold>}, {@code <italic>}, {@code <underlined>},
     *       {@code <strikethrough>}, {@code <obfuscated>} — formatting toggles</li>
     *   <li>{@code <reset>} — reset all formatting</li>
     *   <li>{@code <color:#RRGGBB>} or {@code <#RRGGBB>} — hex colour tag</li>
     * </ul>
     */
    static MutableComponent parseLegacyAndHex(String input) {
        if (input == null || input.isEmpty()) return Component.empty().copy();

        // Pre-process: handle MiniMessage tags that wrap content
        // We process from the inside out to handle nesting

        return parseMiniAndLegacy(input, Style.EMPTY);
    }

    /**
     * Full parser that handles MiniMessage tags + legacy codes.
     */
    private static MutableComponent parseMiniAndLegacy(String input, Style baseStyle) {
        MutableComponent result = Component.empty();

        int i = 0;
        int len = input.length();
        StringBuilder buf = new StringBuilder();
        Style currentStyle = baseStyle;

        while (i < len) {
            char c = input.charAt(i);

            // ── MiniMessage tags: <tag> ────────────────────────────
            if (c == '<') {
                int closeAngle = input.indexOf('>', i);
                if (closeAngle > i) {
                    String tagContent = input.substring(i + 1, closeAngle);

                    String lowerTag = tagContent.toLowerCase();

                    // ── <gradient:#...> or <gr:#...>text</gradient|gr> ──
                    if (lowerTag.startsWith("gradient:") || lowerTag.startsWith("gr:")) {
                        // Flush buffer
                        if (buf.length() > 0) {
                            result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        String gradientSpec = lowerTag.startsWith("gr:")
                                ? tagContent.substring("gr:".length())
                                : tagContent.substring("gradient:".length());
                        // A missing closing tag means "until the end", like in MiniMessage.
                        ClosingTagMatch endTag = findClosingTagMatch(input, closeAngle + 1, "gradient", "gr");
                        int innerEnd = endTag != null ? endTag.index() : len;
                        MutableComponent inner = parseMiniAndLegacy(input.substring(closeAngle + 1, innerEnd), currentStyle);
                        result.append(applyGradient(inner, gradientSpec));
                        i = endTag != null ? endTag.index() + endTag.length() : len;
                        continue;
                    }

                    // ── <shadow> / <shadow:#RRGGBB> — persistent or wrapping ──────
                    // Persistent: <shadow>text  (shadow stays until <reset> or </shadow>)
                    // Wrapping:   <shadow>text</shadow>
                    if (tagContent.toLowerCase().startsWith("shadow")) {
                        if (buf.length() > 0) {
                            result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        String shadowHexStr = null;
                        if (tagContent.contains(":")) {
                            shadowHexStr = tagContent.substring(tagContent.indexOf(':') + 1).trim();
                        }
                        int shadowArgb;
                        if (shadowHexStr != null && !shadowHexStr.isBlank()) {
                            TextColor tc = parseHex(shadowHexStr.startsWith("#") ? shadowHexStr : "#" + shadowHexStr);
                            shadowArgb = tc != null ? (0xFF000000 | tc.getValue()) : 0xFF3F3F3F;
                        } else {
                            shadowArgb = 0xFF3F3F3F; // standard MC dark shadow
                        }
                        int endShadow = findClosingTag(input, closeAngle + 1, "shadow");
                        if (endShadow != -1) {
                            // Wrapping mode
                            String innerText = input.substring(closeAngle + 1, endShadow);
                            result.append(parseMiniAndLegacy(innerText, currentStyle.withShadowColor(shadowArgb)));
                            i = endShadow + "</shadow>".length();
                        } else {
                            // Persistent mode — shadow stays active going forward
                            currentStyle = currentStyle.withShadowColor(shadowArgb);
                            i = closeAngle + 1;
                        }
                        continue;
                    }

                    // ── <bold>, <italic>, etc. modifier tags ───────
                    Style newStyle = tryParseFormattingTag(lowerTag, currentStyle);
                    if (newStyle != null) {
                        if (buf.length() > 0) {
                            result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        currentStyle = newStyle;
                        i = closeAngle + 1;
                        continue;
                    }

                    // ── Disable/closing tags: </bold> or <!bold>, </italic> or <!italic>, etc. ─────
                    if (lowerTag.startsWith("/") || lowerTag.startsWith("!")) {
                        String closingName = lowerTag.substring(1);
                        // </shadow> / <!shadow> — remove persistent shadow color
                        if ("shadow".equals(closingName)) {
                            if (buf.length() > 0) {
                                result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withShadowColor((Integer) null);
                            i = closeAngle + 1;
                            continue;
                        }
                        Style resetStyle = tryRemoveFormattingTag(closingName, currentStyle);
                        if (resetStyle != null) {
                            if (buf.length() > 0) {
                                result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = resetStyle;
                            i = closeAngle + 1;
                            continue;
                        }
                    }

                    // ── <#RRGGBB> or <color:#RRGGBB> ──────────────
                    if (lowerTag.startsWith("#") && lowerTag.length() == 7) {
                        TextColor tc = parseHex(lowerTag);
                        if (tc != null) {
                            if (buf.length() > 0) {
                                result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withColor(tc);
                            i = closeAngle + 1;
                            continue;
                        }
                    }
                    if (lowerTag.startsWith("color:")) {
                        String colorVal = lowerTag.substring("color:".length()).trim();
                        TextColor tc = parseHex(colorVal.startsWith("#") ? colorVal : "#" + colorVal);
                        if (tc != null) {
                            if (buf.length() > 0) {
                                result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withColor(tc);
                            i = closeAngle + 1;
                            continue;
                        }
                    }

                    // ── Named Minecraft colour (<dark_green>, <red>, <gold>, etc.) ──
                    try {
                        ChatFormatting namedFmt = ChatFormatting.valueOf(lowerTag.toUpperCase());
                        if (TextColor.fromLegacyFormat(namedFmt) != null) {
                            if (buf.length() > 0) {
                                result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withColor(TextColor.fromLegacyFormat(namedFmt));
                            i = closeAngle + 1;
                            continue;
                        }
                    } catch (IllegalArgumentException ignored) {}

                    // ── <reset> ────────────────────────────────────
                    if ("reset".equals(lowerTag)) {
                        if (buf.length() > 0) {
                            result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        currentStyle = baseStyle;
                        i = closeAngle + 1;
                        continue;
                    }
                }
                // Not a recognized tag — fall through to legacy parsing
            }

            // ── #RRGGBB hex colour ─────────────────────────────────
            if (c == '#' && i + 6 < len) {
                String hex = input.substring(i, i + 7);
                TextColor tc = parseHex(hex);
                if (tc != null) {
                    if (buf.length() > 0) {
                        result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                        buf.setLength(0);
                    }
                    currentStyle = baseStyle.withColor(tc);
                    i += 7;
                    continue;
                }
            }

            // ── §X colour/format codes ─────────────────────────────
            if (c == '§' && i + 1 < len) {
                char code = input.charAt(i + 1);
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (buf.length() > 0) {
                        result.append(Component.literal(buf.toString()).setStyle(currentStyle));
                        buf.setLength(0);
                    }
                    if (fmt == ChatFormatting.RESET) {
                        currentStyle = baseStyle;
                    } else if (TextColor.fromLegacyFormat(fmt) != null) {
                        currentStyle = baseStyle.withColor(fmt);
                    } else {
                        currentStyle = applyModifier(currentStyle, fmt);
                    }
                    i += 2;
                    continue;
                }
            }

            buf.append(c);
            i++;
        }

        if (buf.length() > 0) {
            result.append(Component.literal(buf.toString()).setStyle(currentStyle));
        }

        return result;
    }

    /**
     * Finds the position of {@code </tagName>} starting from {@code fromIdx}.
     * Returns the index of the opening {@code <} of the closing tag, or -1.
     */
    private static int findClosingTag(String input, int fromIdx, String tagName) {
        String closing = "</" + tagName + ">";
        int idx = input.toLowerCase().indexOf(closing, fromIdx);
        return idx;
    }

    private static ClosingTagMatch findClosingTagMatch(String input, int fromIdx, String... tagNames) {
        String lower = input.toLowerCase();
        int bestIdx = -1;
        int bestLen = 0;

        for (String tagName : tagNames) {
            String closing = "</" + tagName + ">";
            int idx = lower.indexOf(closing, fromIdx);
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
                bestLen = closing.length();
            }
        }

        if (bestIdx < 0) return null;
        return new ClosingTagMatch(bestIdx, bestLen);
    }

    private record ClosingTagMatch(int index, int length) {}

    /**
     * Recolours already parsed text with a per-character gradient. Other style parts of the
     * text (bold, italic, shadow...) are kept. The spec is colon-separated hex stops such as
     * {@code #ff0000:#00ff00}; with fewer than two valid stops the text is left as it is.
     */
    private static MutableComponent applyGradient(MutableComponent inner, String spec) {
        List<Integer> stops = new ArrayList<>();
        for (String part : spec.split(":")) {
            String hex = part.trim();
            if (!hex.startsWith("#")) hex = "#" + hex;
            TextColor tc = parseHex(hex);
            if (tc != null) stops.add(tc.getValue());
        }
        if (stops.size() < 2) return inner;

        List<String> chars = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        inner.<Object>visit((style, text) -> {
            text.codePoints().forEach(cp -> {
                chars.add(new String(Character.toChars(cp)));
                styles.add(style);
            });
            return java.util.Optional.empty();
        }, Style.EMPTY);

        int count = chars.size();
        if (count == 0) return Component.empty();
        int[] colors = stops.stream().mapToInt(Integer::intValue).toArray();
        MutableComponent result = Component.empty();
        for (int ci = 0; ci < count; ci++) {
            float progress = count == 1 ? 0f : (float) ci / (count - 1);
            int rgb = interpolateMulti(colors, progress);
            result.append(Component.literal(chars.get(ci))
                    .setStyle(styles.get(ci).withColor(TextColor.fromRgb(rgb))));
        }
        return result;
    }

    /**
     * Interpolates across multiple colour stops (0.0 → first, 1.0 → last).
     */
    private static int interpolateMulti(int[] colors, float progress) {
        if (progress <= 0f) return colors[0];
        if (progress >= 1f) return colors[colors.length - 1];
        int segments = colors.length - 1;
        float scaled = progress * segments;
        int seg = Math.min((int) scaled, segments - 1);
        float local = scaled - seg;
        return lerpColor(colors[seg], colors[seg + 1], local);
    }

    /**
     * Returns a modified Style if the tag name is a recognised formatting
     * toggle, or {@code null} if unrecognised.
     */
    private static Style tryParseFormattingTag(String tag, Style current) {
        return switch (tag) {
            case "bold", "b"              -> current.withBold(true);
            case "italic", "i", "em"      -> current.withItalic(true);
            case "underlined", "u"        -> current.withUnderlined(true);
            case "strikethrough", "st"    -> current.withStrikethrough(true);
            case "obfuscated", "obf"      -> current.withObfuscated(true);
            default                       -> null;
        };
    }

    /**
     * Removes a formatting modifier when a closing tag is encountered.
     */
    private static Style tryRemoveFormattingTag(String tag, Style current) {
        return switch (tag) {
            case "bold", "b"              -> current.withBold(false);
            case "italic", "i", "em"      -> current.withItalic(false);
            case "underlined", "u"        -> current.withUnderlined(false);
            case "strikethrough", "st"    -> current.withStrikethrough(false);
            case "obfuscated", "obf"      -> current.withObfuscated(false);
            case "reset"                  -> Style.EMPTY;
            default                       -> null;
        };
    }

    private static Style applyModifier(Style style, ChatFormatting fmt) {
        return switch (fmt) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style;
        };
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static TextColor parseHex(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) return null;
        try {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return TextColor.fromRgb(rgb);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
