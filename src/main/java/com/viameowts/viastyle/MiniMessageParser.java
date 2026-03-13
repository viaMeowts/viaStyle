package com.viameowts.viastyle;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Lightweight MiniMessage-like parser for nick coloring.
 *
 * <p>Supported color specifications (as used in LuckPerms permissions or
 * the nick-color file):</p>
 * <ul>
 *   <li>{@code #RRGGBB} — solid hex colour</li>
 *   <li>{@code gradient:#RRGGBB:#RRGGBB} — two-stop gradient</li>
 *   <li>{@code gradient:#RRGGBB:#RRGGBB:#RRGGBB} — three-stop gradient</li>
 *   <li>Named Minecraft colour (e.g. {@code red}, {@code gold}, {@code dark_purple})</li>
 * </ul>
 */
public final class MiniMessageParser {

    private MiniMessageParser() {}

    /**
     * Colours a player name according to a colour specification string.
     *
     * @param name      the raw player name
     * @param colorSpec the spec (hex, gradient, or named colour)
     * @return styled {@link Text}, or {@code null} if the spec is invalid
     */
    public static MutableText colorize(String name, String colorSpec) {
        if (name == null || name.isEmpty() || colorSpec == null || colorSpec.isEmpty()) {
            return null;
        }

        String spec = colorSpec.trim().toLowerCase();

        // ── Gradient ───────────────────────────────────────────────────────
        if (spec.startsWith("gradient:")) {
            return parseGradient(name, spec.substring("gradient:".length()));
        }

        // ── Solid hex colour ───────────────────────────────────────────────
        if (spec.startsWith("#") && spec.length() == 7) {
            TextColor color = parseHex(spec);
            if (color != null) {
                return Text.literal(name).styled(s -> s.withColor(color));
            }
            return null;
        }

        // ── Named Minecraft colour ─────────────────────────────────────────
        try {
            Formatting fmt = Formatting.valueOf(spec.toUpperCase());
            if (!fmt.isModifier() && fmt.getColorValue() != null) {
                TextColor tc = TextColor.fromFormatting(fmt);
                return Text.literal(name).styled(s -> s.withColor(tc));
            }
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    /**
     * Returns the "primary" (first) colour from a spec as a single
     * {@link TextColor}.  Useful for contexts that only support a single
     * solid colour (e.g. scoreboard teams / above-head nametags).
     */
    public static TextColor primaryColor(String colorSpec) {
        if (colorSpec == null || colorSpec.isEmpty()) return null;
        String spec = colorSpec.trim().toLowerCase();

        if (spec.startsWith("gradient:")) {
            String stops = spec.substring("gradient:".length());
            String first = stops.split(":")[0].trim();
            return parseHex(first.startsWith("#") ? first : "#" + first);
        }
        if (spec.startsWith("#") && spec.length() == 7) {
            return parseHex(spec);
        }
        try {
            Formatting fmt = Formatting.valueOf(spec.toUpperCase());
            if (!fmt.isModifier() && fmt.getColorValue() != null) {
                return TextColor.fromFormatting(fmt);
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    /**
     * Returns the average colour across all gradient stops, or the
     * solid colour if the spec is not a gradient.
     *
     * <p>Used by the "team" nametag mode with the {@code "average"}
     * colour strategy to approximate a gradient as a single colour.</p>
     */
    public static TextColor averageColor(String colorSpec) {
        if (colorSpec == null || colorSpec.isEmpty()) return null;
        String spec = colorSpec.trim().toLowerCase();

        if (spec.startsWith("gradient:")) {
            String stopsStr = spec.substring("gradient:".length());
            String[] parts = stopsStr.split(":");
            if (parts.length < 2) return primaryColor(colorSpec);

            int totalR = 0, totalG = 0, totalB = 0, count = 0;
            for (String part : parts) {
                String p = part.trim();
                if (!p.startsWith("#")) p = "#" + p;
                TextColor tc = parseHex(p);
                if (tc == null) continue;
                totalR += (tc.getRgb() >> 16) & 0xFF;
                totalG += (tc.getRgb() >> 8) & 0xFF;
                totalB += tc.getRgb() & 0xFF;
                count++;
            }
            if (count == 0) return null;
            return TextColor.fromRgb(
                    ((totalR / count) << 16) | ((totalG / count) << 8) | (totalB / count));
        }

        // For non-gradient specs, fall back to primaryColor
        return primaryColor(colorSpec);
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    /**
     * Parses a gradient spec like {@code #ff0000:#00ff00} or
     * {@code #ff0000:#ffff00:#00ff00} into a per-character gradient Text.
     */
    private static MutableText parseGradient(String name, String stopsStr) {
        String[] parts = stopsStr.split(":");
        if (parts.length < 2) return null;

        int[] colors = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.startsWith("#")) p = "#" + p;
            TextColor tc = parseHex(p);
            if (tc == null) return null;
            colors[i] = tc.getRgb();
        }

        int len = name.length();
        if (len == 0) return Text.empty();
        if (len == 1) {
            return Text.literal(name).styled(s -> s.withColor(TextColor.fromRgb(colors[0])));
        }

        MutableText result = Text.empty();
        for (int i = 0; i < len; i++) {
            float progress = (float) i / (len - 1);
            int rgb = interpolateMulti(colors, progress);
            final int c = rgb;
            result.append(Text.literal(String.valueOf(name.charAt(i)))
                    .styled(s -> s.withColor(TextColor.fromRgb(c))));
        }
        return result;
    }

    /**
     * Interpolates across multiple colour stops. {@code progress} goes from
     * 0.0 (first stop) to 1.0 (last stop).
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
