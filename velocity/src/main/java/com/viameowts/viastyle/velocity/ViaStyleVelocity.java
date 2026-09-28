package com.viameowts.viastyle.velocity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxy side of viaStyle's server network.
 *
 * <p>The backends (Fabric + viaStyle with {@code [network] enabled = true}) format every
 * message themselves; this plugin only routes them, keeps the network player list, stores
 * per-player profiles and decides whether a connection is a network join, a server switch
 * or a quit. Messages are UTF-8 JSON on the {@code viastyle:net} plugin channel.</p>
 *
 * <p>Messages on that channel coming from a player's client are always dropped, so a
 * modified client cannot spoof chat or PMs.</p>
 */
@Plugin(id = "viastyle", name = "viaStyle", version = "1.0.0",
        description = "Network chat for viaStyle: network/staff channels, cross-server PMs, synced settings",
        authors = {"viaMeowts"})
public final class ViaStyleVelocity {
    static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("viastyle", "net");
    private static final int PLAYERS_PAGE = 80;

    private record PendingJoin(String kind, String from) {}

    private final ProxyServer proxy;
    private final Logger log;
    private final ProfileStore profiles;

    /** Set on ServerConnectedEvent, consumed by the backend's hello. */
    private final Map<UUID, PendingJoin> pending = new ConcurrentHashMap<>();
    /** Server whose hello was already answered for the current connection (retries get kind "none"). */
    private final Map<UUID, String> welcomedOn = new ConcurrentHashMap<>();
    /** Pre-rendered leave messages from the backends (component JSON). */
    private final Map<UUID, JsonElement> leaveMessages = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    /** Server name -> display name reported by its backend. */
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();

