package com.viameowts.viastyle;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
     * Registers {@code %viastyle:online%} — the number of non-vanished players visible
     * to the requesting player.  Vanished players (admins) see the true total count.
     *
     * <p>Must be called once, after PAPI is confirmed to be loaded.</p>
     */
    public static void registerCustomPlaceholders() {
        try {
            Class<?> identifierClass  = Class.forName("net.minecraft.util.Identifier");
            Class<?> handlerClass     = Class.forName("eu.pb4.placeholders.api.PlaceholderHandler");
            Class<?> resultClass      = Class.forName("eu.pb4.placeholders.api.PlaceholderResult");
            Class<?> ctxClass         = Class.forName("eu.pb4.placeholders.api.PlaceholderContext");
            Class<?> placeholdersClass = Class.forName("eu.pb4.placeholders.api.Placeholders");

            // net.minecraft.util.Identifier.of("viastyle:online")
            Object id;
            try {
                id = identifierClass.getMethod("of", String.class).invoke(null, "viastyle:online");
            } catch (NoSuchMethodException e) {
                // Older MC versions use Identifier(String) constructor
                id = identifierClass.getConstructor(String.class).newInstance("viastyle:online");
            }

            // Locate PlaceholderResult.value(Text)
            final Method valueMethod = resultClass.getMethod("value", Text.class);

            // Locate PlaceholderContext.getPlayer() and .getServer()
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

            // Build a Proxy for the PlaceholderHandler functional interface
            Object handler = Proxy.newProxyInstance(
                handlerClass.getClassLoader(),
                new Class<?>[]{ handlerClass },
                (proxy, method, methodArgs) -> {
                    if (method.getDeclaringClass() == Object.class) return null;
                    if (!"handle".equals(method.getName())) return null;
                    try {
                        Object ctx = methodArgs[0];  // PlaceholderContext

                        // Resolve player (may return ServerPlayerEntity or null)
                        ServerPlayerEntity player = null;
                        if (getPlayerMethod != null) {
                            Object raw = getPlayerMethod.invoke(ctx);
                            if (raw instanceof ServerPlayerEntity sp) player = sp;
                        }

                        // Resolve server
                        net.minecraft.server.MinecraftServer server = null;
                        if (getServerMethod != null) {
                            Object raw = getServerMethod.invoke(ctx);
                            if (raw instanceof net.minecraft.server.MinecraftServer ms) server = ms;
                        }
                        if (server == null) server = PlaceholderHelper.getServer();

                        int count = VanishHelper.countVisiblePlayers(server, player);
                        return valueMethod.invoke(null, Text.literal(String.valueOf(count)));
                    } catch (Throwable t) {
                        return valueMethod.invoke(null, Text.literal("?"));
                    }
                }
            );

            // Placeholders.register(id, handler)
            placeholdersClass.getMethod("register", identifierClass, handlerClass)
                             .invoke(null, id, handler);

            viaStyle.LOGGER.info("[viaStyle] Registered PAPI placeholder %viastyle:online%");
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] Failed to register %viastyle:online% placeholder: {}", t.getMessage());
        }
    }

    public PapiPlaceholderProvider() throws ReflectiveOperationException {
        Class<?> placeholdersClass = Class.forName("eu.pb4.placeholders.api.Placeholders");
        this.contextClass = Class.forName("eu.pb4.placeholders.api.PlaceholderContext");
        Class<?> minecraftServerClass = net.minecraft.server.MinecraftServer.class;
        this.ofMethod = contextClass.getMethod("of", ServerPlayerEntity.class);
        this.ofServerMethod = contextClass.getMethod("of", minecraftServerClass);
        this.parseTextMethod = placeholdersClass.getMethod("parseText", Text.class, contextClass);

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
    public Text parse(Text text, ServerPlayerEntity player) {
        try {
            Object ctx = ofMethod.invoke(null, player);
            return (Text) parseTextMethod.invoke(null, text, ctx);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Converts legacy {@code &X} and {@code §X} colour/format codes into
     * Patbox Simplified Text Format tags that {@code TextParserUtils} understands.
     * <p>Example: {@code "&aHello &lWorld"} → {@code "<green>Hello <bold>World"}</p>
     */
    static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return input;
        // Normalise § → &
        String s = input.replace('§', '&');
        StringBuilder out = new StringBuilder(s.length() + 32);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < len) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String tag = switch (code) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'k' -> "<obfuscated>";
                    case 'r' -> "<reset>";
                    default  -> null;
                };
                if (tag != null) {
                    out.append(tag);
                    i++; // skip the code char
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Full pipeline:
     * <ol>
     *   <li>Convert {@code &X}/{@code §X} legacy codes → Patbox MiniMessage tags</li>
     *   <li>Parse Patbox Simplified Text Format tags via {@code TextParserUtils.formatText}</li>
     *   <li>Resolve {@code %namespace:key%} PAPI placeholders via {@code Placeholders.parseText}</li>
     * </ol>
     * If {@code player} is {@code null}, only format tags are applied.
     */
    @Override
    public Text parseFormat(String input, ServerPlayerEntity player) {
        try {
            // Step 1: convert &-codes → <tags>
            String converted = legacyToMiniMessage(input);

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
                Text fallback = TabListManager.parseLegacyAndHex(input);
                if (ctx != null) {
                    return (Text) parseTextMethod.invoke(null, fallback, ctx);
                }
                return fallback;
            }

            if (!hasGradient) {
                // ── Pipeline A (no gradient) ─────────────────────────────────────
                Text formatted = (Text) formatTextMethod.invoke(null, converted);
                if (ctx != null) {
                    return (Text) parseTextMethod.invoke(null, formatted, ctx);
                }
                return formatted;
            } else {
                // ── Pipeline B (gradient present) ────────────────────────────────
                if (ctx != null) {
                    Text asLiteral = Text.literal(converted);
                    Text resolved = (Text) parseTextMethod.invoke(null, asLiteral, ctx);
                    // getString() collapses the text tree to a flat string;
                    // <tags> passed as literals survive intact.
                    converted = resolved.getString();
                }
                return (Text) formatTextMethod.invoke(null, converted);
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
}
