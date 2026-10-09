package com.viameowts.viastyle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.viameowts.viastyle.BanHammerHelper;
import com.viameowts.viastyle.ChatMiniMessageParser;
import com.viameowts.viastyle.Lang;
import com.viameowts.viastyle.LuckPermsHelper;
import com.viameowts.viastyle.PlaceholderHelper;
import com.viameowts.viastyle.ViaStyleConfig;
import com.viameowts.viastyle.viaStyle;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class BroadcastCommand {

    private static final Map<UUID, Long> lastBroadcastMillis = new ConcurrentHashMap<>();
    private static final Pattern CLOSE_COLOR_TAG_PATTERN = Pattern.compile(
            "</(#[0-9a-fA-F]{6}|color:\\s*#?[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>",
            Pattern.CASE_INSENSITIVE
    );

    private BroadcastCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("bc")
                .requires(source -> {
                    ViaStyleConfig cfg = viaStyle.CONFIG;
                    if (cfg == null || !cfg.broadcastEnabled) return false;
                    // an empty key means the default node, so that LuckPerms still decides
                    String node = cfg.broadcastPermission == null || cfg.broadcastPermission.isBlank()
                            ? "viastyle.command.broadcast" : cfg.broadcastPermission;
                    return LuckPermsHelper.checkPermission(source, node, 2);
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(BroadcastCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.broadcastEnabled) {
            return 0;
        }

        CommandSourceStack source = context.getSource();
        String rawMessage = StringArgumentType.getString(context, "message");

        if (source.getEntity() instanceof ServerPlayer sender) {
            if (BanHammerHelper.isMuted(sender)) {
                sender.sendSystemMessage(Lang.get("chat.muted"));
                return 0;
            }
            if (!checkCooldown(sender, cfg, source)) {
                return 0;
            }
        }

        String senderName = source.getEntity() instanceof ServerPlayer player
                ? player.getName().getString()
            : safeString(cfg.broadcastConsoleSenderName, source.getTextName());

        Component header = renderTemplate(cfg.broadcastHeaderFormat, senderName, rawMessage,
                source.getEntity() instanceof ServerPlayer sp ? sp : null);
        Component line = renderTemplate(cfg.broadcastMessageFormat, senderName, rawMessage,
            source.getEntity() instanceof ServerPlayer sp ? sp : null);

        int delivered = 0;
        for (ServerPlayer target : source.getServer().getPlayerList().getPlayers()) {
            target.sendSystemMessage(header);
            target.sendSystemMessage(line);
            if (cfg.broadcastSoundEnabled) {
                playConfiguredSound(target, cfg);
            }
            delivered++;
        }

        if (cfg.broadcastSendFeedback) {
            final int deliveredCount = delivered;
            source.sendSuccess(() -> {
                MutableComponent feedback = Lang.getMutable("broadcast.feedback_prefix")
                        .append(Component.literal(String.valueOf(deliveredCount)).withStyle(s -> s.withColor(Lang.colorGreen())))
                        .append(Lang.get("broadcast.feedback_suffix"));
                return feedback;
            }, false);
        }

        String logLine = applyTokens(safeString(cfg.broadcastLogFormat, ""),
                "sender", senderName,
                "message", rawMessage,
            "count", String.valueOf(delivered));
        if (!logLine.isBlank()) {
            viaStyle.LOGGER.info(logLine);
        }
        com.viameowts.viastyle.MeridianaAudit.action(senderName, "/bc: " + rawMessage + " (получателей: " + delivered + ")");
        return delivered;
    }

    public static void clearPlayer(UUID uuid) {
        lastBroadcastMillis.remove(uuid);
    }

    private static boolean checkCooldown(ServerPlayer sender, ViaStyleConfig cfg, CommandSourceStack source) {
        int seconds = Math.max(0, cfg.broadcastCooldownSeconds);
        if (seconds <= 0) return true;

        long now = System.currentTimeMillis();
        Long last = lastBroadcastMillis.get(sender.getUUID());
        if (last != null) {
            long leftMillis = seconds * 1000L - (now - last);
            if (leftMillis > 0) {
                long leftSec = Math.max(1L, (leftMillis + 999L) / 1000L);
                MutableComponent cooldown = Lang.getMutable("broadcast.cooldown")
                        .append(Component.literal(String.valueOf(leftSec)).withStyle(s -> s.withColor(Lang.colorRed())))
                        .append(Lang.get("broadcast.cooldown_suffix"));
                source.sendFailure(cooldown);
                return false;
            }
        }

        lastBroadcastMillis.put(sender.getUUID(), now);
        return true;
    }

    private static Component renderTemplate(String template,
                                       String senderName,
                                       String rawMessage,
                                       ServerPlayer contextPlayer) {
        String safeTemplate = template == null || template.isBlank() ? "{message}" : template;
        String prepared = applyTokens(safeTemplate,
                "sender", senderName,
                "message", rawMessage);

        if (CLOSE_COLOR_TAG_PATTERN.matcher(prepared).find()) {
            return ChatMiniMessageParser.parse(prepared, TextColor.fromRgb(0xD9D0D5));
        }

        return PlaceholderHelper.parseFormat(prepared, contextPlayer);
    }

    private static String applyTokens(String template, String... keyValues) {
        String result = safeString(template, "");
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            String key = keyValues[index] == null ? "" : keyValues[index];
            String value = keyValues[index + 1] == null ? "" : keyValues[index + 1];
            result = result.replace("{" + key + "}", value);
        }
        return result;
    }

    private static String safeString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static void playConfiguredSound(ServerPlayer target, ViaStyleConfig cfg) {
        RegistryEntryOrNull sound = resolveSound(cfg.broadcastSoundId);
        if (sound == null) return;

        float volume = (float) Math.max(0.0, cfg.broadcastSoundVolume);
        float pitch = (float) Math.max(0.01, cfg.broadcastSoundPitch);

        target.connection.send(new ClientboundSoundPacket(
                sound.entry(), SoundSource.MASTER,
                target.getX(), target.getY(), target.getZ(),
                volume, pitch, target.getRandom().nextLong()));
    }

    private static RegistryEntryOrNull resolveSound(String id) {
        Identifier identifier = id == null || id.isBlank()
                ? Identifier.tryParse("minecraft:block.note_block.bell")
                : Identifier.tryParse(id);
        if (identifier == null) {
            identifier = Identifier.withDefaultNamespace("block.note_block.bell");
        }
        return BuiltInRegistries.SOUND_EVENT.get(identifier)
                .map(RegistryEntryOrNull::new)
                .orElse(null);
    }

    private record RegistryEntryOrNull(net.minecraft.core.Holder.Reference<SoundEvent> entry) {}
}
