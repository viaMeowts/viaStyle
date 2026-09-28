package com.viameowts.viastyle.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.viameowts.viapanel.api.ViaPanelApi;
import com.viameowts.viastyle.BlockBotHelper;
import com.viameowts.viastyle.ChatHandler;
import com.viameowts.viastyle.IgnoreManager;
import com.viameowts.viastyle.JoinLeaveManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.MentionHandler;
import com.viameowts.viastyle.SocialSpyManager;
import com.viameowts.viastyle.VanishHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.command.PrivateMsgCommand;
import com.viameowts.viastyle.viaStyle;
import com.viameowts.viastyle.viaStyleServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backend side of the viaStyle server network.
 *
 * <p>Every backend formats its own messages (LuckPerms prefixes, nick colours, placeholders)
 * and hands the finished component to the viaStyle Velocity plugin over the {@code viastyle:net}
 * plugin channel. The proxy routes it to the other backends, which deliver it to their players
 * after local checks (ignore lists, staff permission). The proxy also keeps the network player
 * list, per-player profiles (ignores, spy, PM sound, chat channel, nick colour) and decides
 * whether a connection is a network join, a server switch or a quit.</p>
 *
 * <p>Wire format: one UTF-8 JSON object per message, {@code "t"} is the message type.
 * Plugin messages need a player connection, so a backend with nobody online can neither send
 * nor receive; that is fine, since there is nobody to deliver to either.</p>
 */
public final class Network {

    /** A player somewhere on the network, as reported by the proxy. */
    public record NetPlayer(UUID uuid, String name, String server, String display, boolean vanished) {}

    private record PendingPm(UUID sender, UUID target, String targetName, Component echo,
                             String plain, long sentAt) {}

    private static final long PM_TIMEOUT_MS = 5000;
    private static final int HELLO_RETRY_TICKS = 60;
    private static final int HELLO_MAX_TRIES = 3;

    private static final Map<UUID, NetPlayer> PLAYERS = new ConcurrentHashMap<>();
    private static final Map<String, PendingPm> PENDING_PMS = new ConcurrentHashMap<>();
    /** Local players still waiting for a welcome: uuid -> [tries, ticksLeft]. */
    private static final Map<UUID, int[]> AWAITING_WELCOME = new ConcurrentHashMap<>();
    private static final Set<UUID> WELCOMED = ConcurrentHashMap.newKeySet();

    private static volatile MinecraftServer server;
    /** True once the proxy has answered at least once. */
    private static volatile boolean proxySeen;
    private static volatile boolean warnedNoProxy;
    /** Name of this backend in velocity.toml, as reported by the proxy. */
    private static volatile String proxyServerName;

    private Network() {}

    // ═══════════════════════════════════════════════════════════════════════
    //  Setup
    // ═══════════════════════════════════════════════════════════════════════

    public static boolean enabled() {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        return cfg != null && cfg.networkEnabled;
    }

