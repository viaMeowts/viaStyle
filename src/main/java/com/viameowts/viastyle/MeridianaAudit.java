package com.viameowts.viastyle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Hands this mod's admin actions and health checks to the central audit of
 * meridiana-core ({@code /аудит}). Soft link by reflection: without
 * meridiana-core every call does nothing, and this mod needs no build-time
 * dependency on it. Contract: dev.meridiana.core.audit.AuditApi.
 */
public final class MeridianaAudit {

    private static final String SOURCE = "viastyle";
    private static MethodHandle event;
    private static MethodHandle check;
    private static boolean resolved;

    private MeridianaAudit() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("meridiana")) {
            return;
        }
        try {
            Class<?> api = Class.forName("dev.meridiana.core.audit.AuditApi", false, MeridianaAudit.class.getClassLoader());
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            event = l.findStatic(api, "event", MethodType.methodType(void.class, String.class, String.class, String.class, String.class, String.class));
            check = l.findStatic(api, "check", MethodType.methodType(void.class, String.class, String.class, String.class, Supplier.class));
        } catch (Throwable t) {
            event = null;
            check = null;
        }
    }

    /** One entry in the central journal. level: INFO, WARN or ALERT; kind: usually "action". */
    public static void event(String who, String kind, String level, String what) {
        resolve();
        if (event == null) {
            return;
        }
        try {
            event.invoke(SOURCE, who, kind, level, what);
        } catch (Throwable ignored) {
            // the audit must never break the mod
        }
    }

    /** A routine admin action. */
    public static void action(String who, String what) {
        event(who, "action", "INFO", what);
    }

    /** Registers a check for {@code /аудит проверить}; lines may start with WARN: or ALERT:, empty list = all well. */
    public static void check(String id, String title, Supplier<List<String>> run) {
        resolve();
        if (check == null) {
            return;
        }
        try {
            check.invoke(SOURCE, id, title, run);
        } catch (Throwable ignored) {
            // the audit must never break the mod
        }
    }
}
