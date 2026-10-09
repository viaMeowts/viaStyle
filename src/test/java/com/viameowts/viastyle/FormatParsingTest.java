package com.viameowts.viastyle;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FormatParsingTest {

    private record Piece(String text, Style style) {}

    private static List<Piece> pieces(Component c) {
        List<Piece> out = new ArrayList<>();
        c.<Object>visit((style, text) -> {
            if (!text.isEmpty()) out.add(new Piece(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static String plain(String input) {
        return TabListManager.parseLegacyAndHex(input).getString();
    }

    @Test
    void unclosedGradientRunsToEndAndLeavesNoLiteralTag() {
        MutableComponent c = TabListManager.parseLegacyAndHex("<gr:#ff0000:#0000ff>    viaStyle    ");
        assertEquals("    viaStyle    ", c.getString());
        List<Piece> p = pieces(c);
        assertEquals(TextColor.fromRgb(0xff0000), p.getFirst().style().getColor());
        assertEquals(TextColor.fromRgb(0x0000ff), p.getLast().style().getColor());
    }

    @Test
    void closedGradientOnlyCoversItsBody() {
        MutableComponent c = TabListManager.parseLegacyAndHex("a<gradient:#ff0000:#00ff00>bc</gradient>d");
        assertEquals("abcd", c.getString());
        List<Piece> p = pieces(c);
        assertNull(p.getFirst().style().getColor());
        assertNull(p.getLast().style().getColor());
        assertEquals(TextColor.fromRgb(0xff0000), p.get(1).style().getColor());
        assertEquals(TextColor.fromRgb(0x00ff00), p.get(2).style().getColor());
    }

    @Test
    void gradientKeepsBoldFromInsideTags() {
        List<Piece> p = pieces(TabListManager.parseLegacyAndHex("<gradient:#ff0000:#00ff00><bold>ab</bold></gradient>"));
        assertEquals(2, p.size());
        assertTrue(p.getFirst().style().isBold());
    }

    @Test
    void defaultsHaveNoLiteralTags() {
        TabListConfig cfg = new TabListConfig();
        for (String line : cfg.header) assertFalse(plain(line).contains("<"), line);
        for (String line : cfg.footer) assertFalse(plain(line).contains("<"), line);
    }

    @Test
    void ampersandCodesBecomeTags() {
        assertEquals("<reset><red>[Admin] <reset><gray>", LegacyText.toMiniTags("&c[Admin] &7"));
        assertEquals("<reset><red>x", LegacyText.toMiniTags("§cx"));
        assertEquals("<reset><#ff5555>x", LegacyText.toMiniTags("&#ff5555x"));
        assertEquals("<reset><#ff5555>x", LegacyText.toMiniTags("&x&f&f&5&5&5&5x"));
        assertEquals("<reset><#ff5555>x", LegacyText.toMiniTags("§x§f§f§5§5§5§5x"));
        assertEquals("<bold>x", LegacyText.toMiniTags("&lx"));
        assertEquals("Tom & Jerry", LegacyText.toMiniTags("Tom & Jerry"));
        assertEquals("&c", LegacyText.sectionsToMiniTags("&c"));
    }

    @Test
    void luckPermsPrefixRendersColoured() {
        List<Piece> p = pieces(TabListManager.parseLegacyAndHex(LegacyText.toMiniTags("&c[Admin] &7")));
        assertEquals("[Admin] ", p.getFirst().text());
        assertEquals(TextColor.fromRgb(0xFF5555), p.getFirst().style().getColor());
    }
}
