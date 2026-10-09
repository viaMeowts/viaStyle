package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.JoinLeaveManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class JoinLeaveCommand {

    private JoinLeaveCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("joinleave")
            .requires(source -> canUseSelf(source) || canUseAdmin(source))
            .then(Commands.literal("show")
                .requires(JoinLeaveCommand::canUseSelf)
                        .executes(JoinLeaveCommand::showSelf))
                .then(Commands.literal("set")
                .requires(JoinLeaveCommand::canUseSelf)
                        .then(Commands.literal("join")
                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                        .executes(ctx -> setSelf(ctx, true))))
                        .then(Commands.literal("leave")
                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                        .executes(ctx -> setSelf(ctx, false)))))
                .then(Commands.literal("remove")
                .requires(JoinLeaveCommand::canUseSelf)
                        .then(Commands.literal("join")
                                .executes(ctx -> removeSelf(ctx, "join")))
                        .then(Commands.literal("leave")
                                .executes(ctx -> removeSelf(ctx, "leave")))
                        .then(Commands.literal("all")
                    .executes(ctx -> removeSelf(ctx, "all"))))
            .then(Commands.literal("admin")
                .requires(JoinLeaveCommand::canUseAdmin)
                .then(Commands.literal("show")
                    .then(Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> showPlayer(ctx, StringArgumentType.getString(ctx, "player")))))
                    .then(Commands.literal("group")
                        .then(Commands.argument("group", StringArgumentType.word())
                            .executes(ctx -> showGroup(ctx, StringArgumentType.getString(ctx, "group"))))))
                .then(Commands.literal("set")
                    .then(Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.literal("join")
                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setPlayer(ctx,
                                        StringArgumentType.getString(ctx, "player"), true))))
                            .then(Commands.literal("leave")
                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setPlayer(ctx,
                                        StringArgumentType.getString(ctx, "player"), false))))))
                    .then(Commands.literal("group")
                        .then(Commands.argument("group", StringArgumentType.word())
                            .then(Commands.literal("join")
                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setGroup(ctx,
                                        StringArgumentType.getString(ctx, "group"), true))))
                            .then(Commands.literal("leave")
                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                    .executes(ctx -> setGroup(ctx,
                                        StringArgumentType.getString(ctx, "group"), false)))))))
                .then(Commands.literal("remove")
                    .then(Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.literal("join")
                                .executes(ctx -> removePlayer(ctx,
                                    StringArgumentType.getString(ctx, "player"), "join")))
                            .then(Commands.literal("leave")
                                .executes(ctx -> removePlayer(ctx,
                                    StringArgumentType.getString(ctx, "player"), "leave")))
                            .then(Commands.literal("all")
                                .executes(ctx -> removePlayer(ctx,
                                    StringArgumentType.getString(ctx, "player"), "all")))))
                    .then(Commands.literal("group")
                        .then(Commands.argument("group", StringArgumentType.word())
                            .then(Commands.literal("join")
                                .executes(ctx -> removeGroup(ctx,
                                    StringArgumentType.getString(ctx, "group"), "join")))
                            .then(Commands.literal("leave")
                                .executes(ctx -> removeGroup(ctx,
                                    StringArgumentType.getString(ctx, "group"), "leave")))
                            .then(Commands.literal("all")
                                .executes(ctx -> removeGroup(ctx,
                                    StringArgumentType.getString(ctx, "group"), "all")))))))
        );
    }

    private static int showSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        JoinLeaveManager.MessagePair pair = JoinLeaveManager.getUser(player.getUUID());
        String defaultMarker = Lang.get("joinleave.self.default_marker").getString();
        String join = pair != null && pair.join != null && !pair.join.isBlank() ? pair.join : defaultMarker;
        String leave = pair != null && pair.leave != null && !pair.leave.isBlank() ? pair.leave : defaultMarker;

        MutableComponent joinLine = Lang.getMutable("joinleave.self.show_join")
            .append(Component.literal(join).withStyle(s -> s.withColor(Lang.colorNormal())));
        MutableComponent leaveLine = Lang.getMutable("joinleave.self.show_leave")
            .append(Component.literal(leave).withStyle(s -> s.withColor(Lang.colorNormal())));
        ctx.getSource().sendSuccess(() -> joinLine, false);
        ctx.getSource().sendSuccess(() -> leaveLine, false);
        return 1;
    }

    private static int setSelf(CommandContext<CommandSourceStack> ctx, boolean join) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String format = StringArgumentType.getString(ctx, "format");
        if (join) {
            JoinLeaveManager.setUserJoin(player.getUUID(), format);
            ctx.getSource().sendSuccess(() -> Lang.get("joinleave.self.saved_join"), false);
        } else {
            JoinLeaveManager.setUserLeave(player.getUUID(), format);
            ctx.getSource().sendSuccess(() -> Lang.get("joinleave.self.saved_leave"), false);
        }
        return 1;
    }

    private static int removeSelf(CommandContext<CommandSourceStack> ctx, String mode) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        switch (mode) {
            case "join" -> JoinLeaveManager.removeUserJoin(player.getUUID());
            case "leave" -> JoinLeaveManager.removeUserLeave(player.getUUID());
            default -> JoinLeaveManager.removeUser(player.getUUID());
        }

        ctx.getSource().sendSuccess(() -> Lang.get("joinleave.self.updated"), false);
        return 1;
    }

    private static int showPlayer(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            ctx.getSource().sendFailure(Lang.get("joinleave.admin.player_not_found"));
            return 0;
        }

        JoinLeaveManager.MessagePair pair = JoinLeaveManager.getUser(target.getUUID());
        String defaultMarker = Lang.get("joinleave.self.default_marker").getString();
        String join = pair != null && pair.join != null && !pair.join.isBlank() ? pair.join : defaultMarker;
        String leave = pair != null && pair.leave != null && !pair.leave.isBlank() ? pair.leave : defaultMarker;

        MutableComponent joinLine = Lang.getMutable("joinleave.admin.show_player_join")
                .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(Lang.colorMid())))
                .append(Component.literal(": ").withStyle(s -> s.withColor(Lang.colorNormal())))
                .append(Component.literal(join).withStyle(s -> s.withColor(Lang.colorNormal())));

        MutableComponent leaveLine = Lang.getMutable("joinleave.admin.show_player_leave")
                .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(Lang.colorMid())))
                .append(Component.literal(": ").withStyle(s -> s.withColor(Lang.colorNormal())))
                .append(Component.literal(leave).withStyle(s -> s.withColor(Lang.colorNormal())));

        ctx.getSource().sendSuccess(() -> joinLine, false);
        ctx.getSource().sendSuccess(() -> leaveLine, false);
        return 1;
    }

    private static int showGroup(CommandContext<CommandSourceStack> ctx, String groupName) {
        JoinLeaveManager.MessagePair pair = JoinLeaveManager.getGroups().get(groupName.toLowerCase(java.util.Locale.ROOT));
        String defaultMarker = Lang.get("joinleave.self.default_marker").getString();
        String join = pair != null && pair.join != null && !pair.join.isBlank() ? pair.join : defaultMarker;
        String leave = pair != null && pair.leave != null && !pair.leave.isBlank() ? pair.leave : defaultMarker;

        MutableComponent joinLine = Lang.getMutable("joinleave.admin.show_group_join")
                .append(Component.literal(groupName).withStyle(s -> s.withColor(Lang.colorMid())))
                .append(Component.literal(": ").withStyle(s -> s.withColor(Lang.colorNormal())))
                .append(Component.literal(join).withStyle(s -> s.withColor(Lang.colorNormal())));

        MutableComponent leaveLine = Lang.getMutable("joinleave.admin.show_group_leave")
                .append(Component.literal(groupName).withStyle(s -> s.withColor(Lang.colorMid())))
                .append(Component.literal(": ").withStyle(s -> s.withColor(Lang.colorNormal())))
                .append(Component.literal(leave).withStyle(s -> s.withColor(Lang.colorNormal())));

        ctx.getSource().sendSuccess(() -> joinLine, false);
        ctx.getSource().sendSuccess(() -> leaveLine, false);
        return 1;
    }

    private static int setPlayer(CommandContext<CommandSourceStack> ctx, String playerName, boolean join) {
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            ctx.getSource().sendFailure(Lang.get("joinleave.admin.player_not_found"));
            return 0;
        }
        String format = StringArgumentType.getString(ctx, "format");
        com.viameowts.viastyle.MeridianaAudit.action(ctx.getSource().getTextName(), "/joinleave admin set: игрок " + target.getName().getString() + (join ? " вход " : " выход ") + format);
        if (join) {
            JoinLeaveManager.setUserJoin(target.getUUID(), format);
            ctx.getSource().sendSuccess(() -> Lang.getMutable("joinleave.admin.saved_player_join")
                    .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(Lang.colorMid()))), false);
        } else {
            JoinLeaveManager.setUserLeave(target.getUUID(), format);
            ctx.getSource().sendSuccess(() -> Lang.getMutable("joinleave.admin.saved_player_leave")
                    .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(Lang.colorMid()))), false);
        }
        return 1;
    }

    private static int setGroup(CommandContext<CommandSourceStack> ctx, String groupName, boolean join) {
        String format = StringArgumentType.getString(ctx, "format");
        com.viameowts.viastyle.MeridianaAudit.action(ctx.getSource().getTextName(), "/joinleave admin set: группа " + groupName + (join ? " вход " : " выход ") + format);
        if (join) {
            JoinLeaveManager.setGroupJoin(groupName, format);
            ctx.getSource().sendSuccess(() -> Lang.getMutable("joinleave.admin.saved_group_join")
                    .append(Component.literal(groupName).withStyle(s -> s.withColor(Lang.colorMid()))), false);
        } else {
            JoinLeaveManager.setGroupLeave(groupName, format);
            ctx.getSource().sendSuccess(() -> Lang.getMutable("joinleave.admin.saved_group_leave")
                    .append(Component.literal(groupName).withStyle(s -> s.withColor(Lang.colorMid()))), false);
        }
        return 1;
    }

    private static int removePlayer(CommandContext<CommandSourceStack> ctx, String playerName, String mode) {
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            ctx.getSource().sendFailure(Lang.get("joinleave.admin.player_not_found"));
            return 0;
        }

        com.viameowts.viastyle.MeridianaAudit.action(ctx.getSource().getTextName(), "/joinleave admin remove: игрок " + target.getName().getString() + " " + mode);
        switch (mode) {
            case "join" -> JoinLeaveManager.removeUserJoin(target.getUUID());
            case "leave" -> JoinLeaveManager.removeUserLeave(target.getUUID());
            default -> JoinLeaveManager.removeUser(target.getUUID());
        }

        ctx.getSource().sendSuccess(() -> Lang.getMutable("joinleave.admin.removed_player")
                .append(Component.literal(target.getName().getString()).withStyle(s -> s.withColor(Lang.colorMid()))), false);
        return 1;
    }

    private static int removeGroup(CommandContext<CommandSourceStack> ctx, String groupName, String mode) {
        com.viameowts.viastyle.MeridianaAudit.action(ctx.getSource().getTextName(), "/joinleave admin remove: группа " + groupName + " " + mode);
        switch (mode) {
            case "join" -> JoinLeaveManager.removeGroupJoin(groupName);
            case "leave" -> JoinLeaveManager.removeGroupLeave(groupName);
            default -> JoinLeaveManager.removeGroup(groupName);
        }

        ctx.getSource().sendSuccess(() -> Lang.getMutable("joinleave.admin.removed_group")
                .append(Component.literal(groupName).withStyle(s -> s.withColor(Lang.colorMid()))), false);
        return 1;
    }

    private static boolean canUseSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;

        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.joinLeavePerPlayerEnabled) return false;

        // an empty key means the default node, so that LuckPerms still decides
        String node = cfg.joinLeaveSelfPermission;
        if (node == null || node.isBlank()) {
            node = "viastyle.joinleave.self";
        }
        return LuckPermsHelper.checkPermission(source, node, 2);
    }

    private static boolean canUseAdmin(CommandSourceStack source) {
        return LuckPermsHelper.checkPermission(source, "viastyle.joinleave.admin", 2);
    }
}
