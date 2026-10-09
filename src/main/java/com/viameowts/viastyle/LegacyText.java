package com.viameowts.viastyle;

/**
 * Converts legacy colour codes into MiniMessage-style tags understood by both the built-in
 * parser and the placeholder-api text engine.
 *
 * <p>LuckPerms stores prefixes as plain strings and the usual convention is {@code &c[Admin]},
 * {@code &#ff5555[Admin]} or the long Bukkit form {@code &x&f&f&5&5&5&5}. Nothing else in the
 * mod understood {@code &}, so such prefixes showed up as literal text. {@code §} codes are
 * handled the same way.</p>
 *
 * <p>Like in the game itself, a colour code resets formatting, so every colour becomes
 * {@code <reset><colour>}.</p>
 */
public final class LegacyText {

    private LegacyText() {}

    private static final String[] COLOR_TAGS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    private static boolean isMarker(char c, boolean ampersand) {
        return c == '§' || (ampersand && c == '&');
    }

    private static int hex(char c) {
        return Character.digit(c, 16);
    }

    /** Converts {@code &x}, {@code §x}, {@code &#rrggbb} and {@code &x&r&r&g&g&b&b} into tags. */
    public static String toMiniTags(String input) {
        return convert(input, true);
    }

    /** Same as {@link #toMiniTags} but only for {@code §} codes (config strings may contain {@code &}). */
    public static String sectionsToMiniTags(String input) {
        return convert(input, false);
    }

    private static String convert(String input, boolean ampersand) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        int len = input.length();
        StringBuilder out = new StringBuilder(len + 32);
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (!isMarker(c, ampersand) || i + 1 >= len) {
                out.append(c);
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));

            // &#rrggbb
            if (code == '#' && c == '&' && i + 7 < len && isHex6(input, i + 2)) {
                out.append("<reset><#").append(input, i + 2, i + 8).append('>');
                i += 7;
                continue;
            }
            // &x&r&r&g&g&b&b  /  §x§r§r§g§g§b§b
            if (code == 'x' && i + 13 < len) {
                StringBuilder rgb = new StringBuilder(6);
                int p = i + 2;
                for (int k = 0; k < 6; k++, p += 2) {
                    if (p + 1 < len && isMarker(input.charAt(p), ampersand) && hex(input.charAt(p + 1)) >= 0) {
                        rgb.append(input.charAt(p + 1));
                    } else {
                        break;
                    }
                }
                if (rgb.length() == 6) {
                    out.append("<reset><#").append(rgb).append('>');
                    i = p - 1;
                    continue;
                }
            }

            String tag = tagFor(code);
            if (tag != null) {
                out.append(tag);
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isHex6(String s, int from) {
        if (from + 6 > s.length()) return false;
        for (int i = from; i < from + 6; i++) if (hex(s.charAt(i)) < 0) return false;
        return true;
    }

    private static String tagFor(char code) {
        int idx = Character.digit(code, 16);
        if (idx >= 0) return "<reset><" + COLOR_TAGS[idx] + ">";
        return switch (code) {
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }
}
