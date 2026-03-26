package com.viameowts.viastyle;

import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

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
 * {@link TabListConfig}.  Supports placeholders, legacy colour codes ({@code &amp;6}),
 * hex colours ({@code #RRGGBB}), and nick-colour integration.</p>
 *
 * <p>Tick-based updates are driven by {@code SERVER_TICK_END} event from
 * {@link viaStyleServer}.</p>
 */
public final class TabListManager {

    private static TabListConfig config;
    private static int tickCounter = 0;

    private TabListManager() {}

    /** Initialise — call once from mod init. */
    public static void init() {
        config = TabListConfig.load();
    }

    /** Returns the current tab list config. */
    public static TabListConfig getConfig() {
        return config;
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
    public static void updatePlayer(ServerPlayerEntity player) {
        if (config == null || !config.enabled) return;

        // 1) Update display name via duck interface
        if (config.modifyPlayerName) {
            Text formatted = formatPlayerName(player);
            if (player instanceof PlayerListNameAccess access) {
                access.viaStyle$setCustomListName(formatted);
            }
        }

        // 2) Send header/footer to this player
        sendHeaderFooter(player);

        // 3) Broadcast name update to all clients
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            server.getPlayerManager().sendToAll(
                    new PlayerListS2CPacket(
                            EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME),
                            List.of(player)));
        }
    }

    /**
     * Updates tab-list entries for ALL online players (names + header/footer + sort order).
     */
    public static void updateAll(MinecraftServer server) {
        if (config == null || !config.enabled) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        // 1) Update all display names
        if (config.modifyPlayerName) {
            for (ServerPlayerEntity p : players) {
                Text formatted = formatPlayerName(p);
                if (p instanceof PlayerListNameAccess access) {
                    access.viaStyle$setCustomListName(formatted);
                }
            }
        }

        // 2) Sort by LP group weight
        applyListOrder(players);

        // 3) Send header/footer to each player
        for (ServerPlayerEntity p : players) {
            sendHeaderFooter(p);
        }

        // 4) Single bulk packet for name + order updates
        server.getPlayerManager().sendToAll(
                new PlayerListS2CPacket(
                        EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME,
                                   PlayerListS2CPacket.Action.UPDATE_LIST_ORDER),
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
    private static void applyListOrder(List<ServerPlayerEntity> players) {
        String mode = viaStyle.CONFIG != null ? viaStyle.CONFIG.tabSortMode : "none";
        boolean spectatorsToBottom = viaStyle.CONFIG != null && viaStyle.CONFIG.tabSortSpectatorsToBottom;
        if ("none".equalsIgnoreCase(mode) && !spectatorsToBottom) return;

        // Pre-compute weight once per player — avoids O(n log n) LP reflection calls
        // and guarantees every comparison uses the exact same value.
        Map<UUID, Integer> weights = new HashMap<>(players.size() * 2);
        for (ServerPlayerEntity p : players) {
            weights.put(p.getUuid(), LuckPermsHelper.getGroupWeight(p.getUuid()));
        }

        // Sort: spectators last (if enabled), then weight descending, then name ascending.
        List<ServerPlayerEntity> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> {
            // Spectator grouping (spectators always last)
            if (spectatorsToBottom) {
                boolean aSpec = a.interactionManager.getGameMode() == GameMode.SPECTATOR;
                boolean bSpec = b.interactionManager.getGameMode() == GameMode.SPECTATOR;
                if (aSpec != bSpec) return aSpec ? 1 : -1; // spectators after non-spectators
            }

            // Weight sorting (only if sort mode is not "none")
            if (!"none".equalsIgnoreCase(mode)) {
                int wa = weights.getOrDefault(a.getUuid(), 0);
                int wb = weights.getOrDefault(b.getUuid(), 0);
                if (wb != wa) return Integer.compare(wb, wa); // higher weight first
            }

            return a.getName().getString().compareToIgnoreCase(b.getName().getString());
        });

        boolean reverseMode = "reverse".equalsIgnoreCase(mode);

        // Assign listOrder (lower value = closer to top in MC tab list).
        //   normal  → index 0 (highest weight) gets order 0 (top of list)
        //   reverse → index 0 (highest weight) gets order n-1 (bottom of list)
        for (int i = 0; i < sorted.size(); i++) {
            ServerPlayerEntity p = sorted.get(i);
            if (p instanceof PlayerListNameAccess access) {
                int order = reverseMode ? (sorted.size() - 1 - i) : i;
                access.viaStyle$setListOrder(order);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Header / Footer
    // ═══════════════════════════════════════════════════════════════════════

    private static void sendHeaderFooter(ServerPlayerEntity player) {
        // Skip if the player has active Carpet HUD loggers (e.g. /log tps)
        // so that Carpet's data is not overwritten.
        if (CarpetHelper.hasActiveHud(player)) return;

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        Text header = Text.empty();
        Text footer = Text.empty();

        if (config.showHeader && config.header != null) {
            header = buildMultiline(config.header, player, server);
        }
        if (config.showFooter && config.footer != null) {
            footer = buildMultiline(config.footer, player, server);
        }

        player.networkHandler.sendPacket(
                new PlayerListHeaderS2CPacket(header, footer));
    }

    private static Text buildMultiline(List<String> lines, ServerPlayerEntity player,
                                        MinecraftServer server) {
        MutableText result = Text.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result.append(Text.literal("\n"));
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
    private static Text formatPlayerName(ServerPlayerEntity player) {
        String format = config.playerNameFormat;
        if (format == null || format.isEmpty()) format = "{player}";

        // Replace simple placeholders first (not {player})
        String processed = format;
        processed = replaceToken(processed, "name", player.getName().getString());
        processed = replaceToken(processed, "ping", String.valueOf(getPlayerPing(player)));
        processed = replaceToken(processed, "lp_prefix", legacyToAmpersand(LuckPermsHelper.getPrefix(player.getUuid())));
        processed = replaceToken(processed, "lp_suffix", legacyToAmpersand(LuckPermsHelper.getSuffix(player.getUuid())));

        // Handle {player} — inject coloured text
        if (containsPlayerToken(processed)) {
            return buildWithPlayerPlaceholder(processed, player);
        }

        return PlaceholderHelper.parseFormat(processed, player);
    }

    /**
     * Splits on {@code {player}} and builds Text with the nick-coloured
     * name injected inline.
     */
    private static MutableText buildWithPlayerPlaceholder(String template,
                                                           ServerPlayerEntity player) {
        MutableText result = Text.empty();
        String[] parts = template.replace("%player%", "{player}").split("\\{player\\}", -1);

        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(PlaceholderHelper.parseFormat(parts[i], player));
            }
            if (i < parts.length - 1) {
                // Insert the coloured player name
                MutableText coloredName = NickColorManager.getColoredName(player);
                if (coloredName != null) {
                    result.append(coloredName);
                } else {
                    result.append(Text.literal(player.getName().getString()));
                }
            }
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Placeholder resolution (for header/footer)
    // ═══════════════════════════════════════════════════════════════════════

    private static String replacePlaceholders(String template, ServerPlayerEntity player,
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
            server != null ? server.getPlayerManager().getMaxPlayerCount() : 20));
        result = replaceToken(result, "ping", String.valueOf(getPlayerPing(player)));
        result = replaceToken(result, "tps", formatTps(server));
        result = replaceToken(result, "mspt", formatMspt(server));
        result = replaceToken(result, "lp_prefix", legacyToAmpersand(
            LuckPermsHelper.getPrefix(player.getUuid())));
        result = replaceToken(result, "lp_suffix", legacyToAmpersand(
            LuckPermsHelper.getSuffix(player.getUuid())));

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

    private static String legacyToAmpersand(String input) {
        if (input == null) return "";
        return input.replace('§', '&');
    }

    private static int getPlayerPing(ServerPlayerEntity player) {
        try {
            return player.networkHandler.getLatency();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static String formatTps(MinecraftServer server) {
        if (server == null) return "N/A";
        double mspt = server.getAverageTickTime();
        double tps = mspt <= 50 ? 20.0 : 1000.0 / mspt;
        String colour;
        if (tps >= 18.0) colour = "&a";
        else if (tps >= 15.0) colour = "&e";
        else colour = "&c";
        return colour + String.format("%.1f", tps);
    }

    private static String formatMspt(MinecraftServer server) {
        if (server == null) return "N/A";
        double mspt = server.getAverageTickTime();
        String colour;
        if (mspt <= 50.0) colour = "&a";      // green: normal
        else if (mspt <= 75.0) colour = "&e"; // yellow: high
        else colour = "&c";                    // red: very high
        return colour + String.format("%.1f", mspt);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Colour + formatting parser  (&-codes, #hex, §-codes, MiniMessage tags)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Parses {@code &}-style colour codes, {@code §}-style codes,
     * {@code #RRGGBB} hex colours, and MiniMessage-style tags into
     * styled {@link Text}.
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
    static MutableText parseLegacyAndHex(String input) {
        if (input == null || input.isEmpty()) return Text.empty().copy();

        // Pre-process: handle MiniMessage tags that wrap content
        // We process from the inside out to handle nesting

        return parseMiniAndLegacy(input);
    }

    /**
     * Full parser that handles MiniMessage tags + legacy codes.
     */
    private static MutableText parseMiniAndLegacy(String input) {
        MutableText result = Text.empty();

        int i = 0;
        int len = input.length();
        StringBuilder buf = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        while (i < len) {
            char c = input.charAt(i);

            // ── MiniMessage tags: <tag> ────────────────────────────
            if (c == '<') {
                int closeAngle = input.indexOf('>', i);
                if (closeAngle > i) {
                    String tagContent = input.substring(i + 1, closeAngle);

                    // ── <gradient:#RRGGBB:#RRGGBB>text</gradient> ──
                    if (tagContent.toLowerCase().startsWith("gradient:")) {
                        // Flush buffer
                        if (buf.length() > 0) {
                            result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        String gradientSpec = tagContent.substring("gradient:".length());
                        int endTag = findClosingTag(input, closeAngle + 1, "gradient");
                        if (endTag != -1) {
                            String innerText = input.substring(closeAngle + 1, endTag);
                            // Parse inner text for legacy codes first, then apply gradient
                            String plainInner = stripCodes(innerText);
                            result.append(applyGradientToText(plainInner, gradientSpec, currentStyle));
                            i = endTag + "</gradient>".length();
                            continue;
                        }
                        // No closing tag — treat as literal
                    }

                    // ── <shadow> / <shadow:#RRGGBB> — persistent or wrapping ──────
                    // Persistent: <shadow>text  (shadow stays until <reset> or </shadow>)
                    // Wrapping:   <shadow>text</shadow>
                    if (tagContent.toLowerCase().startsWith("shadow")) {
                        if (buf.length() > 0) {
                            result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        String shadowHexStr = null;
                        if (tagContent.contains(":")) {
                            shadowHexStr = tagContent.substring(tagContent.indexOf(':') + 1).trim();
                        }
                        int shadowArgb;
                        if (shadowHexStr != null && !shadowHexStr.isBlank()) {
                            TextColor tc = parseHex(shadowHexStr.startsWith("#") ? shadowHexStr : "#" + shadowHexStr);
                            shadowArgb = tc != null ? (0xFF000000 | tc.getRgb()) : 0xFF3F3F3F;
                        } else {
                            shadowArgb = 0xFF3F3F3F; // standard MC dark shadow
                        }
                        int endShadow = findClosingTag(input, closeAngle + 1, "shadow");
                        if (endShadow != -1) {
                            // Wrapping mode
                            String innerText = input.substring(closeAngle + 1, endShadow);
                            result.append(parseLegacyPortion(innerText, currentStyle.withShadowColor(shadowArgb)));
                            i = endShadow + "</shadow>".length();
                        } else {
                            // Persistent mode — shadow stays active going forward
                            currentStyle = currentStyle.withShadowColor(shadowArgb);
                            i = closeAngle + 1;
                        }
                        continue;
                    }

                    // ── <bold>, <italic>, etc. modifier tags ───────
                    String lowerTag = tagContent.toLowerCase();
                    Style newStyle = tryParseFormattingTag(lowerTag, currentStyle);
                    if (newStyle != null) {
                        if (buf.length() > 0) {
                            result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        currentStyle = newStyle;
                        i = closeAngle + 1;
                        continue;
                    }

                    // ── Closing tags: </bold>, </italic>, </shadow>, etc. ─────
                    if (lowerTag.startsWith("/")) {
                        String closingName = lowerTag.substring(1);
                        // </shadow> — remove persistent shadow color
                        if ("shadow".equals(closingName)) {
                            if (buf.length() > 0) {
                                result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withShadowColor((Integer) null);
                            i = closeAngle + 1;
                            continue;
                        }
                        Style resetStyle = tryRemoveFormattingTag(closingName, currentStyle);
                        if (resetStyle != null) {
                            if (buf.length() > 0) {
                                result.append(Text.literal(buf.toString()).setStyle(currentStyle));
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
                                result.append(Text.literal(buf.toString()).setStyle(currentStyle));
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
                                result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withColor(tc);
                            i = closeAngle + 1;
                            continue;
                        }
                    }

                    // ── Named Minecraft colour (<dark_green>, <red>, <gold>, etc.) ──
                    try {
                        Formatting namedFmt = Formatting.valueOf(lowerTag.toUpperCase());
                        if (!namedFmt.isModifier() && namedFmt.getColorValue() != null) {
                            if (buf.length() > 0) {
                                result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withColor(TextColor.fromFormatting(namedFmt));
                            i = closeAngle + 1;
                            continue;
                        }
                    } catch (IllegalArgumentException ignored) {}

                    // ── <reset> ────────────────────────────────────
                    if ("reset".equals(lowerTag)) {
                        if (buf.length() > 0) {
                            result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        currentStyle = Style.EMPTY;
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
                        result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                        buf.setLength(0);
                    }
                    currentStyle = Style.EMPTY.withColor(tc);
                    i += 7;
                    continue;
                }
            }

            // ── &X or §X colour/format codes ──────────────────────
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = input.charAt(i + 1);
                Formatting fmt = Formatting.byCode(code);
                if (fmt != null) {
                    if (buf.length() > 0) {
                        result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                        buf.setLength(0);
                    }
                    if (fmt == Formatting.RESET) {
                        currentStyle = Style.EMPTY;
                    } else if (fmt.isColor()) {
                        currentStyle = Style.EMPTY.withColor(fmt);
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
            result.append(Text.literal(buf.toString()).setStyle(currentStyle));
        }

        return result;
    }

    /**
     * Parses a portion of text with legacy codes, starting from a given style.
     * Used after a MiniMessage tag has set up a base style (e.g. shadow).
     */
    private static MutableText parseLegacyPortion(String input, Style baseStyle) {
        MutableText result = Text.empty();
        StringBuilder buf = new StringBuilder();
        Style currentStyle = baseStyle;

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);

            // Nested MiniMessage tags
            if (c == '<') {
                int closeAngle = input.indexOf('>', i);
                if (closeAngle > i) {
                    String tagContent = input.substring(i + 1, closeAngle).toLowerCase();

                    // <gradient:...>text</gradient> inside shadow
                    if (tagContent.startsWith("gradient:")) {
                        if (buf.length() > 0) {
                            result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                            buf.setLength(0);
                        }
                        String gradientSpec = tagContent.substring("gradient:".length());
                        int endTag = findClosingTag(input, closeAngle + 1, "gradient");
                        if (endTag != -1) {
                            String innerText = stripCodes(input.substring(closeAngle + 1, endTag));
                            result.append(applyGradientToText(innerText, gradientSpec, currentStyle));
                            i = endTag + "</gradient>".length();
                            continue;
                        }
                    }

                    // <#RRGGBB>
                    if (tagContent.startsWith("#") && tagContent.length() == 7) {
                        TextColor tc = parseHex(tagContent);
                        if (tc != null) {
                            if (buf.length() > 0) {
                                result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withColor(tc);
                            i = closeAngle + 1;
                            continue;
                        }
                    }

                    // Named Minecraft colour in parseLegacyPortion (<dark_green>, <red>, etc.)
                    try {
                        Formatting namedFmtP = Formatting.valueOf(tagContent.toUpperCase());
                        if (!namedFmtP.isModifier() && namedFmtP.getColorValue() != null) {
                            if (buf.length() > 0) {
                                result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                                buf.setLength(0);
                            }
                            currentStyle = currentStyle.withColor(TextColor.fromFormatting(namedFmtP));
                            i = closeAngle + 1;
                            continue;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // #RRGGBB
            if (c == '#' && i + 6 < input.length()) {
                String hex = input.substring(i, i + 7);
                TextColor tc = parseHex(hex);
                if (tc != null) {
                    if (buf.length() > 0) {
                        result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                        buf.setLength(0);
                    }
                    currentStyle = currentStyle.withColor(tc);
                    i += 7;
                    continue;
                }
            }

            // &X or §X
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char code = input.charAt(i + 1);
                Formatting fmt = Formatting.byCode(code);
                if (fmt != null) {
                    if (buf.length() > 0) {
                        result.append(Text.literal(buf.toString()).setStyle(currentStyle));
                        buf.setLength(0);
                    }
                    if (fmt == Formatting.RESET) {
                        currentStyle = baseStyle; // reset to base, not EMPTY (keep shadow etc.)
                    } else if (fmt.isColor()) {
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
            result.append(Text.literal(buf.toString()).setStyle(currentStyle));
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

    /**
     * Applies a per-character gradient to plain text.
     * The gradient spec is like {@code #ff0000:#00ff00} (colon-separated hex stops).
     * Extra style (e.g. shadow) from {@code baseStyle} is preserved on each character.
     */
    private static MutableText applyGradientToText(String text, String gradientSpec, Style baseStyle) {
        String[] parts = gradientSpec.split(":");
        // Collect colour stops
        java.util.List<Integer> stops = new java.util.ArrayList<>();
        for (String p : parts) {
            String hex = p.trim();
            if (!hex.startsWith("#")) hex = "#" + hex;
            TextColor tc = parseHex(hex);
            if (tc != null) stops.add(tc.getRgb());
        }
        if (stops.size() < 2) {
            // Fallback: single colour or invalid
            return Text.literal(text).setStyle(
                    stops.isEmpty() ? baseStyle
                            : baseStyle.withColor(TextColor.fromRgb(stops.getFirst())));
        }

        int[] colors = stops.stream().mapToInt(Integer::intValue).toArray();
        int textLen = text.length();
        if (textLen == 0) return Text.empty().copy();
        if (textLen == 1) {
            return Text.literal(text).setStyle(
                    baseStyle.withColor(TextColor.fromRgb(colors[0])));
        }

        MutableText result = Text.empty();
        for (int ci = 0; ci < textLen; ci++) {
            float progress = (float) ci / (textLen - 1);
            int rgb = interpolateMulti(colors, progress);
            Style charStyle = baseStyle.withColor(TextColor.fromRgb(rgb));
            result.append(Text.literal(String.valueOf(text.charAt(ci))).setStyle(charStyle));
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
     * Strips &-codes, §-codes, and #RRGGBB from text, returning plain characters.
     */
    private static String stripCodes(String input) {
        // Strip MiniMessage-style tags (<dark_green>, <gradient:…>, </bold>, etc.)
        // so they don't appear as literal characters inside gradient text.
        String work = input.replaceAll("</?[a-zA-Z0-9#_:]+>", "");
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < work.length()) {
            char c = work.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < work.length()) {
                Formatting fmt = Formatting.byCode(work.charAt(i + 1));
                if (fmt != null) { i += 2; continue; }
            }
            if (c == '#' && i + 6 < work.length()) {
                TextColor tc = parseHex(work.substring(i, i + 7));
                if (tc != null) { i += 7; continue; }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Returns a modified Style if the tag name is a recognised formatting
     * toggle, or {@code null} if unrecognised.
     */
    private static Style tryParseFormattingTag(String tag, Style current) {
        return switch (tag) {
            case "bold", "b"              -> current.withBold(true);
            case "italic", "i", "em"      -> current.withItalic(true);
            case "underlined", "u"        -> current.withUnderline(true);
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
            case "underlined", "u"        -> current.withUnderline(false);
            case "strikethrough", "st"    -> current.withStrikethrough(false);
            case "obfuscated", "obf"      -> current.withObfuscated(false);
            case "reset"                  -> Style.EMPTY;
            default                       -> null;
        };
    }

    private static Style applyModifier(Style style, Formatting fmt) {
        return switch (fmt) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderline(true);
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
