package com.viameowts.viastyle;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reflection-based implementation of {@link PlaceholderProvider} that delegates to
 * TextPlaceholderAPI (eu.pb4:placeholder-api) at runtime without a compile-time dependency.
 *
 * This class is only instantiated inside {@link PlaceholderHelper#init()} when the
 * PAPI mod is confirmed to be loaded, so reflection failures are handled gracefully.
 */
public class PapiPlaceholderProvider implements PlaceholderProvider {

    private final Method parseTextMethod;   // Placeholders.parseText(Text, PlaceholderContext)
    private final Method ofMethod;          // PlaceholderContext.of(ServerPlayerEntity)
    private final Method ofServerMethod;    // PlaceholderContext.of(MinecraftServer)
    private final Method formatTextMethod;  // TextParserUtils.formatText(String)
    private final Class<?> contextClass;    // PlaceholderContext.class

    // ── Custom PAPI placeholder registration ───────────────────────────────────

    /**
     * Registers the viaStyle placeholders: {@code %viastyle:online%} (visible players, vanish-aware)
     * and the LuckPerms ones {@code %viastyle:prefix%}, {@code %viastyle:suffix%},
     * {@code %viastyle:group%}, {@code %viastyle:group_name%}, {@code %viastyle:weight%}.
     *
     * <p>Must be called once, after PAPI is confirmed to be loaded.</p>
     */
    public static void registerCustomPlaceholders() {
        try {
            Class<?> handlerClass     = Class.forName("eu.pb4.placeholders.api.PlaceholderHandler");
            Class<?> resultClass      = Class.forName("eu.pb4.placeholders.api.PlaceholderResult");
            Class<?> ctxClass         = Class.forName("eu.pb4.placeholders.api.PlaceholderContext");
            Class<?> placeholdersClass = Class.forName("eu.pb4.placeholders.api.Placeholders");

            final Method valueMethod = resultClass.getMethod("value", Component.class);
            Method gpm = null;
            for (String name : new String[]{"getPlayer", "player"}) {
                try { gpm = ctxClass.getMethod(name); break; } catch (NoSuchMethodException ignored) {}
            }
            Method gsm = null;
            for (String name : new String[]{"getServer", "server"}) {
                try { gsm = ctxClass.getMethod(name); break; } catch (NoSuchMethodException ignored) {}
            }
            final Method getPlayerMethod = gpm;
            final Method getServerMethod = gsm;
            Method register = placeholdersClass.getMethod("register",
                    net.minecraft.resources.Identifier.class, handlerClass);

            java.util.Map<String, java.util.function.BiFunction<ServerPlayer, net.minecraft.server.MinecraftServer, Component>> all =
                    new java.util.LinkedHashMap<>();
            all.put("online", (player, server) ->
                    Component.literal(String.valueOf(VanishHelper.countVisiblePlayers(server, player))));
            all.put("prefix", (player, server) -> lpText(player, LuckPermsHelper.Info::prefix, true));
            all.put("suffix", (player, server) -> lpText(player, LuckPermsHelper.Info::suffix, true));
            all.put("group", (player, server) -> lpText(player, LuckPermsHelper.Info::group, false));
            all.put("group_name", (player, server) -> lpText(player, LuckPermsHelper.Info::groupName, false));
            all.put("weight", (player, server) -> lpText(player, i -> String.valueOf(i.weight()), false));

            for (var entry : all.entrySet()) {
                Object handler = Proxy.newProxyInstance(
                    handlerClass.getClassLoader(),
                    new Class<?>[]{ handlerClass },
                    (proxy, method, methodArgs) -> {
                        if (method.getDeclaringClass() == Object.class) return null;
                        if (!"handle".equals(method.getName())) return null;
                        try {
                            Object ctx = methodArgs[0];
                            ServerPlayer player = null;
                            if (getPlayerMethod != null && getPlayerMethod.invoke(ctx) instanceof ServerPlayer sp) player = sp;
                            net.minecraft.server.MinecraftServer server = null;
                            if (getServerMethod != null && getServerMethod.invoke(ctx) instanceof net.minecraft.server.MinecraftServer ms) server = ms;
                            if (server == null) server = PlaceholderHelper.getServer();
                            return valueMethod.invoke(null, entry.getValue().apply(player, server));
                        } catch (Throwable t) {
                            return valueMethod.invoke(null, Component.literal("?"));
                        }
                    });
                register.invoke(null, net.minecraft.resources.Identifier.fromNamespaceAndPath("viastyle", entry.getKey()), handler);
            }
            viaStyle.LOGGER.info("[viaStyle] Registered PAPI placeholders %viastyle:{}%", String.join("%, %viastyle:", all.keySet()));
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to register viastyle placeholders: {}", t.getMessage());
        }
    }

    private static Component lpText(ServerPlayer player,
                                    java.util.function.Function<LuckPermsHelper.Info, String> field,
                                    boolean formatted) {
        if (player == null) return Component.empty();
        String value = field.apply(LuckPermsHelper.info(player.getUUID()));
        return formatted ? TabListManager.parseLegacyAndHex(value) : Component.literal(value);
    }

    public PapiPlaceholderProvider() throws ReflectiveOperationException {
        Class<?> placeholdersClass = Class.forName("eu.pb4.placeholders.api.Placeholders");
        this.contextClass = Class.forName("eu.pb4.placeholders.api.PlaceholderContext");
        Class<?> minecraftServerClass = net.minecraft.server.MinecraftServer.class;
        this.ofMethod = contextClass.getMethod("of", ServerPlayer.class);
        this.ofServerMethod = contextClass.getMethod("of", minecraftServerClass);
        this.parseTextMethod = placeholdersClass.getMethod("parseText", Component.class, contextClass);

        // TextParserUtils — resolves all Simplified Text Format tags
        Method fmt = null;
        for (String clsName : new String[]{
                "eu.pb4.placeholders.api.TextParserUtils",
                "eu.pb4.placeholders.api.parsers.TextParserV1"
        }) {
            try {
                Class<?> cls = Class.forName(clsName);
                // Try the static formatText(String) method
                try { fmt = cls.getMethod("formatText", String.class); break; } catch (NoSuchMethodException ignored) {}
                // Some versions expose it as formatTextSafe
                try { fmt = cls.getMethod("formatTextSafe", String.class); break; } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        this.formatTextMethod = fmt; // may be null if PAPI build lacks it
    }

    /** Resolves {@code %namespace:key%} placeholders in an already-parsed Text. */
    @Override
    public Component parse(Component text, ServerPlayer player) {
        try {
            Object ctx = ofMethod.invoke(null, player);
            return (Component) parseTextMethod.invoke(null, text, ctx);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Full pipeline:
     * <ol>
    *   <li>Convert {@code §X} legacy codes → Patbox MiniMessage tags</li>
     *   <li>Parse Patbox Simplified Text Format tags via {@code TextParserUtils.formatText}</li>
     *   <li>Resolve {@code %namespace:key%} PAPI placeholders via {@code Placeholders.parseText}</li>
     * </ol>
     * If {@code player} is {@code null}, only format tags are applied.
     */
    @Override
    public Component parseFormat(String input, ServerPlayer player) {
        try {
            input = normalizePercentAliasSyntax(input);

            // Step 1: convert §-codes → <tags>
            String converted = LegacyText.sectionsToMiniTags(input);

            // Step 2: build placeholder context (player or server fallback)
            Object ctx = null;
            if (player != null) {
                ctx = ofMethod.invoke(null, player);
            } else {
                net.minecraft.server.MinecraftServer server = PlaceholderHelper.getServer();
                if (server != null) {
                    ctx = ofServerMethod.invoke(null, server);
                }
            }

            // Step 3: choose pipeline based on whether the string contains gradient blocks.
            //
            // PIPELINE A — no gradient (default):
            //   formatText(str) → Patbox parses <tags> → Text tree
            //   parseText(Text, ctx) → PAPI injects colored Text nodes into the tree
            //   Result: %server:mspt_colored% keeps its colors; <reset> before it works correctly.
            //
            // PIPELINE B — gradient present:
            //   parseText(Text.literal(str), ctx) → flatten %placeholders% to plain strings
            //   getString() → collapse to static string (gradient needs static char count)
            //   formatText(staticStr) → gradient renders correctly
            //   Downside: placeholder colors inside gradient blocks are lost — acceptable,
            //   since gradient overrides per-character color anyway.
            //
            boolean hasGradient = containsGradientTag(converted);

            if (formatTextMethod == null) {
                // PAPI build without TextParserUtils — legacy fallback
                Component fallback = TabListManager.parseLegacyAndHex(input);
                if (ctx != null) {
                    return (Component) parseTextMethod.invoke(null, fallback, ctx);
                }
                return fallback;
            }

            if (!hasGradient) {
                // ── Pipeline A (no gradient) ─────────────────────────────────────
                Component formatted = (Component) formatTextMethod.invoke(null, converted);
                if (ctx != null) {
                    return (Component) parseTextMethod.invoke(null, formatted, ctx);
                }
                return formatted;
            } else {
                // ── Pipeline B (gradient present) ────────────────────────────────
                if (ctx != null) {
                    Component asLiteral = Component.literal(converted);
                    Component resolved = (Component) parseTextMethod.invoke(null, asLiteral, ctx);
                    // getString() collapses the text tree to a flat string;
                    // <tags> passed as literals survive intact.
                    converted = resolved.getString();
                }
                return (Component) formatTextMethod.invoke(null, converted);
            }
        } catch (Exception e) {
            return TabListManager.parseLegacyAndHex(input);
        }
    }

    /**
     * Returns {@code true} when the string contains any Patbox gradient or rainbow tag
     * that requires a static character count to interpolate colors correctly.
     */
    private static boolean containsGradientTag(String s) {
        // Case-insensitive check without allocating a lower-case copy for the common case.
        int len = s.length();
        for (int i = 0; i < len - 3; i++) {
            if (s.charAt(i) != '<') continue;
            // Quick check on the next char
            char c = s.charAt(i + 1);
            if (c == 'g' || c == 'G') {
                // <gr:  <gradient:
                String sub = s.substring(i + 1, Math.min(i + 10, len)).toLowerCase();
                if (sub.startsWith("gr:") || sub.startsWith("gradient:")
                        || sub.startsWith("hgr:") || sub.startsWith("hard_grad")) return true;
            } else if (c == 'r' || c == 'R') {
                // <rb>  <rb:  <rainbow>  <rainbow:
                String sub = s.substring(i + 1, Math.min(i + 9, len)).toLowerCase();
                if (sub.startsWith("rb>") || sub.startsWith("rb:")
                        || sub.startsWith("rainbow>") || sub.startsWith("rainbow:")) return true;
            }
        }
        return false;
    }

    private static String normalizePercentAliasSyntax(String input) {
        if (input == null || input.isEmpty() || input.indexOf('%') < 0) return input;

        String normalized = input;
        normalized = normalized.replace("%viastyle_online%", "%viastyle:online%");
        normalized = normalized.replace("%server_online%", "%server:online%");
        normalized = normalized.replace("%server_players%", "%server:players%");
        normalized = normalized.replace("%server_max_players%", "%server:max_players%");

        StringBuilder out = new StringBuilder(normalized.length());
        int i = 0;
        while (i < normalized.length()) {
            int open = normalized.indexOf('%', i);
            if (open < 0) {
                out.append(normalized, i, normalized.length());
                break;
            }
            int close = normalized.indexOf('%', open + 1);
            if (close < 0) {
                out.append(normalized, i, normalized.length());
                break;
            }

            out.append(normalized, i, open + 1);
            String token = normalized.substring(open + 1, close);
            String converted = convertAliasToken(token);
            out.append(converted != null ? converted : token);
            out.append('%');
            i = close + 1;
        }
        return out.toString();
    }

    private static String convertAliasToken(String token) {
        if (token == null || token.isEmpty() || token.indexOf(':') >= 0) return token;
        int underscore = token.indexOf('_');
        if (underscore <= 0 || underscore >= token.length() - 1) return token;

        String namespace = token.substring(0, underscore);
        String key = token.substring(underscore + 1);
        if (namespace.isEmpty() || key.isEmpty()) return token;

        return namespace + ':' + key;
    }
}
