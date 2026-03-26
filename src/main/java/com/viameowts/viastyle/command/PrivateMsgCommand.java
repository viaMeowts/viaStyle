package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.IgnoreManager;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.SocialSpyManager;
import com.viameowts.viastyle.ChatHandler;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.VanishHelper;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /msg <player> <message>  — send a private message
 * /reply <message>          — reply to the last sender
 *
 * Format strings are read from ViaStyleConfig and support tokens:
 *   {sender}, {receiver}, {message}
 */
public class PrivateMsgCommand {

    /**
     * Maps each player's UUID to the UUID of the last person who messaged them.
     * Used by /reply.
     */
    private static final Map<UUID, UUID> lastMsgFrom = new ConcurrentHashMap<>();

    /**
     * Cleans up per-player state when a player disconnects.
     * Prevents unbounded map growth on long-running servers.
     */
    public static void clearPlayer(UUID uuid) {
        lastMsgFrom.remove(uuid);
        lastMsgFrom.values().removeIf(v -> v.equals(uuid));
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        // Remove vanilla msg/tell/w/reply nodes so our version takes over
        dispatcher.getRoot().getChildren().removeIf(node -> {
            String n = node.getName();
            return n.equals("msg") || n.equals("tell") || n.equals("w")
                    || n.equals("reply") || n.equals("m") || n.equals("r");
        });

        // /msg <player> <message>  (also registered as /m and /w)
        var msgNode = CommandManager.literal("msg")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.msg"))
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            ServerPlayerEntity sender =
                                    ctx.getSource().getEntity() instanceof ServerPlayerEntity sp ? sp : null;
                            for (ServerPlayerEntity p : ctx.getSource().getServer()
                                    .getPlayerManager().getPlayerList()) {
                                // Hide vanished players from PM suggestions
                                if (sender != null && !VanishHelper.canSeePlayer(p, sender)) continue;
                                builder.suggest(p.getName().getString());
                            }
                            return builder.buildFuture();
                        })
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(PrivateMsgCommand::sendMsg)))
                .build();
        dispatcher.getRoot().addChild(msgNode);
        dispatcher.getRoot().addChild(buildAlias("m",    msgNode));
        dispatcher.getRoot().addChild(buildAlias("w",    msgNode));
        dispatcher.getRoot().addChild(buildAlias("tell", msgNode));

        // /reply <message>  (also /r)
        var replyNode = CommandManager.literal("reply")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.reply"))
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(PrivateMsgCommand::reply))
                .build();
        dispatcher.getRoot().addChild(replyNode);
        dispatcher.getRoot().addChild(buildAlias("r", replyNode));
    }

    /** Creates a redirect alias pointing to {@code target}. */
    private static CommandNode<ServerCommandSource> buildAlias(
            String name,
            CommandNode<ServerCommandSource> target) {
        return CommandManager.literal(name).redirect(target).build();
    }

    // ── /msg ──────────────────────────────────────────────────────────────────

    private static int sendMsg(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity sender)) {
            context.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        String message    = StringArgumentType.getString(context, "message");

        ServerPlayerEntity target = context.getSource().getServer()
                .getPlayerManager().getPlayer(targetName);
        if (target == null) {
            context.getSource().sendError(Lang.get("error.player_not_found"));
            return 0;
        }
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (target == sender && (cfg == null || !cfg.pmAllowSelfMessage)) {
            context.getSource().sendError(Lang.get("pm.error.self"));
            return 0;
        }

        return deliver(sender, target, message) ? 1 : 0;
    }

    // ── /reply ─────────────────────────────────────────────────────────────────

    private static int reply(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity sender)) {
            context.getSource().sendError(Lang.get("error.player_only"));
            return 0;
        }

        UUID targetUuid = lastMsgFrom.get(sender.getUuid());
        if (targetUuid == null) {
            context.getSource().sendError(Lang.get("pm.error.no_reply"));
            return 0;
        }

        ServerPlayerEntity target = context.getSource().getServer()
                .getPlayerManager().getPlayer(targetUuid);
        if (target == null) {
            context.getSource().sendError(Lang.get("pm.error.offline"));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message");
        return deliver(sender, target, message) ? 1 : 0;
    }

    // ── Core delivery ──────────────────────────────────────────────────────────

    private static boolean deliver(ServerPlayerEntity sender, ServerPlayerEntity receiver, String message) {
        // ── Ignore check ───────────────────────────────────────────────────
        if (IgnoreManager.isIgnoring(receiver.getUuid(), sender.getUuid())) {
            sender.sendMessage(Lang.get("pm.error.ignored"), false);
            return false;
        }

        // ── Vanish check — block PM to vanished players ────────────────────
        if (VanishHelper.isVanished(receiver)
                && !LuckPermsHelper.hasPermission(sender.getUuid(), "viastyle.pm.vanished")) {
            sender.sendMessage(Lang.get("error.player_not_found"), false);
            return false;
        }

        ViaStyleConfig cfg = viaStyle.CONFIG;

        String senderFmt   = cfg != null ? cfg.pmSenderFormat   : "[PM -> {receiver}] {message}";
        String receiverFmt = cfg != null ? cfg.pmReceiverFormat : "[PM <- {sender}] {message}";
        String colorStr    = cfg != null ? cfg.pmColor          : "LIGHT_PURPLE";

        TextColor color = cfg != null
                ? cfg.resolveColor(colorStr, TextColor.fromFormatting(Formatting.LIGHT_PURPLE))
                : TextColor.fromFormatting(Formatting.LIGHT_PURPLE);

        String senderName   = sender.getName().getString();
        String receiverName = receiver.getName().getString();

        Text senderMsg   = apply(color, resolve(senderFmt,   senderName, receiverName, message));
        Text receiverMsg = apply(color, resolve(receiverFmt, senderName, receiverName, message));

        sender.sendMessage(senderMsg, false);
        receiver.sendMessage(receiverMsg, false);

        // Track for /reply in both directions
        lastMsgFrom.put(receiver.getUuid(), sender.getUuid());
        lastMsgFrom.put(sender.getUuid(), receiver.getUuid());

        // SocialSpy relay for private messages
        {
            net.minecraft.server.MinecraftServer server = sender.getEntityWorld().getServer();
            if (server != null) {
                ChatHandler.relaySocialSpy(server, sender,
                        "[→ " + receiverName + "] " + message,
                        SocialSpyManager.Channel.PM, "PM");
            }
        }

        if (viaStyle.CONFIG == null || viaStyle.CONFIG.logPrivatesToConsole) {
            viaStyle.LOGGER.info("[PM] {} -> {}: {}", senderName, receiverName, message);
        }
        return true;
    }

    /** Substitutes {sender}, {receiver}, {message} tokens in a format string. */
    private static String resolve(String fmt, String sender, String receiver, String message) {
        return fmt.replace("{sender}", sender)
                  .replace("{receiver}", receiver)
                  .replace("{message}", message);
    }

    /** Wraps a plain string in a Text with the given TextColor. */
    private static Text apply(TextColor color, String text) {
        return Text.literal(text).styled(s -> s.withColor(color));
    }
}
