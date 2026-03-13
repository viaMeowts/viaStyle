package com.viameowts.viastyle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Reflection-based BlockBot integration.
 *
 * <p><b>Minecraft → Discord</b>: viaStyle cancels vanilla chat via
 * {@code ALLOW_CHAT_MESSAGE} returning {@code false}, so BlockBot's own
 * {@code ServerMessageEvents.CHAT_MESSAGE} listener never fires.  Instead,
 * we call {@code BlockBotApi.sendRelayMessage(content, channel)} directly
 * after processing each message.</p>
 *
 * <p><b>Discord → Minecraft</b>: we register a {@code RelayMessageEvent}
 * listener (via {@link Proxy}) so that Discord messages are broadcast
 * in-game through viaStyle.</p>
 */
public final class BlockBotHelper {

    private static boolean available = false;
    private static Method sendRelayMethod;
    private static String defaultChatChannel = "chat";

    /** Cached server reference set by {@link ServerLifecycleEvents#SERVER_STARTED}. */
    private static MinecraftServer cachedServer = null;

    /**
     * All known mod IDs for BlockBot across versions.
     * Modern (1.21+): "blockbot-api" (API jar) + "blockbot-discord" (Discord jar)
     * Legacy (1.17–1.20): "blockbot"
     */
    private static final String[] KNOWN_MOD_IDS = {
            "blockbot-api",      // modern API jar (1.21+)
            "blockbot-discord",  // modern Discord jar
            "blockbot",          // legacy (1.17–1.20)
            "block-bot",         // possible alternate ID
            "block_bot"          // possible alternate ID
    };

    /** Known package paths for the BlockBot API class. */
    private static final String[] KNOWN_API_CLASSES = {
            "io.github.quiltservertools.blockbotapi.BlockBotApi",     // modern (1.21+)
            "io.github.quiltservertools.blockbot.api.BlockBotApi",    // mid-era
            "io.github.quiltservertools.blockbot.BlockBotApi",        // legacy
            "com.github.quiltservertools.blockbot.BlockBot",          // very old (1.17)
            "com.github.quiltservertools.blockbot.api.BlockBotApi"    // old API variant
    };

    /**
     * Known package paths for RelayMessageEvent.
     * Modern: RelayMessageEvent IS the functional interface itself
     *         Signature: message(RelayMessageSender, String channel, String message) → ActionResult
     * Legacy: RelayMessageEvent has a nested interface
     *         Signature: message(String content, String channel) → void
     */
    private static final String[] KNOWN_RELAY_EVENT_CLASSES = {
            "io.github.quiltservertools.blockbotapi.event.RelayMessageEvent",  // modern
            "io.github.quiltservertools.blockbot.api.event.RelayMessageEvent", // mid
            "io.github.quiltservertools.blockbot.event.RelayMessageEvent"      // legacy
    };

    /** Known package paths for the Channels constants class. */
    private static final String[] KNOWN_CHANNELS_CLASSES = {
            "io.github.quiltservertools.blockbotapi.Channels",       // modern
            "io.github.quiltservertools.blockbot.api.Channels",      // mid
            "io.github.quiltservertools.blockbot.Channels"           // legacy
    };

    /** Known package paths for ChatMessageEvent (the event BlockBot actually listens on for MC→Discord). */
    private static final String[] KNOWN_CHAT_EVENT_CLASSES = {
            "io.github.quiltservertools.blockbotapi.event.ChatMessageEvent",  // modern
            "io.github.quiltservertools.blockbot.api.event.ChatMessageEvent", // mid
            "io.github.quiltservertools.blockbot.event.ChatMessageEvent"      // legacy
    };

    /** Known package paths for PlayerMessageSender (wraps a player for ChatMessageEvent). */
    private static final String[] KNOWN_PLAYER_SENDER_CLASSES = {
            "io.github.quiltservertools.blockbotapi.sender.PlayerMessageSender",  // modern
            "io.github.quiltservertools.blockbot.api.sender.PlayerMessageSender", // mid
            "io.github.quiltservertools.blockbot.sender.PlayerMessageSender"      // legacy
    };

    /**
     * Cache populated from {@code RelayMessageSender} data on every incoming Discord message.
     * Maps lowercase name / nickname / global display name -> Discord user ID (string).
     * Used to resolve {@code @Name} tokens in game chat into real Discord pings.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> discordMemberCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Reverse lookup: Discord user ID -> best display name.
     * Used by {@link #sanitizeDiscordContent} to expand {@code <@id>} into a readable name.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> discordIdToDisplayCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Cached ActionResult.PASS for returning from the relay proxy. */
    private static Object actionResultPass = null;

