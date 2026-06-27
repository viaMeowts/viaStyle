package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.JoinLeaveManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class JoinLeaveCommand {

    private JoinLeaveCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("joinleave")
            .requires(source -> canUseSelf(source) || canUseAdmin(source))
            .then(CommandManager.literal("show")
                .requires(JoinLeaveCommand::canUseSelf)
                        .executes(JoinLeaveCommand::showSelf))
                .then(CommandManager.literal("set")
                .requires(JoinLeaveCommand::canUseSelf)
                        .then(CommandManager.literal("join")
                                .then(CommandManager.argument("format", StringArgumentType.greedyString())
                                        .executes(ctx -> setSelf(ctx, true))))
                        .then(CommandManager.literal("leave")
                                .then(CommandManager.argument("format", StringArgumentType.greedyString())
                                        .executes(ctx -> setSelf(ctx, false)))))
                .then(CommandManager.literal("remove")
                .requires(JoinLeaveCommand::canUseSelf)
                        .then(CommandManager.literal("join")
                                .executes(ctx -> removeSelf(ctx, "join")))
                        .then(CommandManager.literal("leave")
                                .executes(ctx -> removeSelf(ctx, "leave")))
                        .then(CommandManager.literal("all")
                    .executes(ctx -> removeSelf(ctx, "all"))))
            .then(CommandManager.literal("admin")
                .requires(JoinLeaveCommand::canUseAdmin)
                .then(CommandManager.literal("show")
                    .then(CommandManager.literal("player")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> showPlayer(ctx, StringArgumentType.getString(ctx, "player")))))
                    .then(CommandManager.literal("group")
                        .then(CommandManager.argument("group", StringArgumentType.word())
                            .executes(ctx -> showGroup(ctx, StringArgumentType.getString(ctx, "group"))))))
                .then(CommandManager.literal("set")
                    .then(CommandManager.literal("player")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.literal("join")
                                .then(CommandManager.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setPlayer(ctx,
                                        StringArgumentType.getString(ctx, "player"), true))))
                            .then(CommandManager.literal("leave")
                                .then(CommandManager.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setPlayer(ctx,
                                        StringArgumentType.getString(ctx, "player"), false))))))
                    .then(CommandManager.literal("group")
                        .then(CommandManager.argument("group", StringArgumentType.word())
                            .then(CommandManager.literal("join")
                                .then(CommandManager.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setGroup(ctx,
                                        StringArgumentType.getString(ctx, "group"), true))))
                            .then(CommandManager.literal("leave")
                                .then(CommandManager.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setGroup(ctx,
                                        StringArgumentType.getString(ctx, "group"), false)))))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.literal("player")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.literal("join")
                                .executes(ctx -> removePlayer(ctx,
                                    StringArgumentType.getString(ctx, "player"), "join")))
                            .then(CommandManager.literal("leave")
                                .executes(ctx -> removePlayer(ctx,
                                    StringArgumentType.getString(ctx, "player"), "leave")))
                            .then(CommandManager.literal("all")
                                .executes(ctx -> removePlayer(ctx,
                                    StringArgumentType.getString(ctx, "player"), "all")))))
                    .then(CommandManager.literal("group")
                        .then(CommandManager.argument("group", StringArgumentType.word())
                            .then(CommandManager.literal("join")
                                .executes(ctx -> removeGroup(ctx,
                                    StringArgumentType.getString(ctx, "group"), "join")))
                            .then(CommandManager.literal("leave")
                                .executes(ctx -> removeGroup(ctx,
                                    StringArgumentType.getString(ctx, "group"), "leave")))
                            .then(CommandManager.literal("all")
                                .executes(ctx -> removeGroup(ctx,
                                    StringArgumentType.getString(ctx, "group"), "all")))))))
        );
    }

    private static int showSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        JoinLeaveManager.MessagePair pair = JoinLeaveManager.getUser(player.getUuid());
        String defaultMarker = Lang.get("joinleave.self.default_marker").getString();
        String join = pair != null && pair.join != null && !pair.join.isBlank() ? pair.join : defaultMarker;
        String leave = pair != null && pair.leave != null && !pair.leave.isBlank() ? pair.leave : defaultMarker;

        MutableText joinLine = Lang.getMutable("joinleave.self.show_join")
            .append(Text.literal(join).styled(s -> s.withColor(Lang.colorNormal())));
        MutableText leaveLine = Lang.getMutable("joinleave.self.show_leave")
            .append(Text.literal(leave).styled(s -> s.withColor(Lang.colorNormal())));
        ctx.getSource().sendFeedback(() -> joinLine, false);
        ctx.getSource().sendFeedback(() -> leaveLine, false);
        return 1;
    }

    private static int setSelf(CommandContext<ServerCommandSource> ctx, boolean join) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String format = StringArgumentType.getString(ctx, "format");
        if (join) {
            JoinLeaveManager.setUserJoin(player.getUuid(), format);
            ctx.getSource().sendFeedback(() -> Lang.get("joinleave.self.saved_join"), false);
        } else {
            JoinLeaveManager.setUserLeave(player.getUuid(), format);
            ctx.getSource().sendFeedback(() -> Lang.get("joinleave.self.saved_leave"), false);
        }
        return 1;
    }

    private static int removeSelf(CommandContext<ServerCommandSource> ctx, String mode) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        switch (mode) {
            case "join" -> JoinLeaveManager.removeUserJoin(player.getUuid());
            case "leave" -> JoinLeaveManager.removeUserLeave(player.getUuid());
            default -> JoinLeaveManager.removeUser(player.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Lang.get("joinleave.self.updated"), false);
        return 1;
    }

    private static int showPlayer(CommandContext<ServerCommandSource> ctx, String playerName) {
        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            ctx.getSource().sendError(Lang.get("joinleave.admin.player_not_found"));
            return 0;
        }

        JoinLeaveManager.MessagePair pair = JoinLeaveManager.getUser(target.getUuid());
        String defaultMarker = Lang.get("joinleave.self.default_marker").getString();
        String join = pair != null && pair.join != null && !pair.join.isBlank() ? pair.join : defaultMarker;
        String leave = pair != null && pair.leave != null && !pair.leave.isBlank() ? pair.leave : defaultMarker;

        MutableText joinLine = Lang.getMutable("joinleave.admin.show_player_join")
                .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(Lang.colorMid())))
                .append(Text.literal(": ").styled(s -> s.withColor(Lang.colorNormal())))
                .append(Text.literal(join).styled(s -> s.withColor(Lang.colorNormal())));

        MutableText leaveLine = Lang.getMutable("joinleave.admin.show_player_leave")
                .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(Lang.colorMid())))
                .append(Text.literal(": ").styled(s -> s.withColor(Lang.colorNormal())))
                .append(Text.literal(leave).styled(s -> s.withColor(Lang.colorNormal())));

        ctx.getSource().sendFeedback(() -> joinLine, false);
        ctx.getSource().sendFeedback(() -> leaveLine, false);
        return 1;
    }

    private static int showGroup(CommandContext<ServerCommandSource> ctx, String groupName) {
        JoinLeaveManager.MessagePair pair = JoinLeaveManager.getGroups().get(groupName.toLowerCase(java.util.Locale.ROOT));
        String defaultMarker = Lang.get("joinleave.self.default_marker").getString();
        String join = pair != null && pair.join != null && !pair.join.isBlank() ? pair.join : defaultMarker;
        String leave = pair != null && pair.leave != null && !pair.leave.isBlank() ? pair.leave : defaultMarker;

        MutableText joinLine = Lang.getMutable("joinleave.admin.show_group_join")
                .append(Text.literal(groupName).styled(s -> s.withColor(Lang.colorMid())))
                .append(Text.literal(": ").styled(s -> s.withColor(Lang.colorNormal())))
                .append(Text.literal(join).styled(s -> s.withColor(Lang.colorNormal())));

        MutableText leaveLine = Lang.getMutable("joinleave.admin.show_group_leave")
                .append(Text.literal(groupName).styled(s -> s.withColor(Lang.colorMid())))
                .append(Text.literal(": ").styled(s -> s.withColor(Lang.colorNormal())))
                .append(Text.literal(leave).styled(s -> s.withColor(Lang.colorNormal())));

        ctx.getSource().sendFeedback(() -> joinLine, false);
        ctx.getSource().sendFeedback(() -> leaveLine, false);
        return 1;
    }

    private static int setPlayer(CommandContext<ServerCommandSource> ctx, String playerName, boolean join) {
        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            ctx.getSource().sendError(Lang.get("joinleave.admin.player_not_found"));
            return 0;
        }
        String format = StringArgumentType.getString(ctx, "format");
        if (join) {
            JoinLeaveManager.setUserJoin(target.getUuid(), format);
            ctx.getSource().sendFeedback(() -> Lang.getMutable("joinleave.admin.saved_player_join")
                    .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(Lang.colorMid()))), false);
        } else {
            JoinLeaveManager.setUserLeave(target.getUuid(), format);
            ctx.getSource().sendFeedback(() -> Lang.getMutable("joinleave.admin.saved_player_leave")
                    .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(Lang.colorMid()))), false);
        }
        return 1;
    }

    private static int setGroup(CommandContext<ServerCommandSource> ctx, String groupName, boolean join) {
        String format = StringArgumentType.getString(ctx, "format");
        if (join) {
            JoinLeaveManager.setGroupJoin(groupName, format);
            ctx.getSource().sendFeedback(() -> Lang.getMutable("joinleave.admin.saved_group_join")
                    .append(Text.literal(groupName).styled(s -> s.withColor(Lang.colorMid()))), false);
        } else {
            JoinLeaveManager.setGroupLeave(groupName, format);
            ctx.getSource().sendFeedback(() -> Lang.getMutable("joinleave.admin.saved_group_leave")
                    .append(Text.literal(groupName).styled(s -> s.withColor(Lang.colorMid()))), false);
        }
        return 1;
    }

    private static int removePlayer(CommandContext<ServerCommandSource> ctx, String playerName, String mode) {
        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            ctx.getSource().sendError(Lang.get("joinleave.admin.player_not_found"));
            return 0;
        }

        switch (mode) {
            case "join" -> JoinLeaveManager.removeUserJoin(target.getUuid());
            case "leave" -> JoinLeaveManager.removeUserLeave(target.getUuid());
            default -> JoinLeaveManager.removeUser(target.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Lang.getMutable("joinleave.admin.removed_player")
                .append(Text.literal(target.getName().getString()).styled(s -> s.withColor(Lang.colorMid()))), false);
        return 1;
    }

    private static int removeGroup(CommandContext<ServerCommandSource> ctx, String groupName, String mode) {
        switch (mode) {
            case "join" -> JoinLeaveManager.removeGroupJoin(groupName);
            case "leave" -> JoinLeaveManager.removeGroupLeave(groupName);
            default -> JoinLeaveManager.removeGroup(groupName);
        }

        ctx.getSource().sendFeedback(() -> Lang.getMutable("joinleave.admin.removed_group")
                .append(Text.literal(groupName).styled(s -> s.withColor(Lang.colorMid()))), false);
        return 1;
    }

    private static boolean canUseSelf(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return false;

        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.joinLeavePerPlayerEnabled) return false;

        String node = cfg.joinLeaveSelfPermission;
        if (node == null || node.isBlank()) {
            return LuckPermsHelper.hasOpLevel(source, 2);
        }
        return LuckPermsHelper.checkPermission(source, node, 2);
    }

    private static boolean canUseAdmin(ServerCommandSource source) {
        return LuckPermsHelper.checkPermission(source, "viastyle.joinleave.admin", 2);
    }
}