    /** True when network mode is on and the proxy plugin has answered. */
    public static boolean active() {
        return enabled() && proxySeen;
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(NetPayload.TYPE, NetPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NetPayload.TYPE, NetPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NetPayload.TYPE, (payload, ctx) -> {
            if (!enabled()) return;
            try {
                JsonObject msg = JsonParser.parseString(new String(payload.data(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                handle(ctx.server(), msg);
            } catch (RuntimeException e) {
                viaStyle.LOGGER.warn("[viaStyle] Bad network message: {}", e.getMessage());
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerTickEvents.END_SERVER_TICK.register(Network::tick);
    }

    /** Display name of this server for chat ({server} token). */
    public static String serverDisplayName() {
        return ViaPanelApi.getServerDisplayName();
    }

    /** Id of this server: the proxy's name for it once known, else viaPanel's server_id. */
    public static String serverId() {
        String name = proxyServerName;
        return name != null ? name : ViaPanelApi.getServerId();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Player list
    // ═══════════════════════════════════════════════════════════════════════

    /** Network players other than those on this server (empty when network mode is off). */
    public static Collection<NetPlayer> remotePlayers() {
        if (!active()) return List.of();
        String self = serverId();
        List<NetPlayer> out = new ArrayList<>();
        for (NetPlayer p : PLAYERS.values()) {
            if (!p.server().equalsIgnoreCase(self)) out.add(p);
        }
        return out;
    }

    public static NetPlayer findRemote(String name) {
        if (name == null) return null;
        for (NetPlayer p : remotePlayers()) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    public static NetPlayer findRemote(UUID uuid) {
        if (uuid == null || !active()) return null;
        NetPlayer p = PLAYERS.get(uuid);
        return p != null && !p.server().equalsIgnoreCase(serverId()) ? p : null;
    }

    public static Collection<NetPlayer> allPlayers() {
        return PLAYERS.values();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Join / leave
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Called when a player joins this backend. Returns {@code true} when the network takes over
     * the join announcement (the local one must be skipped).
     */
    public static boolean onJoin(ServerPlayer player) {
        if (!enabled()) return false;
        WELCOMED.remove(player.getUUID());
        AWAITING_WELCOME.put(player.getUUID(), new int[]{1, HELLO_RETRY_TICKS});
        sendHello(player);
        return true;
    }

    /** Returns {@code true} when the proxy announces the leave (the local one must be skipped). */
    public static boolean onLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        AWAITING_WELCOME.remove(uuid);
        boolean welcomed = WELCOMED.remove(uuid);
        return enabled() && welcomed;
    }

    private static void sendHello(ServerPlayer player) {
        JsonObject msg = message("hello");
        msg.addProperty("uuid", player.getUUID().toString());
        msg.addProperty("name", player.getName().getString());
        msg.addProperty("server", ViaPanelApi.getServerId());
        msg.addProperty("display", serverDisplayName());
        msg.addProperty("vanished", VanishHelper.isVanished(player));
        send(player, msg);
    }

    private static void onWelcome(MinecraftServer srv, JsonObject msg) {
        UUID uuid = uuid(msg, "uuid");
        if (uuid == null) return;
        if (msg.has("server")) proxyServerName = msg.get("server").getAsString();
        ServerPlayer player = srv.getPlayerList().getPlayer(uuid);
        if (player == null) return;
        AWAITING_WELCOME.remove(uuid);
        WELCOMED.add(uuid);

        if (msg.has("profile") && msg.get("profile").isJsonObject()) {
            Profiles.apply(uuid, msg.getAsJsonObject("profile"));
        }

        ViaStyleConfig cfg = viaStyle.CONFIG;
        boolean vanished = VanishHelper.isVanished(player);

        // Pre-rendered leave message: the proxy shows it when the player quits the network.
        JsonObject presence = message("presence");
        presence.addProperty("uuid", uuid.toString());
        presence.addProperty("vanished", vanished);
        String leaveFmt = JoinLeaveManager.resolveLeaveFormat(uuid, cfg.leaveFormat);
        presence.add("leave", toJson(srv, viaStyleServer.renderJoinLeave(leaveFmt, player, false)));
        send(player, presence);

        if (vanished) return;
        String kind = str(msg, "kind", "join");
        Component announce;
        if ("switch".equals(kind)) {
            if (!cfg.networkAnnounceSwitch) return;
            String fmt = cfg.networkSwitchFormat
                    .replace("{from}", str(msg, "fromDisplay", str(msg, "from", "?")))
                    .replace("{to}", serverDisplayName());
            announce = viaStyleServer.renderJoinLeave(fmt, player, true);
        } else {
            boolean first = msg.has("first") && msg.get("first").getAsBoolean();
            String fmt = first
                    ? JoinLeaveManager.resolveFirstJoinFormat(uuid, cfg.firstJoinFormat)
                    : JoinLeaveManager.resolveJoinFormat(uuid, cfg.joinFormat);
            announce = viaStyleServer.renderJoinLeave(fmt, player, true);
        }
        JsonObject out = message("announce");
        out.addProperty("uuid", uuid.toString());
        out.add("json", toJson(srv, announce));
        out.addProperty("plain", announce.getString());
        send(player, out);
    }

    private static void onAnnounce(MinecraftServer srv, JsonObject msg) {
        UUID actor = uuid(msg, "uuid");
        Component text = component(srv, msg);
        if (text == null || text.getString().isBlank()) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(actor)) continue;
            p.sendSystemMessage(text);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Chat
    // ═══════════════════════════════════════════════════════════════════════

    /** Sends a formatted network or staff message to the other servers. */
    public static void sendChat(ServerPlayer sender, ChatChannel channel, Component formatted, String plain) {
        if (!active()) return;
        JsonObject msg = message("chat");
        msg.addProperty("channel", channel.id);
        msg.addProperty("uuid", sender.getUUID().toString());
        msg.addProperty("name", sender.getName().getString());
        msg.addProperty("display", serverDisplayName());
        msg.add("json", toJson(sender.level().getServer(), formatted));
        msg.addProperty("plain", plain);
        send(sender, msg);
    }

    private static void onChat(MinecraftServer srv, JsonObject msg) {
        ChatChannel channel = ChatChannel.parse(str(msg, "channel", ""));
        if (channel != ChatChannel.NETWORK && channel != ChatChannel.STAFF) return;
        UUID from = uuid(msg, "uuid");
        String name = str(msg, "name", "?");
        String plain = str(msg, "plain", "");
        Component text = component(srv, msg);
        if (text == null) return;
        ViaStyleConfig cfg = viaStyle.CONFIG;

        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (channel == ChatChannel.STAFF && !ChatHandler.hasStaffPermission(p)) continue;
            if (from != null && IgnoreManager.isIgnoring(p.getUUID(), from)) continue;
            p.sendSystemMessage(text);
        }

        if (channel == ChatChannel.NETWORK) {
            MentionHandler.processRemoteMentions(srv, name, plain);
            if (cfg.logNetworkToConsole) {
                viaStyle.LOGGER.info("[Network/{}] {}: {}", str(msg, "display", "?"), name, plain);
            }
            relayNetworkToDiscord(name, plain);
        } else {
            ChatHandler.relaySocialSpy(srv, name, plain, SocialSpyManager.Channel.STAFF, "Staff");
            if (cfg.logStaffToConsole) {
                viaStyle.LOGGER.info("[Staff/{}] {}: {}", str(msg, "display", "?"), name, plain);
            }
        }
    }

    /** Discord relay for network chat, only on the server marked as the bridge. */
    public static void relayNetworkToDiscord(String senderName, String plain) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (!cfg.networkDiscordBridge || !BlockBotHelper.isAvailable()) return;
        BlockBotHelper.relayToDiscordAs(senderName, plain, cfg.blockbotGlobalChannel);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Private messages
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sends a PM to a player on another server. The sender sees {@code echo} once the target
     * server confirms delivery.
     */
    public static void sendPm(ServerPlayer sender, NetPlayer target, Component receiverMsg,
                              Component echo, String plain, boolean canSeeVanished) {
        String id = UUID.randomUUID().toString();
        PENDING_PMS.put(id, new PendingPm(sender.getUUID(), target.uuid(), target.name(), echo, plain,
                System.currentTimeMillis()));
        JsonObject msg = message("pm");
        msg.addProperty("id", id);
        msg.addProperty("from", sender.getUUID().toString());
        msg.addProperty("fromName", sender.getName().getString());
        msg.addProperty("to", target.uuid().toString());
        msg.addProperty("seeVanished", canSeeVanished);
        msg.add("json", toJson(sender.level().getServer(), receiverMsg));
        msg.addProperty("plain", plain);
        send(sender, msg);
    }

    private static void onPm(MinecraftServer srv, JsonObject msg) {
        String id = str(msg, "id", "");
        UUID from = uuid(msg, "from");
        UUID to = uuid(msg, "to");
        ServerPlayer receiver = to != null ? srv.getPlayerList().getPlayer(to) : null;
        String reason = null;
        if (receiver == null) {
            reason = "offline";
        } else if (from != null && IgnoreManager.isIgnoring(to, from)) {
            reason = "ignored";
        } else if (VanishHelper.isVanished(receiver)
                && !(msg.has("seeVanished") && msg.get("seeVanished").getAsBoolean())) {
            reason = "offline";
        }

        JsonObject result = message("pm_result");
        result.addProperty("id", id);
        result.addProperty("from", from != null ? from.toString() : "");
        result.addProperty("ok", reason == null);
        if (reason != null) {
            result.addProperty("reason", reason);
            sendAny(srv, result);
            return;
        }

        Component text = component(srv, msg);
        if (text != null) receiver.sendSystemMessage(text);
        PrivateMsgCommand.onRemotePmReceived(receiver, from);
        result.addProperty("toName", receiver.getName().getString());
        send(receiver, result);
    }

    private static void onPmResult(MinecraftServer srv, JsonObject msg) {
        PendingPm pending = PENDING_PMS.remove(str(msg, "id", ""));
        if (pending == null) return;
        ServerPlayer sender = srv.getPlayerList().getPlayer(pending.sender());
        if (sender == null) return;
        boolean ok = msg.has("ok") && msg.get("ok").getAsBoolean();
        if (!ok) {
            String reason = str(msg, "reason", "offline");
            sender.sendSystemMessage(Lang.get("ignored".equals(reason) ? "pm.error.ignored" : "error.player_not_found"));
            return;
        }
        sender.sendSystemMessage(pending.echo());
        PrivateMsgCommand.onRemotePmSent(sender, pending.target());

        String spyText = "[→ " + pending.targetName() + "] " + pending.plain();
        ChatHandler.relaySocialSpy(srv, sender, spyText, SocialSpyManager.Channel.PM, "PM");
        sendSpy(sender, SocialSpyManager.Channel.PM, sender.getName().getString(), spyText);
        if (viaStyle.CONFIG.logPrivatesToConsole) {
            viaStyle.LOGGER.info("[PM] {} -> {}: {}", sender.getName().getString(), pending.targetName(), pending.plain());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Social spy / profiles
    // ═══════════════════════════════════════════════════════════════════════

    public static void sendSpy(ServerPlayer via, SocialSpyManager.Channel channel, String name, String text) {
        if (!active()) return;
        JsonObject msg = message("spy");
        msg.addProperty("channel", channel.name());
        msg.addProperty("name", name);
        msg.addProperty("text", text);
        send(via, msg);
    }

    private static void onSpy(MinecraftServer srv, JsonObject msg) {
        SocialSpyManager.Channel channel = SocialSpyManager.Channel.fromString(str(msg, "channel", ""));
        if (channel == null) return;
        ChatHandler.relaySocialSpy(srv, str(msg, "name", "?"), str(msg, "text", ""), channel,
                channel == SocialSpyManager.Channel.PM ? "PM" : channel.name());
    }

    /** Stores one profile key for a player on the proxy (merged, then pushed to their server). */
    static void sendProfileKey(UUID uuid, String key, JsonElement value) {
        MinecraftServer srv = server;
        if (srv == null || !active()) return;
        JsonObject msg = message("profile_set");
        msg.addProperty("uuid", uuid.toString());
        msg.addProperty("key", key);
        msg.add("value", value);
        ServerPlayer self = srv.getPlayerList().getPlayer(uuid);
        if (self != null) {
            send(self, msg);
        } else {
            sendAny(srv, msg);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Dispatch
    // ═══════════════════════════════════════════════════════════════════════

    private static void handle(MinecraftServer srv, JsonObject msg) {
        if (!proxySeen) {
            proxySeen = true;
            viaStyle.LOGGER.info("[viaStyle] Connected to the viaStyle proxy plugin, network chat is on.");
        }
        switch (str(msg, "t", "")) {
            case "welcome" -> onWelcome(srv, msg);
            case "players" -> onPlayers(msg);
            case "player" -> {
                NetPlayer p = netPlayer(msg);
                if (p != null) PLAYERS.put(p.uuid(), p);
            }
            case "player_left" -> {
                UUID uuid = uuid(msg, "uuid");
                if (uuid != null) PLAYERS.remove(uuid);
            }
            case "announce" -> onAnnounce(srv, msg);
            case "chat" -> onChat(srv, msg);
            case "pm" -> onPm(srv, msg);
            case "pm_result" -> onPmResult(srv, msg);
            case "spy" -> onSpy(srv, msg);
            case "profile_set" -> {
                UUID uuid = uuid(msg, "uuid");
                if (uuid != null && msg.has("key")) {
                    Profiles.applyKey(uuid, msg.get("key").getAsString(), msg.get("value"));
                }
            }
            default -> { }
        }
    }

    private static void onPlayers(JsonObject msg) {
        if (msg.has("reset") && msg.get("reset").getAsBoolean()) PLAYERS.clear();
        if (!msg.has("list")) return;
        for (JsonElement e : msg.getAsJsonArray("list")) {
            if (!e.isJsonObject()) continue;
            NetPlayer p = netPlayer(e.getAsJsonObject());
            if (p != null) PLAYERS.put(p.uuid(), p);
        }
    }

    private static NetPlayer netPlayer(JsonObject o) {
        UUID uuid = uuid(o, "uuid");
        if (uuid == null) return null;
        return new NetPlayer(uuid, str(o, "name", "?"), str(o, "server", "?"),
                str(o, "display", str(o, "server", "?")),
                o.has("vanished") && o.get("vanished").getAsBoolean());
    }

    private static void tick(MinecraftServer srv) {
        if (!enabled()) return;
        if (!PENDING_PMS.isEmpty()) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, PendingPm>> it = PENDING_PMS.entrySet().iterator();
            while (it.hasNext()) {
                PendingPm pm = it.next().getValue();
                if (now - pm.sentAt() < PM_TIMEOUT_MS) continue;
                it.remove();
                ServerPlayer sender = srv.getPlayerList().getPlayer(pm.sender());
                if (sender != null) sender.sendSystemMessage(Lang.get("pm.error.not_delivered"));
            }
        }
        if (AWAITING_WELCOME.isEmpty()) return;
        Iterator<Map.Entry<UUID, int[]>> it = AWAITING_WELCOME.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, int[]> e = it.next();
            int[] state = e.getValue();
            if (--state[1] > 0) continue;
            ServerPlayer player = srv.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (state[0] >= HELLO_MAX_TRIES) {
                it.remove();
                if (!proxySeen && !warnedNoProxy) {
                    warnedNoProxy = true;
                    viaStyle.LOGGER.warn("[viaStyle] network.enabled is true but the proxy never answered. "
                            + "Is viastyle-velocity installed on Velocity? Falling back to local join messages.");
                }
                if (!proxySeen) viaStyleServer.announceJoinLocally(srv, player);
                continue;
            }
            state[0]++;
            state[1] = HELLO_RETRY_TICKS;
            sendHello(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static JsonObject message(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        return o;
    }

    private static void send(ServerPlayer via, JsonObject msg) {
        if (via == null || via.hasDisconnected()) {
            MinecraftServer srv = server;
            if (srv != null) sendAny(srv, msg);
            return;
        }
        ServerPlayNetworking.send(via, new NetPayload(msg.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static void sendAny(MinecraftServer srv, JsonObject msg) {
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.hasDisconnected()) continue;
            ServerPlayNetworking.send(p, new NetPayload(msg.toString().getBytes(StandardCharsets.UTF_8)));
            return;
        }
    }

    static JsonElement toJson(MinecraftServer srv, Component component) {
        return ComponentSerialization.CODEC
                .encodeStart(srv.registryAccess().createSerializationContext(JsonOps.INSTANCE), component)
                .getOrThrow();
    }

    /** Parses {@code json}; falls back to {@code plain} (e.g. an item hover from a mod this server lacks). */
    private static Component component(MinecraftServer srv, JsonObject msg) {
        if (msg.has("json")) {
            var parsed = ComponentSerialization.CODEC
                    .parse(srv.registryAccess().createSerializationContext(JsonOps.INSTANCE), msg.get("json"))
                    .result();
            if (parsed.isPresent()) return parsed.get();
        }
        return msg.has("plain") ? Component.literal(msg.get("plain").getAsString()) : null;
    }

    private static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static UUID uuid(JsonObject o, String key) {
        try {
            String s = str(o, key, null);
            return s == null || s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static JsonArray uuidArray(Collection<UUID> uuids) {
        JsonArray arr = new JsonArray();
        for (UUID u : uuids) arr.add(u.toString());
        return arr;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
