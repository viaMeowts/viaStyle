package com.viameowts.viastyle.network;

import java.util.Locale;

/**
 * Chat channels.
 *
 * <ul>
 *   <li>{@link #LOCAL} – players within {@code local.radius} blocks.</li>
 *   <li>{@link #PLANET} – everyone on this server (the old "global" chat).</li>
 *   <li>{@link #NETWORK} – every server behind the proxy. Falls back to {@link #PLANET}
 *       when network mode is off.</li>
 *   <li>{@link #STAFF} – holders of {@code viastyle.staff}, network-wide in network mode.</li>
 * </ul>
 */
public enum ChatChannel {
    LOCAL("local"),
    PLANET("planet"),
    NETWORK("network"),
    STAFF("staff");

    public final String id;

    ChatChannel(String id) {
        this.id = id;
    }

    /** Parses an id or a common alias (en/ru); returns {@code null} when unknown. */
    public static ChatChannel parse(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "local", "l", "локальный", "локал", "л" -> LOCAL;
            case "planet", "global", "server", "p", "g", "планета", "глобальный", "п" -> PLANET;
            case "network", "net", "n", "all", "сеть", "с" -> NETWORK;
            case "staff", "admin", "mod", "s", "штаб", "админ", "ш" -> STAFF;
            default -> null;
        };
    }
}
