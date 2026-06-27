package com.viameowts.viastyle;

import com.viameowts.viapanel.api.ViaPanelProvider;
import com.viameowts.viapanel.api.ViaPanelSection;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ViaStylePanelProvider implements ViaPanelProvider {

    private static final List<ViaPanelSection> SECTIONS = List.of(
            new ViaPanelSection("local", Lang.get("panel.page.local"), List.of(
                    "localChatRadius", "localTrigger", "localPrefix", "localPrefixColor",
                    "localNameColor", "localMessageColor", "localFormat",
                    "localNooneHeard", "localNooneHeardMessage"
            )),
            new ViaPanelSection("global", Lang.get("panel.page.global"), List.of(
                    "globalTrigger", "globalPrefix", "globalPrefixColor",
                    "globalNameColor", "globalMessageColor", "globalFormat"
            )),
            new ViaPanelSection("staff", Lang.get("panel.page.staff"), List.of(
                    "staffTrigger", "staffPrefix", "staffPrefixColor",
                    "staffNameColor", "staffMessageColor", "staffFormat"
            )),
            new ViaPanelSection("chat_format", Lang.get("panel.page.chat_format"), List.of(
                    "chatMiniMessageEnabled", "chatMiniMessageRequirePermission", "chatMiniMessagePermission"
            )),
            new ViaPanelSection("timestamp", Lang.get("panel.page.timestamp"), List.of(
                    "showTimestamp", "timestampFormat", "timestampColor"
            )),
            new ViaPanelSection("nickcolor", Lang.get("panel.page.nickcolor"), List.of(
                    "nickColorEnabled", "nickColorInChat", "nickColorInTab", "nickColorInNametag"
            )),
            new ViaPanelSection("nametag", Lang.get("panel.page.nametag"), List.of(
                    "nametagMode", "nametagShowLpPrefix", "nametagColorStrategy",
                    "nametagOrphanScanEnabled", "nametagOrphanScanIntervalTicks"
            )),
            new ViaPanelSection("tablist", Lang.get("panel.page.tablist"), List.of(
                    "tabSortMode", "tabSortSpectatorsToBottom"
            )),
            new ViaPanelSection("pm", Lang.get("panel.page.pm"), List.of(
                    "pmAllowSelfMessage", "pmBanHammerMute", "pmSenderFormat", "pmReceiverFormat", "pmColor",
                    "pmSoundEnabled", "pmSoundId", "pmSoundVolume", "pmSoundPitch"
            )),
            new ViaPanelSection("mentions", Lang.get("panel.page.mentions"), List.of(
                    "mentionsEnabled", "mentionSound", "mentionColor"
            )),
            new ViaPanelSection("broadcast", Lang.get("panel.page.broadcast"), List.of(
                    "broadcastEnabled", "broadcastPermission", "broadcastCooldownSeconds",
                    "broadcastHeaderFormat", "broadcastMessageFormat",
                    "broadcastConsoleSenderName", "broadcastCooldownFormat", "broadcastFeedbackFormat", "broadcastLogFormat",
                    "broadcastSoundEnabled", "broadcastSoundId", "broadcastSoundVolume", "broadcastSoundPitch",
                    "broadcastSendFeedback"
            )),
            new ViaPanelSection("joinleave", Lang.get("panel.page.joinleave"), List.of(
                    "joinFormat", "leaveFormat", "firstJoinFormat", "joinLeavePerPlayerEnabled"
            )),
            new ViaPanelSection("joinleave_overrides", Lang.get("panel.page.joinleave_overrides"), List.of(
                    "joinLeavePanelPlayerTarget", "joinLeavePanelPlayerJoinFormat", "joinLeavePanelPlayerLeaveFormat",
                    "joinLeavePanelGroupTarget", "joinLeavePanelGroupJoinFormat", "joinLeavePanelGroupLeaveFormat"
            )),
            new ViaPanelSection("console", Lang.get("panel.page.console"), List.of(
                    "logGlobalToConsole", "logLocalToConsole", "logStaffToConsole", "logPrivatesToConsole"
            )),
            new ViaPanelSection("language", Lang.get("panel.page.language"), List.of(
                    "defaultLanguage"
            )),
            new ViaPanelSection("blockbot", Lang.get("panel.page.blockbot"), List.of(
                    "discordBridgeMode", "blockbotGlobalChannel", "blockbotLocalChannel",
                    "discordFormat", "discordPassthrough",
                    "discordMentionPing", "discordMentionMappings"
            )),
            new ViaPanelSection("integrations", Lang.get("panel.page.integrations"), List.of(
                    "usePlaceholderApi", "useBanHammer", "useLuckPerms"
            )),
            new ViaPanelSection("viasuper", Lang.get("panel.page.viasuper"), List.of(
                    "viaSuperWordSound", "viaSuperSubtitleLength",
                    "viaSuperTitleFormat", "viaSuperSubtitleFormat"
            )),
            new ViaPanelSection("afk", Lang.get("panel.page.afk"), List.of(
                    "afkEnabled", "afkTimeout", "afkSuffix", "afkSuffixColor", "afkNameColor",
                    "afkPermission", "afkBypassPermission", "afkExemptPlayers",
                    "afkEnabledColor", "afkDisabledColor"
            ))
    );

    @Override
    public String modId() {
        return viaStyle.MOD_ID;
    }

    @Override
    public Text modDisplayName() {
        return Text.literal("viaStyle");
    }

    @Override
    public Text panelTitle() {
        return Lang.get("panel.title");
    }

    @Override
    public boolean hasPermission(ServerCommandSource source) {
        return LuckPermsHelper.checkPermission(source, "viastyle.panel", 2);
    }

    @Override
    public Class<?> configClass() {
        return ViaStyleConfig.class;
    }

    @Override
    public Object configInstance() {
        return viaStyle.CONFIG;
    }

    @Override
    public List<ViaPanelSection> sections() {
        return SECTIONS;
    }

    @Override
    public Text fieldDisplayName(String fieldName) {
        return Text.literal(toDisplayName(fieldName));
    }

    @Override
    public Text fieldDescription(String fieldName) {
        return Lang.get("panel.field." + fieldName);
    }

    @Override
    public Text toggleHintText() {
        return Lang.get("panel.click_toggle");
    }

    @Override
    public Text editHintText() {
        return Lang.get("panel.click_edit");
    }

    @Override
    public Text savedSuffixText() {
        return Lang.get("panel.saved");
    }

    @Override
    public Text fieldNotBooleanText() {
        return Lang.get("panel.field_not_boolean");
    }

    @Override
    public Text unknownFieldText() {
        return Lang.get("panel.unknown_field");
    }

    @Override
    public Text invalidNumberText() {
        return Lang.get("panel.invalid_number");
    }

    @Override
    public void reload(ServerCommandSource source) {
        viaStyle.CONFIG = ViaStyleConfig.load();
        Lang.setLang(viaStyle.CONFIG.defaultLanguage);
        if (viaStyle.CONFIG.applyLocalizedPlaceholderDefaults(viaStyle.CONFIG.defaultLanguage)) {
            viaStyle.CONFIG.save();
        }
        JoinLeaveManager.reload();
        TabListManager.reloadConfig();
        NickColorManager.reload();

        var server = source.getServer();
        TabListManager.updateAll(server);
        NametagManager.updateAll(server);
    }

    @Override
    public Text reloadDoneText() {
        return Lang.get("panel.reload_done");
    }

    @Override
    public void onFieldUpdated(String fieldName, ServerCommandSource source) {
        if ("defaultLanguage".equals(fieldName) && viaStyle.CONFIG != null) {
            Lang.setLang(viaStyle.CONFIG.defaultLanguage);
            if (viaStyle.CONFIG.applyLocalizedPlaceholderDefaults(viaStyle.CONFIG.defaultLanguage)) {
                viaStyle.CONFIG.save();
            }
        }

        handleJoinLeaveOverrideField(fieldName, source);

        if (needsVisualRefresh(fieldName)) {
            var server = source.getServer();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                NickColorManager.invalidate(player.getUuid());
            }
            TabListManager.updateAll(server);
            NametagManager.updateAll(server);
        }
    }

    @Override
    public void applyGlobalLanguage(String languageCode, ServerCommandSource source) {
        if (viaStyle.CONFIG == null) {
            return;
        }
        if (!"ru".equalsIgnoreCase(languageCode) && !"en".equalsIgnoreCase(languageCode)) {
            return;
        }
        viaStyle.CONFIG.defaultLanguage = languageCode.toLowerCase();
        viaStyle.CONFIG.applyLocalizedPlaceholderDefaults(viaStyle.CONFIG.defaultLanguage);
        viaStyle.CONFIG.save();
        Lang.setLang(viaStyle.CONFIG.defaultLanguage);
    }

    private static boolean needsVisualRefresh(String fieldName) {
        return fieldName.contains("nickColor") || fieldName.contains("nametag")
                || fieldName.contains("tab") || fieldName.contains("Tab")
                || fieldName.contains("Nametag") || fieldName.contains("NickColor")
                || fieldName.contains("Spectator") || fieldName.contains("spectator")
                || fieldName.contains("afk");
    }

    private static void handleJoinLeaveOverrideField(String fieldName, ServerCommandSource source) {
        if (viaStyle.CONFIG == null) return;

        switch (fieldName) {
            case "joinLeavePanelPlayerTarget" -> {
                UUID uuid = resolvePlayerTargetUuid(source, viaStyle.CONFIG.joinLeavePanelPlayerTarget);
                if (uuid == null) return;
                JoinLeaveManager.MessagePair pair = JoinLeaveManager.getUser(uuid);
                viaStyle.CONFIG.joinLeavePanelPlayerJoinFormat = pair != null && pair.join != null ? pair.join : "";
                viaStyle.CONFIG.joinLeavePanelPlayerLeaveFormat = pair != null && pair.leave != null ? pair.leave : "";
                viaStyle.CONFIG.save();
            }
            case "joinLeavePanelPlayerJoinFormat" -> {
                UUID uuid = resolvePlayerTargetUuid(source, viaStyle.CONFIG.joinLeavePanelPlayerTarget);
                if (uuid == null) return;
                String format = normalizePanelField(viaStyle.CONFIG.joinLeavePanelPlayerJoinFormat);
                if (format == null) JoinLeaveManager.removeUserJoin(uuid);
                else JoinLeaveManager.setUserJoin(uuid, format);
            }
            case "joinLeavePanelPlayerLeaveFormat" -> {
                UUID uuid = resolvePlayerTargetUuid(source, viaStyle.CONFIG.joinLeavePanelPlayerTarget);
                if (uuid == null) return;
                String format = normalizePanelField(viaStyle.CONFIG.joinLeavePanelPlayerLeaveFormat);
                if (format == null) JoinLeaveManager.removeUserLeave(uuid);
                else JoinLeaveManager.setUserLeave(uuid, format);
            }
            case "joinLeavePanelGroupTarget" -> {
                String group = normalizeGroupTarget(viaStyle.CONFIG.joinLeavePanelGroupTarget);
                if (group == null) return;
                JoinLeaveManager.MessagePair pair = JoinLeaveManager.getGroups().get(group);
                viaStyle.CONFIG.joinLeavePanelGroupJoinFormat = pair != null && pair.join != null ? pair.join : "";
                viaStyle.CONFIG.joinLeavePanelGroupLeaveFormat = pair != null && pair.leave != null ? pair.leave : "";
                viaStyle.CONFIG.save();
            }
            case "joinLeavePanelGroupJoinFormat" -> {
                String group = normalizeGroupTarget(viaStyle.CONFIG.joinLeavePanelGroupTarget);
                if (group == null) return;
                String format = normalizePanelField(viaStyle.CONFIG.joinLeavePanelGroupJoinFormat);
                if (format == null) JoinLeaveManager.removeGroupJoin(group);
                else JoinLeaveManager.setGroupJoin(group, format);
            }
            case "joinLeavePanelGroupLeaveFormat" -> {
                String group = normalizeGroupTarget(viaStyle.CONFIG.joinLeavePanelGroupTarget);
                if (group == null) return;
                String format = normalizePanelField(viaStyle.CONFIG.joinLeavePanelGroupLeaveFormat);
                if (format == null) JoinLeaveManager.removeGroupLeave(group);
                else JoinLeaveManager.setGroupLeave(group, format);
            }
            default -> {
            }
        }
    }

    private static UUID resolvePlayerTargetUuid(ServerCommandSource source, String target) {
        String value = normalizePanelField(target);
        if (value == null) return null;

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (player.getName().getString().equalsIgnoreCase(value)) {
                return player.getUuid();
            }
        }

        source.sendError(Lang.get("joinleave.admin.player_not_found"));
        return null;
    }

    private static String normalizePanelField(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeGroupTarget(String group) {
        String normalized = normalizePanelField(group);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String toDisplayName(String fieldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append(' ');
            }
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }
}
