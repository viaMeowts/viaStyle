package com.viameowts.viastyle;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Abstraction over TextPlaceholderAPI so that the dependency is truly optional.
 * The concrete implementation ({@link PapiPlaceholderProvider}) is only loaded
 * by the JVM when {@code placeholder-api} is actually present at runtime.
 */
public interface PlaceholderProvider {
    /**
     * Replaces any {@code %namespace:key%} patterns found in {@code text}
     * with the values produced by TextPlaceholderAPI for the given player.
     */
    Text parse(Text text, ServerPlayerEntity player);

    /**
     * Full pipeline: parses Patbox Simplified Text Format tags
     * ({@code <dark_green>}, {@code <gradient:...>}, {@code <bold>}, {@code &}-codes, etc.)
     * and then resolves {@code %namespace:key%} PAPI placeholders.
     *
     * @param input  raw format string
     * @param player player context for placeholder resolution (may be {@code null}
     *               — format tags are still applied, only placeholders are skipped)
     */
    Text parseFormat(String input, ServerPlayerEntity player);
}