    @Inject
    public ViaStyleVelocity(ProxyServer proxy, Logger log, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.log = log;
        this.profiles = new ProfileStore(dataDir.resolve("profiles"), log);
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        log.info("viaStyle network bridge ready on channel {}", CHANNEL.getId());
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        profiles.flush();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Connection lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Subscribe
    public void onConnected(ServerConnectedEvent event) {
        Optional<RegisteredServer> previous = event.getPreviousServer();
        pending.put(event.getPlayer().getUniqueId(), previous
                .map(s -> new PendingJoin("switch", s.getServerInfo().getName()))
                .orElse(new PendingJoin("join", null)));
        welcomedOn.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(conn -> {
            JsonObject msg = message("player");
            fillPlayer(msg, player, conn.getServerInfo().getName());
            broadcast(msg, null);
        });
    }

    @Subscribe(order = PostOrder.LATE)
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        pending.remove(uuid);
        welcomedOn.remove(uuid);

        JsonObject left = message("player_left");
        left.addProperty("uuid", uuid.toString());
        broadcast(left, null);

        JsonElement leave = leaveMessages.remove(uuid);
        boolean wasVanished = vanished.remove(uuid);
        if (leave != null && !wasVanished
                && event.getLoginStatus() == DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            JsonObject announce = message("announce");
            announce.addProperty("uuid", uuid.toString());
            announce.add("json", leave);
            announce.addProperty("plain", player.getUsername());
            broadcast(announce, null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Plugin messages
    // ═══════════════════════════════════════════════════════════════════════

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        // Never forward this channel: backend messages are for us, client messages are spoofs.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection conn)) return;

        JsonObject msg;
        try {
            msg = JsonParser.parseString(new String(event.getData(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            log.warn("Bad viaStyle message from {}: {}", conn.getServerInfo().getName(), e.getMessage());
            return;
        }
        String origin = conn.getServerInfo().getName();
        switch (str(msg, "t")) {
            case "hello" -> onHello(conn, msg);
            case "presence" -> onPresence(msg);
            case "announce" -> broadcast(msg, null);
            case "chat" -> {
                msg.addProperty("origin", origin);
                broadcast(msg, origin);
            }
            case "spy" -> broadcast(msg, origin);
            case "pm" -> onPm(conn, msg);
            case "pm_result" -> sendToPlayerServer(uuid(msg, "from"), msg);
            case "profile_set" -> onProfileSet(origin, msg);
            default -> { }
        }
    }

    private void onHello(ServerConnection conn, JsonObject msg) {
        UUID uuid = uuid(msg, "uuid");
        if (uuid == null) return;
        String serverName = conn.getServerInfo().getName();
        String display = str(msg, "display");
        if (!display.isEmpty()) displayNames.put(serverName, display);
        if (msg.has("vanished") && msg.get("vanished").getAsBoolean()) vanished.add(uuid);
        else vanished.remove(uuid);

        String kind;
        String from = null;
        if (serverName.equals(welcomedOn.get(uuid))) {
            kind = "none"; // hello retry after a lost welcome: don't announce twice
        } else {
            PendingJoin join = pending.remove(uuid);
            kind = join != null ? join.kind() : "join";
            from = join != null ? join.from() : null;
            welcomedOn.put(uuid, serverName);
        }

        boolean first = !profiles.exists(uuid);
        JsonObject welcome = message("welcome");
        welcome.addProperty("uuid", uuid.toString());
        welcome.addProperty("server", serverName);
        welcome.addProperty("kind", kind);
        welcome.addProperty("first", first);
        if (from != null) {
            welcome.addProperty("from", from);
            welcome.addProperty("fromDisplay", displayNames.getOrDefault(from, from));
        }
        welcome.add("profile", profiles.get(uuid));
        if (first) profiles.touch(uuid);
        send(conn.getServer(), welcome);
        sendPlayerList(conn.getServer());

        // Refresh this player's entry now that the display name / vanish state is known.
        proxy.getPlayer(uuid).ifPresent(p -> {
            JsonObject entry = message("player");
            fillPlayer(entry, p, serverName);
            broadcast(entry, null);
        });
    }

    private void onPresence(JsonObject msg) {
        UUID uuid = uuid(msg, "uuid");
        if (uuid == null) return;
        if (msg.has("leave")) leaveMessages.put(uuid, msg.get("leave"));
        if (msg.has("vanished") && msg.get("vanished").getAsBoolean()) vanished.add(uuid);
        else vanished.remove(uuid);
    }

    private void onPm(ServerConnection conn, JsonObject msg) {
        UUID to = uuid(msg, "to");
        Optional<ServerConnection> target = to == null ? Optional.empty()
                : proxy.getPlayer(to).flatMap(Player::getCurrentServer);
        if (target.isEmpty()) {
            JsonObject result = message("pm_result");
            result.addProperty("id", str(msg, "id"));
            result.addProperty("from", str(msg, "from"));
            result.addProperty("ok", false);
            result.addProperty("reason", "offline");
            send(conn.getServer(), result);
            return;
        }
        send(target.get().getServer(), msg);
    }

    private void onProfileSet(String origin, JsonObject msg) {
        UUID uuid = uuid(msg, "uuid");
        if (uuid == null || !msg.has("key")) return;
        profiles.set(uuid, msg.get("key").getAsString(), msg.get("value"));
        proxy.getPlayer(uuid).flatMap(Player::getCurrentServer).ifPresent(conn -> {
            if (!conn.getServerInfo().getName().equals(origin)) send(conn.getServer(), msg);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private void sendPlayerList(RegisteredServer server) {
        List<JsonObject> entries = new ArrayList<>();
        for (Player p : proxy.getAllPlayers()) {
            Optional<ServerConnection> conn = p.getCurrentServer();
            if (conn.isEmpty()) continue;
            JsonObject e = new JsonObject();
            fillPlayer(e, p, conn.get().getServerInfo().getName());
            entries.add(e);
        }
        // Plugin messages to a backend are capped at 32 KiB, so the list goes in pages.
        for (int i = 0; i == 0 || i < entries.size(); i += PLAYERS_PAGE) {
            JsonObject page = message("players");
            page.addProperty("reset", i == 0);
            JsonArray list = new JsonArray();
            for (JsonObject e : entries.subList(i, Math.min(entries.size(), i + PLAYERS_PAGE))) list.add(e);
            page.add("list", list);
            send(server, page);
        }
    }

    private void fillPlayer(JsonObject o, Player p, String serverName) {
        o.addProperty("uuid", p.getUniqueId().toString());
        o.addProperty("name", p.getUsername());
        o.addProperty("server", serverName);
        o.addProperty("display", displayNames.getOrDefault(serverName, serverName));
        o.addProperty("vanished", vanished.contains(p.getUniqueId()));
    }

    private void sendToPlayerServer(UUID uuid, JsonObject msg) {
        if (uuid == null) return;
        proxy.getPlayer(uuid).flatMap(Player::getCurrentServer)
                .ifPresent(conn -> send(conn.getServer(), msg));
    }

    /** Sends to every server that has players, except {@code exceptServer}. */
    private void broadcast(JsonObject msg, String exceptServer) {
        byte[] data = bytes(msg);
        for (RegisteredServer server : proxy.getAllServers()) {
            if (server.getServerInfo().getName().equals(exceptServer)) continue;
            if (server.getPlayersConnected().isEmpty()) continue;
            server.sendPluginMessage(CHANNEL, data);
        }
    }

    private void send(RegisteredServer server, JsonObject msg) {
        server.sendPluginMessage(CHANNEL, bytes(msg));
    }

    private static byte[] bytes(JsonObject msg) {
        return msg.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject message(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        return o;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static UUID uuid(JsonObject o, String key) {
        try {
            String s = str(o, key);
            return s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
