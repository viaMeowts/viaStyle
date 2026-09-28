package com.viameowts.viastyle.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.viameowts.viastyle.IgnoreManager;
import com.viameowts.viastyle.NickColorManager;
import com.viameowts.viastyle.SocialSpyManager;
import com.viameowts.viastyle.viaStyle;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player settings that follow a player across the network. The proxy stores them;
 * this backend applies them on join and pushes each change back.
 *
 * <p>Keys: {@code ignores}, {@code spy}, {@code pmSoundOff}, {@code channel}, {@code nickColor}.</p>
 */
public final class Profiles {
    public static final String IGNORES = "ignores";
    public static final String SPY = "spy";
    public static final String PM_SOUND_OFF = "pmSoundOff";
    public static final String CHANNEL = "channel";
    public static final String NICK_COLOR = "nickColor";

    /** Set while applying data from the proxy, so the managers don't push it straight back. */
    private static boolean applying;

    private Profiles() {}

    public static void apply(UUID uuid, JsonObject profile) {
        for (Map.Entry<String, JsonElement> e : profile.entrySet()) {
            applyKey(uuid, e.getKey(), e.getValue());
        }
    }

    public static void applyKey(UUID uuid, String key, JsonElement value) {
        applying = true;
        try {
            boolean isNull = value == null || value.isJsonNull();
            switch (key) {
                case IGNORES -> {
                    Set<UUID> set = new HashSet<>();
                    if (!isNull) {
                        for (JsonElement e : value.getAsJsonArray()) {
                            try { set.add(UUID.fromString(e.getAsString())); } catch (IllegalArgumentException ignored) {}
                        }
                    }
                    IgnoreManager.replace(uuid, set);
                }
                case SPY -> {
                    Set<SocialSpyManager.Channel> set = EnumSet.noneOf(SocialSpyManager.Channel.class);
                    if (!isNull) {
                        for (JsonElement e : value.getAsJsonArray()) {
                            SocialSpyManager.Channel c = SocialSpyManager.Channel.fromString(e.getAsString());
                            if (c != null) set.add(c);
                        }
                    }
                    SocialSpyManager.replace(uuid, set);
                }
                case PM_SOUND_OFF -> {
                    if (!isNull && value.getAsBoolean()) viaStyle.disablePmSound(uuid);
                    else viaStyle.enablePmSound(uuid);
                }
                case CHANNEL -> {
                    ChatChannel channel = isNull ? null : ChatChannel.parse(value.getAsString());
                    if (channel == null) viaStyle.playerChannel.remove(uuid);
                    else viaStyle.playerChannel.put(uuid, channel);
                }
                case NICK_COLOR -> {
                    if (isNull || value.getAsString().isBlank()) NickColorManager.removeOverride(uuid);
                    else NickColorManager.setOverride(uuid, value.getAsString());
                }
                default -> { }
            }
        } finally {
            applying = false;
        }
    }

    /** Called by the managers after a local change; sends the key's new value to the proxy. */
    public static void changed(UUID uuid, String key) {
        if (applying || !Network.active()) return;
        Network.sendProfileKey(uuid, key, current(uuid, key));
    }

    private static JsonElement current(UUID uuid, String key) {
        return switch (key) {
            case IGNORES -> Network.uuidArray(IgnoreManager.getIgnored(uuid));
            case SPY -> {
                JsonArray arr = new JsonArray();
                for (SocialSpyManager.Channel c : SocialSpyManager.getChannels(uuid)) arr.add(c.name());
                yield arr;
            }
            case PM_SOUND_OFF -> new JsonPrimitive(!viaStyle.isPmSoundEnabled(uuid));
            case CHANNEL -> {
                ChatChannel c = viaStyle.playerChannel.get(uuid);
                yield c == null ? JsonNull.INSTANCE : new JsonPrimitive(c.id);
            }
            case NICK_COLOR -> {
                String spec = NickColorManager.getOverride(uuid);
                yield spec == null ? JsonNull.INSTANCE : new JsonPrimitive(spec);
            }
            default -> JsonNull.INSTANCE;
        };
    }
}
