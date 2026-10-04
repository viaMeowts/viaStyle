package com.viameowts.viastyle;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.util.Tristate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Direct LuckPerms API calls. This class is the only one that references {@code net.luckperms.api}
 * and is only ever loaded by {@link LuckPermsHelper} after the luckperms mod was detected, so the
 * mod keeps working without LuckPerms installed.
 */
final class LuckPermsBridge {

    private LuckPermsBridge() {}

    static void probe() {
        LuckPermsProvider.get();
    }

    private static User user(UUID uuid) {
        return LuckPermsProvider.get().getUserManager().getUser(uuid);
    }

    static LuckPermsHelper.Info info(UUID uuid) {
        User user = user(uuid);
        if (user == null) return LuckPermsHelper.Info.EMPTY;
        CachedMetaData meta = user.getCachedData().getMetaData();
        String prefix = meta.getPrefix();
        String suffix = meta.getSuffix();
        String primary = user.getPrimaryGroup();
        String groupName = primary;
        Group primaryGroup = primary == null ? null : LuckPermsProvider.get().getGroupManager().getGroup(primary);
        if (primaryGroup != null) {
            String display = primaryGroup.getDisplayName();
            if (display != null && !display.isBlank()) groupName = display;
        }
        return new LuckPermsHelper.Info(
                prefix == null ? "" : prefix,
                suffix == null ? "" : suffix,
                primary == null ? "" : primary,
                groupName == null ? "" : groupName,
                weight(user, meta, primaryGroup));
    }

    /**
     * Sort weight: explicit {@code weight} meta first, then the heaviest group the player is
     * directly in (so {@code admin} wins even when the primary group is {@code default}),
     * then the primary group's weight.
     */
    private static int weight(User user, CachedMetaData meta, Group primaryGroup) {
        String metaWeight = meta.getMetaValue("weight");
        if (metaWeight != null) {
            try {
                return Integer.parseInt(metaWeight.trim());
            } catch (NumberFormatException ignored) {}
        }
        int max = Integer.MIN_VALUE;
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (!node.getValue()) continue;
            Group group = LuckPermsProvider.get().getGroupManager().getGroup(node.getGroupName());
            if (group == null) continue;
            OptionalInt w = group.getWeight();
            if (w.isPresent()) max = Math.max(max, w.getAsInt());
        }
        if (max != Integer.MIN_VALUE) return max;
        if (primaryGroup != null) {
            OptionalInt w = primaryGroup.getWeight();
            if (w.isPresent()) return w.getAsInt();
        }
        return 0;
    }

    static String metaValue(UUID uuid, String key) {
        User user = user(uuid);
        return user == null ? null : user.getCachedData().getMetaData().getMetaValue(key);
    }

    /** Resolved permission value: 1 allowed, -1 explicitly denied, 0 undefined or no user data. */
    static int check(UUID uuid, String permission) {
        User user = user(uuid);
        if (user == null) return 0;
        Tristate t = user.getCachedData().getPermissionData().checkPermission(permission);
        return t == Tristate.TRUE ? 1 : t == Tristate.FALSE ? -1 : 0;
    }

    /** Value of a node set directly on the player (not inherited), or null. */
    static Boolean direct(UUID uuid, String permission, String legacy) {
        User user = user(uuid);
        if (user == null) return null;
        for (Node node : user.getNodes(NodeType.PERMISSION)) {
            String key = node.getKey();
            if (key.equals(permission) || (legacy != null && key.equals(legacy))) return node.getValue();
        }
        return null;
    }

    static Map<String, Boolean> permissionMap(UUID uuid) {
        User user = user(uuid);
        return user == null ? null : user.getCachedData().getPermissionData().getPermissionMap();
    }

    static List<String> groups(UUID uuid) {
        List<String> groups = new ArrayList<>();
        User user = user(uuid);
        if (user == null) return groups;
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (node.getValue()) groups.add(node.getGroupName());
        }
        if (groups.isEmpty() && user.getPrimaryGroup() != null) groups.add(user.getPrimaryGroup());
        return groups;
    }

    static CompletableFuture<?> load(UUID uuid) {
        return LuckPermsProvider.get().getUserManager().loadUser(uuid);
    }

    static void subscribe(Consumer<UUID> onRecalculate) {
        LuckPerms api = LuckPermsProvider.get();
        api.getEventBus().subscribe(UserDataRecalculateEvent.class,
                event -> onRecalculate.accept(event.getUser().getUniqueId()));
    }
}
