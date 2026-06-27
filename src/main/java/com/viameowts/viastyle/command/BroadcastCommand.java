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
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class BroadcastCommand {

    private static final Map<UUID, Long> lastBroadcastMillis = new ConcurrentHashMap<>();
    private static final Pattern CLOSE_COLOR_TAG_PATTERN = Pattern.compile(
            "</(#[0-9a-fA-F]{6}|color:\\s*#?[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>",
            Pattern.CASE_INSENSITIVE
    );

    private BroadcastCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("bc")
                .requires(source -> {
                    ViaStyleConfig cfg = viaStyle.CONFIG;
                    if (cfg == null || !cfg.broadcastEnabled) return false;
                    if (cfg.broadcastPermission == null || cfg.broadcastPermission.isBlank()) {
                        return LuckPermsHelper.hasOpLevel(source, 2);
                    }
                    return LuckPermsHelper.checkPermission(source, cfg.broadcastPermission, 2);
                })
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(BroadcastCommand::execute)));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        if (cfg == null || !cfg.broadcastEnabled) {
            return 0;
        }

        ServerCommandSource source = context.getSource();
        String rawMessage = StringArgumentType.getString(context, "message");

        if (source.getEntity() instanceof ServerPlayerEntity sender) {
            if (BanHammerHelper.isMuted(sender)) {
                sender.sendMessage(Lang.get("chat.muted"), false);
                return 0;
            }
            if (!checkCooldown(sender, cfg, source)) {
                return 0;
            }
        }

        String senderName = source.getEntity() instanceof ServerPlayerEntity player
                ? player.getName().getString()
            : safeString(cfg.broadcastConsoleSenderName, source.getName());

        Text header = renderTemplate(cfg.broadcastHeaderFormat, senderName, rawMessage,
                source.getEntity() instanceof ServerPlayerEntity sp ? sp : null);
        Text line = renderTemplate(cfg.broadcastMessageFormat, senderName, rawMessage,
            source.getEntity() instanceof ServerPlayerEntity sp ? sp : null);

        int delivered = 0;
        for (ServerPlayerEntity target : source.getServer().getPlayerManager().getPlayerList()) {
            target.sendMessage(header, false);
            target.sendMessage(line, false);
            if (cfg.broadcastSoundEnabled) {
                playConfiguredSound(target, cfg);
            }
            delivered++;
        }

        if (cfg.broadcastSendFeedback) {
            final int deliveredCount = delivered;
            source.sendFeedback(() -> {
                MutableText feedback = Lang.getMutable("broadcast.feedback_prefix")
                        .append(Text.literal(String.valueOf(deliveredCount)).styled(s -> s.withColor(Lang.colorGreen())))
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
        return delivered;
    }

    public static void clearPlayer(UUID uuid) {
        lastBroadcastMillis.remove(uuid);
    }

    private static boolean checkCooldown(ServerPlayerEntity sender, ViaStyleConfig cfg, ServerCommandSource source) {
        int seconds = Math.max(0, cfg.broadcastCooldownSeconds);
        if (seconds <= 0) return true;

        long now = System.currentTimeMillis();
        Long last = lastBroadcastMillis.get(sender.getUuid());
        if (last != null) {
            long leftMillis = seconds * 1000L - (now - last);
            if (leftMillis > 0) {
                long leftSec = Math.max(1L, (leftMillis + 999L) / 1000L);
                MutableText cooldown = Lang.getMutable("broadcast.cooldown")
                        .append(Text.literal(String.valueOf(leftSec)).styled(s -> s.withColor(Lang.colorRed())))
                        .append(Lang.get("broadcast.cooldown_suffix"));
                source.sendError(cooldown);
                return false;
            }
        }

        lastBroadcastMillis.put(sender.getUuid(), now);
        return true;
    }

    private static Text renderTemplate(String template,
                                       String senderName,
                                       String rawMessage,
                                       ServerPlayerEntity contextPlayer) {
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

    private static void playConfiguredSound(ServerPlayerEntity target, ViaStyleConfig cfg) {
        RegistryEntryOrNull sound = resolveSound(cfg.broadcastSoundId);
        if (sound == null) return;

        float volume = (float) Math.max(0.0, cfg.broadcastSoundVolume);
        float pitch = (float) Math.max(0.01, cfg.broadcastSoundPitch);

        target.networkHandler.sendPacket(new PlaySoundS2CPacket(
                sound.entry(), SoundCategory.MASTER,
                target.getX(), target.getY(), target.getZ(),
                volume, pitch, target.getRandom().nextLong()));
    }

    private static RegistryEntryOrNull resolveSound(String id) {
        Identifier identifier = id == null || id.isBlank()
                ? Identifier.tryParse("minecraft:block.note_block.bell")
                : Identifier.tryParse(id);
        if (identifier == null) {
            identifier = Identifier.ofVanilla("block.note_block.bell");
        }
        return Registries.SOUND_EVENT.getEntry(identifier)
                .map(RegistryEntryOrNull::new)
                .orElse(null);
    }

    private record RegistryEntryOrNull(net.minecraft.registry.entry.RegistryEntry.Reference<SoundEvent> entry) {}
}
