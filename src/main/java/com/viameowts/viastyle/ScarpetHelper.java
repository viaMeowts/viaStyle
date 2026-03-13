package com.viameowts.viastyle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based Carpet / Scarpet integration.
 * Fires the {@code PLAYER_MESSAGE} event so that Scarpet scripts can
 * react to viaStyle-managed messages.
 *
 * <p>The event object is resolved from
 * {@code carpet.script.CarpetEventServer$Event.PLAYER_MESSAGE}
 * (or the {@code carpet.api.script} variant in newer Carpet builds).</p>
 */
public final class ScarpetHelper {

    private static boolean available = false;
    private static Object playerMessageEvent;       // the Event constant
    private static Method onPlayerMessageMethod;     // method to fire the event

    private ScarpetHelper() {}

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("carpet")) {
            viaStyle.LOGGER.info("[viaStyle] Carpet not found => Scarpet integration disabled.");
            return;
        }
        try {
            // Try both package names (different Carpet versions)
            if (tryResolve("carpet.script.CarpetEventServer$Event")
                    || tryResolve("carpet.api.script.CarpetEventServer$Event")) {
                available = true;
                viaStyle.LOGGER.info("[viaStyle] Carpet/Scarpet detected \u2014 event integration enabled!");
            } else {
                viaStyle.LOGGER.warn("[viaStyle] Carpet found but PLAYER_MESSAGE event not accessible.");
            }
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] Scarpet init failed: {}", t.getMessage());
        }
    }

    private static boolean tryResolve(String eventClassName) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            Field pmField = eventClass.getField("PLAYER_MESSAGE");
            playerMessageEvent = pmField.get(null);
            if (playerMessageEvent == null) return false;

            // Look for onPlayerMessage(ServerPlayerEntity, String) first
            for (Method m : playerMessageEvent.getClass().getMethods()) {
                if (m.getName().equals("onPlayerMessage") && m.getParameterCount() == 2) {
                    onPlayerMessageMethod = m;
                    return true;
                }
            }

            // Fallback — look for any two-arg method accepting (entity, String)
            for (Method m : playerMessageEvent.getClass().getMethods()) {
                if (m.getParameterCount() == 2) {
                    Class<?>[] params = m.getParameterTypes();
                    if (ServerPlayerEntity.class.isAssignableFrom(params[0])
                            && params[1] == String.class) {
                        onPlayerMessageMethod = m;
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return available && viaStyle.CONFIG != null && viaStyle.CONFIG.useScarpetEvents;
    }

    /**
     * Fires the Scarpet {@code PLAYER_MESSAGE} event so that Scarpet scripts
     * can handle it. Does nothing when Carpet is absent or the feature is
     * disabled in the config.
     */
    public static void firePlayerMessage(ServerPlayerEntity player, String message) {
        if (!isAvailable()) return;
        try {
            onPlayerMessageMethod.invoke(playerMessageEvent, player, message);
        } catch (Throwable t) {
            viaStyle.LOGGER.error("[viaStyle] Scarpet event fire error: {}", t.getMessage());
        }
    }
}
