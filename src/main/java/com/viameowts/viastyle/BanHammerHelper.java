package com.viameowts.viastyle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * Reflection-based BanHammer integration.
 * Checks if a player is muted before allowing chat messages.
 *
 * <p>Tries multiple known API patterns to maximise compatibility across
 * BanHammer versions. If none are found the integration is silently disabled.</p>
 */
public final class BanHammerHelper {

    private static boolean available = false;
    private static Method muteCheckMethod;
    private static Object[] extraArgs = new Object[0];
    private static boolean needsGameProfile = false;

    private BanHammerHelper() {}

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("banhammer")) {
            viaStyle.LOGGER.info("[viaStyle] BanHammer not found => mute integration disabled.");
            return;
        }
        try {
            available = resolve();
            if (available) {
                viaStyle.LOGGER.info("[viaStyle] BanHammer detected => mute integration enabled!");
            } else {
                viaStyle.LOGGER.warn("[viaStyle] BanHammer found but no compatible API detected => mute integration disabled.");
            }
        } catch (Throwable t) {
            viaStyle.LOGGER.warn("[viaStyle] BanHammer init failed: {}", t.getMessage());
        }
    }

    // ── API resolution ─────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean resolve() {
        // Pattern A: BanHammer.isPunished(UUID, PunishmentType)
        try {
            Class<?> api = Class.forName("eu.pb4.banhammer.api.BanHammer");
            Class<?> pt  = Class.forName("eu.pb4.banhammer.api.PunishmentType");
            Object muteEnum = Enum.valueOf((Class<Enum>) pt, "MUTE");
            muteCheckMethod = api.getMethod("isPunished", UUID.class, pt);
            extraArgs = new Object[]{ muteEnum };
            return true;
        } catch (Throwable ignored) {}

        // Pattern B: BanHammerApi.isMuted(GameProfile)
        try {
            Class<?> api = Class.forName("eu.pb4.banhammer.api.BanHammerApi");
            Class<?> gp  = Class.forName("com.mojang.authlib.GameProfile");
            muteCheckMethod = api.getMethod("isMuted", gp);
            needsGameProfile = true;
            return true;
        } catch (Throwable ignored) {}

        // Pattern C: scan known class names for a public static method whose name
        //            contains "mute" and takes a single UUID or GameProfile argument.
        for (String className : new String[]{
                "eu.pb4.banhammer.api.BanHammer",
                "eu.pb4.banhammer.api.BanHammerApi",
                "eu.pb4.banhammer.api.PunishmentChecker"
        }) {
            try {
                Class<?> cls = Class.forName(className);
                for (Method m : cls.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers())) continue;
                    if (!m.getName().toLowerCase().contains("mute")) continue;
                    if (m.getParameterCount() != 1) continue;

                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType == UUID.class) {
                        muteCheckMethod = m;
                        return true;
                    }
                    if (paramType.getSimpleName().equals("GameProfile")) {
                        muteCheckMethod = m;
                        needsGameProfile = true;
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public static boolean isAvailable() {
        return available && viaStyle.CONFIG != null && viaStyle.CONFIG.useBanHammer;
    }

    /**
     * Returns {@code true} when the player has an active mute in BanHammer.
     * Returns {@code false} if BanHammer is absent, disabled, or the check fails.
     */
    public static boolean isMuted(ServerPlayerEntity player) {
        if (!isAvailable()) return false;
        try {
            Object arg = needsGameProfile ? player.getGameProfile() : player.getUuid();
            Object result;
            if (extraArgs.length > 0) {
                result = muteCheckMethod.invoke(null, arg, extraArgs[0]);
            } else {
                result = muteCheckMethod.invoke(null, arg);
            }
            if (result instanceof Boolean b) return b;
            return result != null; // non-null object = punishment exists
        } catch (Throwable t) {
            viaStyle.LOGGER.error("[viaStyle] BanHammer mute check failed: {}", t.getMessage());
            return false;
        }
    }
}