    // ── ChatMessageEvent (direct MC → Discord relay) ───────────────────────────
    // Modern BlockBot picks up chat via ChatMessageEvent.EVENT, NOT sendRelayMessage.
    // Because viaStyle cancels ALLOW_CHAT_MESSAGE (returns false), BlockBot's normal
    // ServerMessageEvents.CHAT_MESSAGE listener never fires — so we fire it manually.
    private static Object chatEventObj = null;            // ChatMessageEvent.EVENT
    private static Method chatEventInvokerMethod = null;  // EVENT.invoker()
    private static Method chatEventMessageMethod = null;  // .message(MessageSender, Text)
    private static Constructor<?> playerSenderCtor = null; // PlayerMessageSender(player, type)
    private static Object messageTypeRegular = null;       // MessageSender.MessageType.REGULAR

    private BlockBotHelper() {}

    // ── Reflection helpers ─────────────────────────────────────────────────────

    /**
     * Checks all known mod IDs for BlockBot. Also tries class-based detection
     * as a fallback (the mod jar may be present without a matching mod ID if
     * it was loaded as a library / jar-in-jar).
     */
    private static boolean isBlockBotLoaded() {
        // 1. Try FabricLoader mod IDs
        for (String id : KNOWN_MOD_IDS) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                viaStyle.LOGGER.info("[viaStyle] BlockBot detected via mod ID \"{}\".", id);
                return true;
            }
        }

        // 2. Fallback: try to load any known API class directly
        //    (covers cases where BlockBot is jar-in-jar or has a non-standard mod ID)
        for (String cls : KNOWN_API_CLASSES) {
            try {
                Class.forName(cls);
                viaStyle.LOGGER.info("[viaStyle] BlockBot detected via class \"{}\" (no mod ID matched).", cls);
                return true;
            } catch (ClassNotFoundException ignored) {}
        }

        return false;
    }

    /** Tries all known package paths for the BlockBot API class. */
    private static Class<?> resolveApiClass() {
        for (String name : KNOWN_API_CLASSES) {
            try {
                Class<?> cls = Class.forName(name);
                viaStyle.LOGGER.debug("[viaStyle] Resolved BlockBot API class: {}", name);
                return cls;
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    /** Tries to find the relay send method on the resolved API class. */
    private static Method findSendMethod(Class<?> apiClass) {
        // Exact signature first: sendRelayMessage(String, String)
        try {
            Method m = apiClass.getMethod("sendRelayMessage", String.class, String.class);
            viaStyle.LOGGER.debug("[viaStyle] Found exact sendRelayMessage(String, String) on {}.", apiClass.getName());
            return m;
        } catch (NoSuchMethodException ignored) {}

        // Fallback: any public static method(String, String) with a relay/send-like name
        for (Method m : apiClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            if (m.getParameterCount() != 2) continue;
            if (m.getParameterTypes()[0] != String.class || m.getParameterTypes()[1] != String.class) continue;
            String n = m.getName().toLowerCase();
            if (n.contains("relay") || n.contains("send") || n.contains("message")) {
                viaStyle.LOGGER.debug("[viaStyle] Found send method via scan: {}.{}()", apiClass.getName(), m.getName());
                return m;
            }
        }
        return null;
    }

    /** Tries all known package paths for the RelayMessageEvent class. */
    private static Class<?> resolveRelayEventClass() {
        for (String name : KNOWN_RELAY_EVENT_CLASSES) {
            try {
                Class<?> cls = Class.forName(name);
                viaStyle.LOGGER.debug("[viaStyle] Resolved RelayMessageEvent: {}", name);
                return cls;
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    /** Reads the Channels.CHAT constant from any known Channels class. */
    private static void resolveDefaultChannel() {
        for (String clsName : KNOWN_CHANNELS_CLASSES) {
            try {
                Class<?> channelsClass = Class.forName(clsName);
                Field chatField = channelsClass.getField("CHAT");
                Object val = chatField.get(null);
                if (val instanceof String s) {
                    defaultChatChannel = s;
                    viaStyle.LOGGER.debug("[viaStyle] Resolved Channels.CHAT = \"{}\" from {}.", s, clsName);
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Pre-resolves {@code ActionResult.PASS} via reflection (needed for the
     * modern RelayMessageEvent that returns ActionResult instead of void).
     */
    private static void resolveActionResult() {
        for (String cls : new String[]{
                "net.minecraft.util.ActionResult",
                "net.minecraft.util.ActionResult"
        }) {
            try {
                Class<?> arClass = Class.forName(cls);
                Field passField = arClass.getField("PASS");
                actionResultPass = passField.get(null);
                viaStyle.LOGGER.debug("[viaStyle] Resolved ActionResult.PASS.");
                return;
            } catch (Throwable ignored) {}
        }
        // If ActionResult can't be resolved, proxy will return null — still works for void-return APIs
    }

    /**
     * Resolves {@code ChatMessageEvent.EVENT} and {@code PlayerMessageSender} via reflection.
     *
     * <p>This is the <b>primary MC → Discord</b> path in modern BlockBot:
     * {@code ChatMessageEvent.EVENT.invoker().message(new PlayerMessageSender(player, REGULAR), text)}
     * fires all registered listeners, including BlockBot-Discord's handler that posts to Discord.</p>
     *
     * <p>This replaces the old {@code sendRelayMessage} approach which is a no-op in
     * some BlockBot versions.</p>
     */
    private static void resolveChatMessageEvent() {
        for (String className : KNOWN_CHAT_EVENT_CLASSES) {
            try {
                Class<?> eventClass = Class.forName(className);
                Field eventField = eventClass.getField("EVENT");
                chatEventObj = eventField.get(null);

                // Resolve invoker() on the Fabric Event object
                chatEventInvokerMethod = chatEventObj.getClass().getMethod("invoker");

                // Find the message() method on the ChatMessageEvent interface
                for (Method m : eventClass.getDeclaredMethods()) {
                    if ("message".equals(m.getName()) && m.getParameterCount() == 2) {
                        chatEventMessageMethod = m;
                        break;
                    }
                }
                if (chatEventMessageMethod == null) {
                    viaStyle.LOGGER.debug("[viaStyle] ChatMessageEvent {} has no message(2-arg) method.", className);
                    chatEventObj = null;
                    continue;
                }

                Class<?> senderParamType = chatEventMessageMethod.getParameterTypes()[0]; // MessageSender
                viaStyle.LOGGER.debug("[viaStyle] ChatMessageEvent.message({}, {})",
                        senderParamType.getSimpleName(),
                        chatEventMessageMethod.getParameterTypes()[1].getSimpleName());

                // Resolve PlayerMessageSender and MessageType.REGULAR
                resolvePlayerSender(senderParamType);

                if (playerSenderCtor != null && messageTypeRegular != null) {
                    viaStyle.LOGGER.info("[viaStyle] ChatMessageEvent resolved => will fire directly for MC->Discord.");
                    return;
                } else {
                    viaStyle.LOGGER.debug("[viaStyle] PlayerMessageSender not fully resolved for {}.", className);
                }
            } catch (Throwable t) {
                viaStyle.LOGGER.debug("[viaStyle] Could not resolve ChatMessageEvent from {}: {}", className, t.getMessage());
            }
        }

        // Clear partial state if resolution failed
        chatEventObj = null;
        chatEventInvokerMethod = null;
        chatEventMessageMethod = null;
        viaStyle.LOGGER.info("[viaStyle] ChatMessageEvent not available => will use sendRelayMessage fallback.");
    }

    /**
     * Finds the {@code PlayerMessageSender(ServerPlayerEntity, MessageType)} constructor
     * and the {@code MessageType.REGULAR} enum constant.
     */
    private static void resolvePlayerSender(Class<?> messageSenderInterface) {
        for (String psName : KNOWN_PLAYER_SENDER_CLASSES) {
            try {
                Class<?> psCls = Class.forName(psName);
                if (!messageSenderInterface.isAssignableFrom(psCls)) continue;

                for (Constructor<?> ctor : psCls.getConstructors()) {
                    Class<?>[] paramTypes = ctor.getParameterTypes();
                    if (paramTypes.length == 2
                            && paramTypes[0].isAssignableFrom(ServerPlayerEntity.class)) {
                        playerSenderCtor = ctor;
                        // Second param should be the MessageType enum
                        Class<?> enumType = paramTypes[1];
                        if (enumType.isEnum()) {
                            for (Object constant : enumType.getEnumConstants()) {
                                if ("REGULAR".equals(((Enum<?>) constant).name())) {
                                    messageTypeRegular = constant;
                                    break;
                                }
                            }
                        }
                        if (messageTypeRegular != null) {
                            viaStyle.LOGGER.debug("[viaStyle] Resolved PlayerMessageSender: {}", psName);
                            return;
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {}
        }
    }

    // ── Initialisation ─────────────────────────────────────────────────────────

    public static void init() {
        if (!isBlockBotLoaded()) {
            viaStyle.LOGGER.info("[viaStyle] BlockBot not found => Discord relay disabled.");
            // Log which IDs were checked
            viaStyle.LOGGER.debug("[viaStyle]   Checked mod IDs: {}", Arrays.toString(KNOWN_MOD_IDS));
            viaStyle.LOGGER.debug("[viaStyle]   Checked classes: {}", Arrays.toString(KNOWN_API_CLASSES));
            return;
        }
        try {
            Class<?> apiClass = resolveApiClass();
            if (apiClass == null) {
                viaStyle.LOGGER.warn("[viaStyle] BlockBot detected but API class not found => relay disabled.");
                viaStyle.LOGGER.warn("[viaStyle]   Tried: {}", String.join(", ", KNOWN_API_CLASSES));
                return;
            }

            sendRelayMethod = findSendMethod(apiClass);
            if (sendRelayMethod == null) {
                viaStyle.LOGGER.warn("[viaStyle] BlockBot API found ({}) but no send method.",
                        apiClass.getName());
                // Log all methods for debugging
                viaStyle.LOGGER.warn("[viaStyle]   Available methods:");
                for (Method m : apiClass.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers())) {
                        viaStyle.LOGGER.warn("[viaStyle]     {} {}.{}({})",
                                m.getReturnType().getSimpleName(), apiClass.getSimpleName(), m.getName(),
                                Arrays.toString(m.getParameterTypes()));
                    }
                }
                // Don't return — ChatMessageEvent might still work
            }

            resolveDefaultChannel();
            resolveActionResult();
            resolveChatMessageEvent();

            // We're available if EITHER ChatMessageEvent OR sendRelayMethod resolved
            available = (chatEventObj != null) || (sendRelayMethod != null);
            if (!available) {
                viaStyle.LOGGER.warn("[viaStyle] BlockBot detected but no working relay method found.");
                return;
            }

            viaStyle.LOGGER.info("[viaStyle] BlockBot integration enabled => relay channel: \"{}\"{}.",
                    defaultChatChannel,
                    chatEventObj != null ? " (ChatMessageEvent)" : " (sendRelayMessage)");

            // Cache MinecraftServer so the relay listener can broadcast messages
            ServerLifecycleEvents.SERVER_STARTED.register(server -> cachedServer = server);
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> cachedServer = null);

            // Discord → Minecraft relay:
            // BlockBot already handles Discord→MC broadcasting itself (using its own messageFormat,
            // convertMarkdown, replyFormat, etc. from blockbot.toml). Our RelayMessageEvent listener
            // must NOT be registered in passthrough mode, or it will broadcast a second raw copy
            // of the message and override/duplicate BlockBot's fully-formatted output.
            //
            // Only register our own listener when the user explicitly sets passthrough=false,
            // meaning they want viaStyle's discordFormat to own the Discord→MC formatting instead.
            ViaStyleConfig cfg = viaStyle.CONFIG;
            boolean passthrough = cfg == null || cfg.discordPassthrough; // default true
            if (!passthrough) {
                viaStyle.LOGGER.info("[viaStyle] discordPassthrough=false => registering custom Discord->MC relay.");
                registerDiscordToMinecraftRelay();
                // Sender cache extractor only needed in non-passthrough mode.
                // In passthrough mode BlockBot's native listener handles RelayMessageEvent;
                // adding our proxy could break the event chain if ActionResult can't be resolved.
                registerSenderDataExtractor();
            } else {
                viaStyle.LOGGER.info("[viaStyle] discordPassthrough=true => BlockBot handles Discord->MC natively.");
            }

            // @mention ping scanner for MC-side mentions from Discord messages.
            // Hooks into GAME_MESSAGE which fires on every playerManager.broadcast(),
            // including BlockBot's own Discord->MC relay — works in any passthrough mode.
            ServerMessageEvents.GAME_MESSAGE.register(
                    (server, message, overlay) -> {
                        ViaStyleConfig mcfg = viaStyle.CONFIG;
                        if (mcfg == null || !mcfg.mentionsEnabled || !mcfg.discordMentionPing) return;
                        if (overlay) return;
                        MentionHandler.processDiscordMentions(server, message.getString(), null);
                    });
            viaStyle.LOGGER.info("[viaStyle] Registered GAME_MESSAGE mention scanner.");

        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] BlockBot init failed: {}", t.getMessage());
            viaStyle.LOGGER.debug("[viaStyle] BlockBot init stacktrace:", t);
        }
    }

    // ── Discord → Minecraft relay ──────────────────────────────────────────────

    /**
     * Registers a lightweight {@code RelayMessageEvent} listener that only extracts
     * sender data into the local cache (never broadcasts anything, always returns PASS).
     * This populates {@link #discordMemberCache} so game-side {@code @Name} tokens can
     * be resolved to Discord user IDs for real pings.
     */
    private static void registerSenderDataExtractor() {
        try {
            Class<?> relayEventClass = resolveRelayEventClass();
            if (relayEventClass == null) {
                viaStyle.LOGGER.debug("[viaStyle] RelayMessageEvent not found => sender extractor skipped.");
                return;
            }
            Field eventField = relayEventClass.getField("EVENT");
            Object event = eventField.get(null);

            Class<?> targetInterface = relayEventClass.isInterface() ? relayEventClass : null;
            if (targetInterface == null) {
                for (Class<?> inner : relayEventClass.getDeclaredClasses()) {
                    if (inner.isInterface()) { targetInterface = inner; break; }
                }
            }
            if (targetInterface == null) return;

            final Class<?> proxyInterface = targetInterface;
            Object proxy = Proxy.newProxyInstance(
                    proxyInterface.getClassLoader(),
                    new Class<?>[]{ proxyInterface },
                    (proxyObj, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "viaStyle-SenderExtractor";
                                case "hashCode" -> System.identityHashCode(proxyObj);
                                case "equals"   -> proxyObj == args[0];
                                default         -> null;
                            };
                        }
                        if (args != null && args.length >= 1) populateSenderCache(args[0]);
                        return actionResultPass; // always PASS — never broadcast
                    }
            );

            boolean registered = false;
            try {
                Class<?> fabricEvent = Class.forName("net.fabricmc.fabric.api.event.Event");
                fabricEvent.getMethod("register", Object.class).invoke(event, proxy);
                registered = true;
            } catch (Throwable ignored) {}
            if (!registered) {
                for (Method m : event.getClass().getMethods()) {
                    if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                        try { m.setAccessible(true); m.invoke(event, proxy); registered = true; } catch (Throwable ignored) {}
                        break;
                    }
                }
            }
            if (registered) viaStyle.LOGGER.info("[viaStyle] Registered RelayMessageEvent sender-cache extractor.");
        } catch (Throwable t) {
            viaStyle.LOGGER.debug("[viaStyle] Sender extractor registration failed: {}", t.getMessage());
        }
    }

    /**
     * Extracts {@code id()}, {@code name()}, {@code nickname()}, {@code getDisplayName()}
     * from a {@code RelayMessageSender} record and populates the member caches.
     * <ul>
     *   <li>{@code name()} = Discord username</li>
     *   <li>{@code nickname()} = server nickname (the value the player types as {@code @Nick})</li>
     *   <li>{@code id()} = Discord user ID string</li>
     * </ul>
     */
    private static void populateSenderCache(Object sender) {
        if (sender == null) return;
        try {
            String id = null, name = null, nickname = null, displayName = null;
            try { Object v = sender.getClass().getMethod("id").invoke(sender);           if (v instanceof String s && !s.isBlank()) id = s.trim();          } catch (Throwable ignored) {}
            try { Object v = sender.getClass().getMethod("name").invoke(sender);         if (v instanceof String s && !s.isBlank()) name = s.trim();        } catch (Throwable ignored) {}
            try { Object v = sender.getClass().getMethod("nickname").invoke(sender);     if (v instanceof String s && !s.isBlank()) nickname = s.trim();    } catch (Throwable ignored) {}
            try { Object v = sender.getClass().getMethod("getDisplayName").invoke(sender); if (v instanceof String s && !s.isBlank()) displayName = s.trim(); } catch (Throwable ignored) {}

            if (id == null) return;

            // Reverse lookup: Discord ID → display name (for sanitizeDiscordContent <@id> expansion)
            String best = displayName != null ? displayName : (nickname != null ? nickname : name);
            if (best != null) discordIdToDisplayCache.put(id, best);

            // Forward lookup: all known names/nicks → ID (for resolveDiscordMentions @Name)
            if (name != null)        discordMemberCache.put(name.toLowerCase(), id);
            if (nickname != null)    discordMemberCache.put(nickname.toLowerCase(), id);
            if (displayName != null) discordMemberCache.put(displayName.toLowerCase(), id);

            viaStyle.LOGGER.debug("[viaStyle] Cached Discord sender: id={} name='{}' nick='{}'", id, name, nickname);
        } catch (Throwable ignored) {}
    }

    /**
     * Registers a listener on {@code RelayMessageEvent.EVENT}.
     *
     * <p>Modern BlockBot (1.21+): {@code RelayMessageEvent} IS the functional interface.
     * Signature: {@code message(RelayMessageSender sender, String channel, String message) → ActionResult}</p>
     *
     * <p>Legacy BlockBot: Has a nested inner interface.
     * Signature: {@code message(String content, String channel) → void}</p>
     *
     * <p>We use {@link Proxy} to handle both variants without compile-time dependency.</p>
     */
    private static void registerDiscordToMinecraftRelay() {
        try {
            Class<?> relayEventClass = resolveRelayEventClass();
            if (relayEventClass == null) {
                viaStyle.LOGGER.warn("[viaStyle] RelayMessageEvent not found => Discord->MC relay disabled.");
                return;
            }

            Field eventField = relayEventClass.getField("EVENT");
            Object event = eventField.get(null);

            // Determine what interface to proxy:
            // Modern API: RelayMessageEvent IS the interface itself
            // Legacy API: RelayMessageEvent contains a nested interface
            Class<?> targetInterface;
            if (relayEventClass.isInterface()) {
                // Modern: the class itself is the functional interface
                targetInterface = relayEventClass;
                viaStyle.LOGGER.debug("[viaStyle] RelayMessageEvent is the interface itself (modern API).");
            } else {
                // Legacy: look for a nested interface
                targetInterface = null;
                for (Class<?> inner : relayEventClass.getDeclaredClasses()) {
                    if (inner.isInterface()) {
                        targetInterface = inner;
                        break;
                    }
                }
                if (targetInterface == null) {
                    viaStyle.LOGGER.warn("[viaStyle] RelayMessageEvent has no nested interface => relay disabled.");
                    return;
                }
                viaStyle.LOGGER.debug("[viaStyle] Using nested interface: {}", targetInterface.getName());
            }

            // Log the interface methods for debugging
            for (Method m : targetInterface.getDeclaredMethods()) {
                viaStyle.LOGGER.debug("[viaStyle]   Interface method: {} {}({}) ",
                        m.getReturnType().getSimpleName(), m.getName(),
                        Arrays.toString(m.getParameterTypes()));
            }

            final Class<?> proxyInterface = targetInterface;
            Object proxy = Proxy.newProxyInstance(
                    proxyInterface.getClassLoader(),
                    new Class<?>[]{ proxyInterface },
                    (proxyObj, method, args) -> {
                        // Skip Object methods (equals, hashCode, toString)
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "viaStyle-BlockBot-RelayProxy";
                                case "hashCode" -> System.identityHashCode(proxyObj);
                                case "equals" -> proxyObj == args[0];
                                default -> null;
                            };
                        }

                        // Extract the message and channel from the arguments.
                        // Modern: message(RelayMessageSender sender, String channel, String message)
                        // Legacy: message(String content, String channel)
                        if (args != null) {
                            String content = null;
                            String channel = null;
                            String senderName = null;

                            if (args.length >= 3 && args[1] instanceof String ch && args[2] instanceof String msg) {
                                // Modern signature: (RelayMessageSender, String, String)
                                channel = ch;
                                content = msg;
                                // Try to extract sender display name and JDA Guild from RelayMessageSender
                                try {
                                    Object sender = args[0];
                                    // Try getDisplayName() first, then name()
                                    for (String methodName : new String[]{"getDisplayName", "name", "getName"}) {
                                        try {
                                            Method nameMethod = sender.getClass().getMethod(methodName);
                                            Object name = nameMethod.invoke(sender);
                                            if (name instanceof String s && !s.isBlank()) {
                                                senderName = s;
                                                break;
                                            }
                                        } catch (NoSuchMethodException ignored) {}
                                    }
                                    // Populate the sender cache from this outgoing Discord message.
                                    populateSenderCache(sender);
                                } catch (Throwable ignored) {}
                            } else if (args.length >= 2 && args[0] instanceof String c && args[1] instanceof String ch) {
                                // Legacy signature: (String content, String channel)
                                content = c;
                                channel = ch;
                            }

                            if (content != null && channel != null) {
                                // If we have a sender name and the content doesn't already include it,
                                // prepend it to match BlockBot's expected format
                                if (senderName != null && !content.contains(senderName)) {
                                    content = senderName + ": " + content;
                                }
                                handleDiscordRelay(content, channel);
                            }
                        }

                        // Return ActionResult.PASS for modern API, null for legacy (void)
                        return actionResultPass;
                    }
            );

            // Invoke EVENT.register(proxy)
            // Retrieve register() via the public Fabric Event API class to avoid
            // Java module-access errors on the internal ArrayBackedEvent impl.
            boolean registered = false;
            try {
                Class<?> eventApiClass = Class.forName("net.fabricmc.fabric.api.event.Event");
                Method regMethod = eventApiClass.getMethod("register", Object.class);
                regMethod.invoke(event, proxy);
                registered = true;
                viaStyle.LOGGER.info("[viaStyle] Registered Discord=>MC relay listener.");
            } catch (Throwable ignored) {}

            if (!registered) {
                // Fallback: walk getMethods() with setAccessible to bypass module check
                for (Method m : event.getClass().getMethods()) {
                    if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                        try {
                            m.setAccessible(true);
                            m.invoke(event, proxy);
                            registered = true;
                            viaStyle.LOGGER.info("[viaStyle] Registered Discord=>MC relay listener (fallback).");
                        } catch (Throwable t2) {
                            viaStyle.LOGGER.warn("[viaStyle] register() fallback failed: {}", t2.getMessage());
                        }
                        break;
                    }
                }
            }
            if (!registered) {
                viaStyle.LOGGER.warn("[viaStyle] Could not find register() on RelayMessageEvent.EVENT.");
            }

        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] Discord→MC relay registration failed: {}", t.getMessage());
            viaStyle.LOGGER.debug("[viaStyle] Relay registration stacktrace:", t);
        }
    }

    /**
     * Strips / resolves Discord-specific markup so messages display cleanly in Minecraft.
     *
     * <ul>
     *   <li>{@code <@123>} / {@code <@!123>} — resolves to real display name via JDA guild
     *       (falls back to {@code @user} if guild unavailable)</li>
     *   <li>{@code <#123>} — channel mention → {@code #channel}</li>
     *   <li>{@code <@&123>} — role mention, resolved to name or {@code @role}</li>
     *   <li>{@code <:name:123>} / {@code <a:name:123>} — custom emoji → {@code :name:}</li>
     * </ul>
     */
    private static String sanitizeDiscordContent(String content) {
        if (content == null) return "";

        // User mentions  <@123> or <@!123>  — resolve to display name from our local cache
        java.util.regex.Matcher userMatcher =
            java.util.regex.Pattern.compile("<@!?(\\d+)>").matcher(content);
        StringBuilder userSb = new StringBuilder();
        while (userMatcher.find()) {
            String userId = userMatcher.group(1);
            String displayName = discordIdToDisplayCache.get(userId);
            userMatcher.appendReplacement(userSb, java.util.regex.Matcher.quoteReplacement(
                    "@" + (displayName != null ? displayName : "user")));
        }
        userMatcher.appendTail(userSb);
        content = userSb.toString();

        // Role / channel mentions — no JDA access, use readable placeholders
        content = content.replaceAll("<@&\\d+>", "@role");
        content = content.replaceAll("<#\\d+>", "#channel");

        // Custom emoji  <:name:123>  or animated  <a:name:123>
        content = content.replaceAll("<a?:([a-zA-Z0-9_]+):(\\d+)>", ":$1:");
        return content;
    }

    /**
     * Called when our custom Discord→MC relay listener fires (only active when
     * {@code discordPassthrough=false}).  BlockBot handles Discord→MC natively when
     * passthrough is enabled, so this method is never called in the default configuration.
     */
    private static void handleDiscordRelay(String content, String channel) {
        MinecraftServer server = cachedServer;
        if (server == null) return;

        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null) return;

        // "none" = relay explicitly disabled by user
        if ("none".equalsIgnoreCase(cfg.discordBridgeMode)) return;

        // Log which channel this message arrived on (helps diagnose channel name mismatches)
        viaStyle.LOGGER.debug("[viaStyle] Discord relay received: channel='{}' content='{}'", channel, content);

        // Strip Discord-specific markup (<@ID>, <#ID>, custom emoji, etc.)
        content = sanitizeDiscordContent(content);

        // Apply the configurable Discord format wrapper.
        String format = (cfg.discordFormat != null && !cfg.discordFormat.isBlank())
                ? cfg.discordFormat : "[Discord] {message}";
        String formatted = format
                .replace("{message}", content)
                .replace("{channel}", channel);
        Text relayText = Text.literal(formatted).formatted(Formatting.AQUA);

        // Capture finals for lambda
        final String finalContent = content;

        // The relay handler fires on BlockBot/JDA's Discord thread — broadcast()
        // must run on the server thread to safely iterate the player list.
        server.execute(() -> {
            server.getPlayerManager().broadcast(relayText, false);
            // Ping any @MCPlayerName mentions found in the Discord message
            if (cfg.discordMentionPing) {
                MentionHandler.processDiscordMentions(server, finalContent, null);
            }
        });
    }

    // ── Minecraft → Discord relay ──────────────────────────────────────────────

    /**
     * Returns {@code true} when BlockBot reflection initialised successfully
     * and the config does not explicitly disable the bridge ({@code "none"}).
     */
    public static boolean isAvailable() {
        if (!available || viaStyle.CONFIG == null) return false;
        String mode = viaStyle.CONFIG.discordBridgeMode;
        return mode == null || !mode.trim().equalsIgnoreCase("none");
    }

    /**
     * Relays a chat message to Discord via BlockBot.
     *
     * <p><b>Primary path:</b> fires {@code ChatMessageEvent.EVENT.invoker().message(...)}.
     * This is how BlockBot normally picks up chat — the Discord bot's listener on this
     * event sends the message to Discord.</p>
     *
     * <p><b>Fallback:</b> calls {@code BlockBotApi.sendRelayMessage(content, channel)} if
     * ChatMessageEvent is not available (may be a no-op in some BlockBot versions).</p>
     *
     * @param player  the sending player (used to construct PlayerMessageSender)
     * @param message the raw message text
     * @param channel BlockBot channel name
     */
    public static void relayToDiscord(ServerPlayerEntity player, String message, String channel) {
        if (!isAvailable()) return;

        String resolvedChannel = (channel != null && !channel.isBlank()) ? channel : defaultChatChannel;
        boolean isDefaultChannel = resolvedChannel.equalsIgnoreCase(defaultChatChannel);

        // ── Strategy ──────────────────────────────────────────────────────────
        // ChatMessageEvent  — proven to work in modern BlockBot, but BlockBot itself
        //   decides the target Discord channel (always its configured default).
        //   Use it ONLY when we are targeting the default/global channel.
        //
        // sendRelayMessage(content, channel) — explicitly routes to any named channel.
        //   Works in some BlockBot builds. Use it when we need a non-default channel
        //   (e.g. local chat → "local"), and fall back to ChatMessageEvent if unavailable.
        // ─────────────────────────────────────────────────────────────────────

        if (isDefaultChannel) {
            // ── Global / default channel → ChatMessageEvent (reliable) ────────
            if (chatEventObj != null && chatEventInvokerMethod != null
                    && chatEventMessageMethod != null && playerSenderCtor != null) {
                try {
                    Object playerSender = playerSenderCtor.newInstance(player, messageTypeRegular);
                    Object invoker = chatEventInvokerMethod.invoke(chatEventObj);
                    chatEventMessageMethod.invoke(invoker, playerSender, Text.literal(resolveDiscordMentions(message)));
                    viaStyle.LOGGER.debug("[viaStyle] ChatMessageEvent → default channel \"{}\" for <{}>.",
                            resolvedChannel, player.getName().getString());
                    return;
                } catch (Throwable t) {
                    viaStyle.LOGGER.warn("[viaStyle] ChatMessageEvent failed: {} => trying sendRelayMessage.", t.getMessage());
                }
            }
            // Fall through to sendRelayMessage if ChatMessageEvent unavailable
        }

        // ── Non-default channel (or ChatMessageEvent unavailable) → sendRelayMessage ──
        if (sendRelayMethod != null) {
            try {
                String content = "<" + player.getName().getString() + "> " + resolveDiscordMentions(message);
                sendRelayMethod.invoke(null, content, resolvedChannel);
                viaStyle.LOGGER.debug("[viaStyle] sendRelayMessage → channel \"{}\" for <{}>.",
                        resolvedChannel, player.getName().getString());
                return;
            } catch (Throwable t) {
                viaStyle.LOGGER.warn("[viaStyle] sendRelayMessage failed for channel \"{}\": {}", resolvedChannel, t.getMessage());
            }
        }

        // ── Last resort for non-default: ChatMessageEvent (wrong channel but better than nothing) ──
        if (!isDefaultChannel && chatEventObj != null && chatEventInvokerMethod != null
                && chatEventMessageMethod != null && playerSenderCtor != null) {
            try {
                Object playerSender = playerSenderCtor.newInstance(player, messageTypeRegular);
                Object invoker = chatEventInvokerMethod.invoke(chatEventObj);
                // Prepend channel label so Discord readers can distinguish local from global
                String labeledMessage = "[" + resolvedChannel + "] " + resolveDiscordMentions(message);
                chatEventMessageMethod.invoke(invoker, playerSender, Text.literal(labeledMessage));
                viaStyle.LOGGER.debug("[viaStyle] ChatMessageEvent fallback (labeled) → channel \"{}\" for <{}>.",
                        resolvedChannel, player.getName().getString());
            } catch (Throwable t) {
                viaStyle.LOGGER.error("[viaStyle] All relay methods failed for channel \"{}\": {}", resolvedChannel, t.getMessage());
            }
        }
    }

    public static String getDefaultChatChannel() {
        return defaultChatChannel;
    }

    /**
     * Scans {@code message} for {@code @Name} tokens and replaces matching
     * Discord guild member names with their proper Discord mention ({@code <@userId>}),
     * so that Discord renders them as real pings.
     *
     * <p>Priority:
     * <ol>
     *   <li>Config-defined mappings ({@code discordMentionMappings} field)</li>
     *   <li>Sender cache (populated from {@code RelayMessageSender} data in incoming Discord messages)</li>
     * </ol></p>
     */
    public static String resolveDiscordMentions(String message) {
        if (message == null || !message.contains("@")) return message;

        java.util.Map<String, Long> configMap = buildMentionMappings();

        if (configMap.isEmpty() && discordMemberCache.isEmpty()) {
            viaStyle.LOGGER.debug("[viaStyle] resolveDiscordMentions: cache empty — no Discord pings (someone must send a Discord message first).");
            return message;
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("@(\\w{1,32})")
                .matcher(message);

        StringBuilder sb = new StringBuilder();
        int last = 0;
        boolean anyReplaced = false;
        while (m.find()) {
            String name = m.group(1);
            String mention = null;

            // 1. Config map (manual mappings — highest priority)
            Long configId = configMap.get(name.toLowerCase());
            if (configId != null) mention = "<@" + configId + ">";

            // 2. Sender cache (populated from RelayMessageEvent data)
            if (mention == null) {
                String cachedId = discordMemberCache.get(name.toLowerCase());
                if (cachedId != null) mention = "<@" + cachedId + ">";
            }

            if (mention != null) {
                sb.append(message, last, m.start()).append(mention);
                last = m.end();
                anyReplaced = true;
                viaStyle.LOGGER.debug("[viaStyle] Resolved @{} → {} for Discord.", name, mention);
            }
        }
        if (!anyReplaced) return message;
        sb.append(message, last, message.length());
        return sb.toString();
    }

    /**
     * Parses {@code ViaStyleConfig.discordMentionMappings} into a lowercase-name → id map.
     * Format: {@code "Nick:123456789,OtherNick:987654321"}
     */
    private static java.util.Map<String, Long> buildMentionMappings() {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || cfg.discordMentionMappings == null || cfg.discordMentionMappings.isBlank())
            return java.util.Collections.emptyMap();
        java.util.Map<String, Long> map = new java.util.LinkedHashMap<>();
        for (String pair : cfg.discordMentionMappings.split(",")) {
            String[] parts = pair.trim().split(":", 2);
            if (parts.length != 2) continue;
            try {
                String mcName = parts[0].trim().toLowerCase();
                long discordId = Long.parseLong(parts[1].trim());
                if (!mcName.isEmpty()) map.put(mcName, discordId);
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }
}
