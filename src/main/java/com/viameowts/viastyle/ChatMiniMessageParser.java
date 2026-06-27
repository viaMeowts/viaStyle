package com.viameowts.viastyle;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatMiniMessageParser {

        private static final Pattern TAG_PATTERN = Pattern.compile(
            "<([/!]?)((?:#[0-9a-fA-F]{6})|gradient:[^>]+|bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|o|reset|r|" +
                    "black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Formatting> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", Formatting.BLACK),
            Map.entry("dark_blue", Formatting.DARK_BLUE),
            Map.entry("dark_green", Formatting.DARK_GREEN),
            Map.entry("dark_aqua", Formatting.DARK_AQUA),
            Map.entry("dark_red", Formatting.DARK_RED),
            Map.entry("dark_purple", Formatting.DARK_PURPLE),
            Map.entry("gold", Formatting.GOLD),
            Map.entry("gray", Formatting.GRAY),
            Map.entry("dark_gray", Formatting.DARK_GRAY),
            Map.entry("blue", Formatting.BLUE),
            Map.entry("green", Formatting.GREEN),
            Map.entry("aqua", Formatting.AQUA),
            Map.entry("red", Formatting.RED),
            Map.entry("light_purple", Formatting.LIGHT_PURPLE),
            Map.entry("yellow", Formatting.YELLOW),
            Map.entry("white", Formatting.WHITE)
    );

    private ChatMiniMessageParser() {}

    public static Text parse(String input, TextColor defaultColor) {
        if (input == null || input.isEmpty()) return Text.empty();

        MutableText result = Text.empty();
        TextColor currentColor = defaultColor;
        boolean bold = false, italic = false, underlined = false, strikethrough = false, obfuscated = false;

        Matcher matcher = TAG_PATTERN.matcher(input);
        int lastEnd = 0;
        int searchFrom = 0;

        while (matcher.find(searchFrom)) {
            if (matcher.start() > lastEnd) {
                String text = input.substring(lastEnd, matcher.start());
                result.append(styledLiteral(text, currentColor, bold, italic, underlined, strikethrough, obfuscated));
            }

            boolean isClose = matcher.group(1).equals("/");
            boolean isDisable = matcher.group(1).equals("!");
            String tagName = matcher.group(2).toLowerCase();

            if (isClose || isDisable) {
                switch (tagName) {
                    case "bold", "b" -> bold = false;
                    case "italic", "i" -> italic = false;
                    case "underlined", "u" -> underlined = false;
                    case "strikethrough", "st" -> strikethrough = false;
                    case "obfuscated", "o" -> obfuscated = false;
                    case "gradient" -> { }
                    default -> {
                        if (tagName.startsWith("#") || NAMED_COLORS.containsKey(tagName)) {
                            currentColor = defaultColor;
                        }
                    }
                }
                lastEnd = matcher.end();
                searchFrom = matcher.end();
            } else if (tagName.equals("reset") || tagName.equals("r")) {
                currentColor = defaultColor;
                bold = italic = underlined = strikethrough = obfuscated = false;
                lastEnd = matcher.end();
                searchFrom = matcher.end();
            } else if (tagName.startsWith("gradient:")) {
                String gradientSpec = tagName.substring("gradient:".length());
                int gradientEnd = input.indexOf("</gradient>", matcher.end());
                if (gradientEnd >= 0) {
                    String gradientText = input.substring(matcher.end(), gradientEnd);
                    result.append(applyGradient(gradientText, gradientSpec, bold, italic, underlined, strikethrough, obfuscated));
                    lastEnd = gradientEnd + "</gradient>".length();
                    searchFrom = lastEnd;
                } else {
                    String gradientText = input.substring(matcher.end());
                    result.append(applyGradient(gradientText, gradientSpec, bold, italic, underlined, strikethrough, obfuscated));
                    lastEnd = input.length();
                    searchFrom = lastEnd;
                }
                continue;
            } else if (tagName.startsWith("#")) {
                TextColor hex = parseHex(tagName);
                currentColor = hex != null ? hex : defaultColor;
                lastEnd = matcher.end();
                searchFrom = matcher.end();
            } else {
                switch (tagName) {
                    case "bold", "b" -> bold = true;
                    case "italic", "i" -> italic = true;
                    case "underlined", "u" -> underlined = true;
                    case "strikethrough", "st" -> strikethrough = true;
                    case "obfuscated", "o" -> obfuscated = true;
                    default -> {
                        Formatting named = NAMED_COLORS.get(tagName);
                        if (named != null && named.getColorValue() != null) {
                            currentColor = TextColor.fromFormatting(named);
                        }
                    }
                }
                lastEnd = matcher.end();
                searchFrom = matcher.end();
            }
        }

        if (lastEnd < input.length()) {
            String tail = input.substring(lastEnd);
            result.append(styledLiteral(tail, currentColor, bold, italic, underlined, strikethrough, obfuscated));
        }

        return result;
    }

    public static boolean containsTags(String input) {
        if (input == null) return false;
        return TAG_PATTERN.matcher(input).find();
    }

    private static MutableText styledLiteral(String text, TextColor color,
                                             boolean bold, boolean italic, boolean underlined,
                                             boolean strikethrough, boolean obfuscated) {
        return Text.literal(text).styled(s -> {
            s = s.withColor(color);
            if (bold) s = s.withBold(true);
            if (italic) s = s.withItalic(true);
            if (underlined) s = s.withUnderline(true);
            if (strikethrough) s = s.withStrikethrough(true);
            if (obfuscated) s = s.withObfuscated(true);
            return s;
        });
    }

    private static MutableText applyGradient(String text, String gradientSpec,
                                             boolean bold, boolean italic, boolean underlined,
                                             boolean strikethrough, boolean obfuscated) {
        String[] parts = gradientSpec.split(":");
        if (parts.length < 2) {
            return Text.literal(text);
        }

        int[] colors = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String piece = parts[i].trim();
            if (!piece.startsWith("#")) piece = "#" + piece;
            TextColor tc = parseHex(piece);
            if (tc == null) return Text.literal(text);
            colors[i] = tc.getRgb();
        }

        int len = text.length();
        if (len == 0) return Text.empty();

        MutableText out = Text.empty();
        for (int i = 0; i < len; i++) {
            float progress = len == 1 ? 0f : (float) i / (len - 1);
            int rgb = interpolateMulti(colors, progress);
            final int c = rgb;
            out.append(Text.literal(String.valueOf(text.charAt(i))).styled(s -> {
                s = s.withColor(TextColor.fromRgb(c));
                if (bold) s = s.withBold(true);
                if (italic) s = s.withItalic(true);
                if (underlined) s = s.withUnderline(true);
                if (strikethrough) s = s.withStrikethrough(true);
                if (obfuscated) s = s.withObfuscated(true);
                return s;
            }));
        }
        return out;
    }

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
        return (Math.round(r1 + (r2 - r1) * t) << 16)
                | (Math.round(g1 + (g2 - g1) * t) << 8)
                | Math.round(b1 + (b2 - b1) * t);
    }

    private static TextColor parseHex(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) return null;
        try {
            return TextColor.fromRgb(Integer.parseInt(hex.substring(1), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
