package com.viameowts.viastyle;

import com.viameowts.viapanel.api.ViaPanelProvider;
import com.viameowts.viapanel.api.ViaPanelSection;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

public class ViaStylePanelProvider implements ViaPanelProvider {

    private static final List<ViaPanelSection> SECTIONS = List.of(
            new ViaPanelSection("chat", Lang.get("panel.page.chat"), List.of(
                    "localChatRadius", "localPrefix", "localPrefixColor", "localNameColor", "localMessageColor",
                    "localNooneHeard", "localNooneHeardMessage",
                    "globalTrigger", "globalPrefix", "globalPrefixColor", "globalNameColor", "globalMessageColor",
                    "staffTrigger", "staffPrefix", "staffPrefixColor", "staffNameColor", "staffMessageColor"
            )),
            new ViaPanelSection("timestamp", Lang.get("panel.page.timestamp"), List.of(
                    "showTimestamp", "timestampFormat", "timestampColor"
            )),
            new ViaPanelSection("integrations", Lang.get("panel.page.integrations"), List.of(
                    "usePlaceholderApi", "useBanHammer", "useLuckPerms", "useScarpetEvents"
            )),
            new ViaPanelSection("nickcolor", Lang.get("panel.page.nickcolor"), List.of(
                    "nickColorEnabled", "nickColorInChat", "nickColorInTab", "nickColorInNametag",
                    "nametagShowLpPrefix", "nametagMode", "nametagColorStrategy",
                    "nametagOrphanScanEnabled", "nametagOrphanScanIntervalTicks"
            )),
            new ViaPanelSection("tablist", Lang.get("panel.page.tablist"), List.of(
                    "tabSortMode", "tabSortSpectatorsToBottom"
            )),
            new ViaPanelSection("pm", Lang.get("panel.page.pm"), List.of(
                    "pmAllowSelfMessage", "pmSenderFormat", "pmReceiverFormat", "pmColor"
            )),
            new ViaPanelSection("mentions", Lang.get("panel.page.mentions"), List.of(
                    "mentionsEnabled", "mentionSound", "mentionBold", "mentionColor"
            )),
            new ViaPanelSection("viasuper", Lang.get("panel.page.viasuper"), List.of(
                    "viaSuperWordSound", "viaSuperSubtitleLength"
            )),
            new ViaPanelSection("joinleave", Lang.get("panel.page.joinleave"), List.of(
                    "joinFormat", "leaveFormat", "firstJoinFormat"
            )),
            new ViaPanelSection("console", Lang.get("panel.page.console"), List.of(
                    "logGlobalToConsole", "logLocalToConsole", "logStaffToConsole", "logPrivatesToConsole"
            )),
            new ViaPanelSection("language", Lang.get("panel.page.language"), List.of(
                    "defaultLanguage"
            )),
            new ViaPanelSection("blockbot", Lang.get("panel.page.blockbot"), List.of(
                    "discordBridgeMode", "blockbotGlobalChannel", "discordFormat", "discordPassthrough", "discordMentionPing"
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
        }

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
        viaStyle.CONFIG.save();
        Lang.setLang(viaStyle.CONFIG.defaultLanguage);
    }

    private static boolean needsVisualRefresh(String fieldName) {
        return fieldName.contains("nickColor") || fieldName.contains("nametag")
                || fieldName.contains("tab") || fieldName.contains("Tab")
                || fieldName.contains("Nametag") || fieldName.contains("NickColor")
                || fieldName.contains("Spectator") || fieldName.contains("spectator");
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
