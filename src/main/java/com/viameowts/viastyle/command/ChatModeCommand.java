package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.viaStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import com.viameowts.viastyle.ChatHandler;
import com.viameowts.viastyle.VanishHelper;
import com.viameowts.viastyle.network.ChatChannel;
import com.viameowts.viastyle.network.Network;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ChatModeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("viaStyle")
            .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.chatmode", 0))
                .then(Commands.literal("local")
                        .then(Commands.literal("!")
                                .executes(ChatModeCommand::setModeLocal)
                        )
                )
                .then(Commands.literal("global")
                        .then(Commands.literal("!")
                                .executes(ChatModeCommand::setModeGlobal)
                        )
                )
                .then(Commands.literal("lang")
                    .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.lang"))
                        .then(Commands.argument("language", StringArgumentType.word())
                                .suggests((context, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            if ("en".startsWith(remaining)) builder.suggest("en");
                            if ("ru".startsWith(remaining)) builder.suggest("ru");
                                    return builder.buildFuture();
                                })
                                .executes(ChatModeCommand::setLanguage)
                        )
                        .executes(ChatModeCommand::showCurrentLanguage)
                )
                .then(Commands.literal("reload")
                        .requires(src -> LuckPermsHelper.checkPermission(src, "viastyle.command.reload", 2))
                        .executes(ChatModeCommand::reloadConfig)
                )
                .executes(ChatModeCommand::showCurrentMode)
        );

        // /ch [channel]  (alias /channel)
        var chNode = Commands.literal("ch")
                .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.channel", 0))
                .then(Commands.argument("channel", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            boolean ru = "ru".equals(Lang.getCurrentLang());
                            for (ChatChannel c : ChatChannel.values()) {
                                if (c == ChatChannel.NETWORK && !Network.enabled()) continue;
                                String name = ru ? Lang.get("channel.name." + c.id).getString() : c.id;
                                if (name.startsWith(remaining)) builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ChatModeCommand::setChannelArg))
                .executes(ChatModeCommand::showCurrentMode)
                .build();
        dispatcher.getRoot().addChild(chNode);
        dispatcher.getRoot().addChild(Commands.literal("channel").requires(chNode.getRequirement()).redirect(chNode).build());

        dispatcher.register(Commands.literal("online")
                .requires(src -> LuckPermsHelper.checkPlayerPermission(src, "viastyle.command.online", 0))
                .executes(ChatModeCommand::showOnline));
    }

    // Legacy toggles: "/viaStyle local !" = the '!' trigger means local, so plain messages go
    // server-wide; "/viaStyle global !" = '!' means global, plain messages stay local.
    private static int setModeLocal(CommandContext<CommandSourceStack> context) {
        return setChannel(context, ChatChannel.PLANET);
    }

    private static int setModeGlobal(CommandContext<CommandSourceStack> context) {
        return setChannel(context, ChatChannel.LOCAL);
    }

    private static int setChannelArg(CommandContext<CommandSourceStack> context) {
        ChatChannel channel = ChatChannel.parse(StringArgumentType.getString(context, "channel"));
        if (channel == null) {
            context.getSource().sendFailure(Lang.get("channel.unknown"));
            return 0;
        }
        return setChannel(context, channel);
    }

    private static int setChannel(CommandContext<CommandSourceStack> context, ChatChannel channel) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        if (channel == ChatChannel.NETWORK && !Network.enabled()) {
            source.sendFailure(Lang.get("channel.no_network"));
            return 0;
        }
        if (channel == ChatChannel.STAFF && !ChatHandler.hasStaffPermission(player)) {
            source.sendFailure(Lang.get("chat.staff_no_permission"));
            return 0;
        }
        viaStyle.setDefaultChannel(player.getUUID(), channel);
        Component feedback = Lang.getMutable("channel.set").append(channelName(channel));
        source.sendSuccess(() -> feedback, false);
        return 1;
    }

    private static int showCurrentMode(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Lang.get("error.player_only"));
            return 0;
        }
        ChatChannel current = viaStyle.getDefaultChannel(player.getUUID());
        MutableComponent feedback = Lang.getMutable("channel.current").append(channelName(current));
        source.sendSuccess(() -> feedback, false);

        ViaStyleConfig cfg = viaStyle.CONFIG;
        MutableComponent triggers = Lang.getMutable("channel.triggers");
        boolean network = Network.enabled();
        appendTrigger(triggers, cfg.globalTrigger, network ? ChatChannel.NETWORK : ChatChannel.PLANET);
        if (network) appendTrigger(triggers, cfg.planetTrigger, ChatChannel.PLANET);
        appendTrigger(triggers, cfg.localTrigger, ChatChannel.LOCAL);
        if (ChatHandler.hasStaffPermission(player)) appendTrigger(triggers, cfg.staffTrigger, ChatChannel.STAFF);
        source.sendSuccess(() -> triggers, false);
        return 1;
    }

    private static void appendTrigger(MutableComponent out, String trigger, ChatChannel channel) {
        if (trigger == null || trigger.isEmpty()) return;
        out.append(Component.literal(" " + trigger + " ").withStyle(ChatFormatting.WHITE))
           .append(channelName(channel));
    }

    private static Component channelName(ChatChannel channel) {
        return Lang.get("channel.name." + channel.id);
    }

    /** /online — who is on which server of the network. */
    private static int showOnline(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer viewer = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        boolean seeVanished = viewer == null
                || LuckPermsHelper.checkPlayerPermission(viewer, "viastyle.pm.vanished", 2);

        Map<String, List<String>> byServer = new TreeMap<>();
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            if (viewer != null && !VanishHelper.canSeePlayer(p, viewer)) continue;
            byServer.computeIfAbsent(Network.serverDisplayName(), k -> new ArrayList<>()).add(p.getName().getString());
        }
        for (Network.NetPlayer p : Network.remotePlayers()) {
            if (p.vanished() && !seeVanished) continue;
            byServer.computeIfAbsent(p.display(), k -> new ArrayList<>()).add(p.name());
        }
        int total = byServer.values().stream().mapToInt(List::size).sum();
        MutableComponent header = Lang.getMutable("online.header")
                .append(Component.literal(String.valueOf(total)).withStyle(ChatFormatting.WHITE));
        source.sendSuccess(() -> header, false);
        byServer.forEach((serverName, names) -> {
            names.sort(String.CASE_INSENSITIVE_ORDER);
            MutableComponent line = Component.literal(" " + serverName + " (" + names.size() + "): ")
                    .withStyle(s -> s.withColor(TextColor.fromRgb(0xFCDE9D)))
                    .append(Component.literal(String.join(", ", names)).withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        });
        return total;
    }

    private static int setLanguage(CommandContext<CommandSourceStack> context) {
        String langArg = StringArgumentType.getString(context, "language");
        CommandSourceStack source = context.getSource();

        if (Lang.setLang(langArg)) {
            // Persist language choice to config
            viaStyle.CONFIG.defaultLanguage = langArg.toLowerCase();
            viaStyle.CONFIG.applyLocalizedPlaceholderDefaults(viaStyle.CONFIG.defaultLanguage);
            viaStyle.CONFIG.save();
            com.viameowts.viastyle.MeridianaAudit.action(source.getTextName(), "/viaStyle lang: язык сервера теперь " + langArg);

            Component feedback = Lang.getMutable("command.lang.set")
                    .append(Component.literal(langArg).withStyle(ChatFormatting.AQUA));
            source.sendSuccess(() -> feedback, true);
            return 1;
        } else {
            source.sendFailure(Lang.get("command.lang.invalid"));
            return 0;
        }
    }

    private static int showCurrentLanguage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Component feedback = Lang.getMutable("command.lang.current")
                .append(Component.literal(Lang.getCurrentLang()).withStyle(ChatFormatting.AQUA));
        source.sendSuccess(() -> feedback, false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Reload main config
        viaStyle.CONFIG = ViaStyleConfig.load();

        // Reload tab list config
        com.viameowts.viastyle.TabListManager.reloadConfig();

        // Reload nick colours
        com.viameowts.viastyle.NickColorManager.reload();

        // Re-apply to all online players
        net.minecraft.server.MinecraftServer server = source.getServer();
        com.viameowts.viastyle.TabListManager.updateAll(server);
        com.viameowts.viastyle.NametagManager.updateAll(server);

        // Re-set language
        if (viaStyle.CONFIG.defaultLanguage != null && !viaStyle.CONFIG.defaultLanguage.isBlank()) {
            Lang.setLang(viaStyle.CONFIG.defaultLanguage);
            if (viaStyle.CONFIG.applyLocalizedPlaceholderDefaults(viaStyle.CONFIG.defaultLanguage)) {
                viaStyle.CONFIG.save();
            }
        }

        com.viameowts.viastyle.MeridianaAudit.action(source.getTextName(), "/viaStyle reload: настройки перечитаны");
        source.sendSuccess(
                () -> Lang.get("reload.done"),
                true);
        return 1;
    }
}
