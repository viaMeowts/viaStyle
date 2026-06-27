package com.viameowts.viastyle;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatSharePlaceholders {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\[(item|pos|inv|ec|ender)]", Pattern.CASE_INSENSITIVE);
    private static final Map<UUID, Long> lastUseMillis = new ConcurrentHashMap<>();
    private static final Map<String, SharedView> sharedViews = new ConcurrentHashMap<>();

    private ChatSharePlaceholders() {}

    public record ProcessedMessage(Text component, String plainText) {}

    private record Replacement(Text component, String plainText) {}

    private record SharedView(
            String id,
            String type,
            String ownerName,
            SimpleInventory inventory,
            int rows,
            long expiresAtMillis
    ) {}

    public static ProcessedMessage processMessage(String message,
                                                  ServerPlayerEntity sender,
                                                  MinecraftServer server,
                                                  TextColor baseColor) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        boolean useMiniMessage = canUseMiniMessage(sender, cfg);
        if (cfg == null || !cfg.chatPlaceholdersEnabled || message == null || message.isEmpty()) {
            Text plain = MentionHandler.highlightMentions(message == null ? "" : message, baseColor, server, sender, useMiniMessage);
            return new ProcessedMessage(plain, message == null ? "" : message);
        }

        Matcher matcher = TOKEN_PATTERN.matcher(message);
        if (!matcher.find()) {
            Text plain = MentionHandler.highlightMentions(message, baseColor, server, sender, useMiniMessage);
            return new ProcessedMessage(plain, message);
        }

        int tokenCount = 0;
        matcher.reset();
        while (matcher.find()) tokenCount++;

        long now = System.currentTimeMillis();
        int cooldownSeconds = Math.max(0, cfg.chatPlaceholderCooldownSeconds);
        Long lastUse = lastUseMillis.get(sender.getUuid());
        if (cooldownSeconds > 0 && lastUse != null) {
            long delta = now - lastUse;
            long cooldownMillis = cooldownSeconds * 1000L;
            if (delta < cooldownMillis) {
                long left = Math.max(1L, (cooldownMillis - delta + 999L) / 1000L);
                sender.sendMessage(Lang.getMutable("chat.placeholder.cooldown")
                        .append(Text.literal(String.valueOf(left))), false);
                Text plain = MentionHandler.highlightMentions(message, baseColor, server, sender, useMiniMessage);
                return new ProcessedMessage(plain, message);
            }
        }

        int limit = cfg.chatPlaceholderMaxPerMessage <= 0
                ? Integer.MAX_VALUE : cfg.chatPlaceholderMaxPerMessage;

        MutableText out = Text.empty();
        StringBuilder plainOut = new StringBuilder();
        int cursor = 0;
        int used = 0;
        boolean replacedAny = false;
        boolean warnedNoPermission = false;

        matcher.reset();
        while (matcher.find()) {
            String before = message.substring(cursor, matcher.start());
            if (!before.isEmpty()) {
                out.append(MentionHandler.highlightMentions(before, baseColor, server, sender, useMiniMessage));
                plainOut.append(before);
            }

            String tokenRaw = matcher.group(0);
            String token = matcher.group(1).toLowerCase(Locale.ROOT);

            if (used >= limit) {
                appendLiteral(out, plainOut, tokenRaw, baseColor);
                cursor = matcher.end();
                continue;
            }

            if (!hasTokenPermission(sender, token, cfg)) {
                appendLiteral(out, plainOut, tokenRaw, baseColor);
                if (!warnedNoPermission) {
                    sender.sendMessage(Lang.get("chat.placeholder.no_permission"), false);
                    warnedNoPermission = true;
                }
                cursor = matcher.end();
                continue;
            }

            Replacement replacement = switch (token) {
                case "item" -> buildItemReplacement(sender, cfg);
                case "pos" -> buildPosReplacement(sender, cfg);
                case "inv" -> buildInventoryReplacement(sender, cfg, false);
                case "ec", "ender" -> buildInventoryReplacement(sender, cfg, true);
                default -> null;
            };

            if (replacement == null) {
                if (cfg.chatPlaceholderLetMessageThrough) {
                    appendLiteral(out, plainOut, tokenRaw, baseColor);
                }
            } else {
                out.append(replacement.component());
                plainOut.append(replacement.plainText());
                used++;
                replacedAny = true;
            }

            cursor = matcher.end();
        }

        if (cursor < message.length()) {
            String tail = message.substring(cursor);
            out.append(MentionHandler.highlightMentions(tail, baseColor, server, sender, useMiniMessage));
            plainOut.append(tail);
        }

        if (replacedAny && cooldownSeconds > 0 && tokenCount > 0) {
            lastUseMillis.put(sender.getUuid(), now);
        }

        pruneExpiredViews(now);
        return new ProcessedMessage(out, plainOut.toString());
    }

    public static boolean openSharedView(ServerPlayerEntity viewer, String id) {
        if (id == null || id.isBlank()) return false;
        SharedView view = sharedViews.get(id);
        if (view == null) return false;

        long now = System.currentTimeMillis();
        if (view.expiresAtMillis() < now) {
            sharedViews.remove(id);
            return false;
        }

        ScreenHandlerType<?> type = switch (view.rows()) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, player) ->
                new ReadOnlyContainer(type, syncId, playerInventory, view.inventory(), view.rows()),
                buildViewTitle(view, viewer)));
        return true;
    }

    private static Text buildViewTitle(SharedView view, ServerPlayerEntity viewer) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        String template;
        if ("ec".equals(view.type())) {
            template = cfg != null ? cfg.chatPlaceholderEnderChestTitle : "{player}'s ender chest";
        } else {
            template = cfg != null ? cfg.chatPlaceholderInventoryTitle : "{player}'s inventory";
        }
        String prepared = template
                .replace("{player}", view.ownerName())
                .replace("{id}", view.id());
        return Text.literal(stripColorFormatting(prepared));
    }

    private static Replacement buildItemReplacement(ServerPlayerEntity sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderItemEnabled) return null;

        ItemStack stack = sender.getMainHandStack();

        if (stack.isEmpty()) {
            if (cfg.chatPlaceholderDenyIfNoItem) {
                sender.sendMessage(Lang.get("chat.placeholder.no_item"), false);
                return null;
            }
            Text empty = Text.literal("[Empty Hand]").styled(s -> s.withColor(TextColor.fromRgb(0xD9D0D5)));
            return new Replacement(empty, "Empty Hand");
        }

        SimpleInventory inv = new SimpleInventory(9);
        ItemStack filler = createFiller();
        for (int i = 0; i < 9; i++) {
            inv.setStack(i, filler.copy());
        }
        inv.setStack(4, stack.copy());

        String id = storeSnapshot(
                stack.getName().copy(),
                inv,
                1,
                "item",
                sender.getName().getString(),
                cfg);

        int count = stack.getCount();
        String plainName = stack.getName().getString();
        String plainLabel = count > 1 ? plainName + " x" + count : plainName;

        MutableText component = Text.literal("[")
                .append(stack.getName().copy())
                .append(count > 1 ? Text.literal(" x" + count) : Text.empty())
                .append(Text.literal("]"))
                .styled(s -> s
                        .withHoverEvent(new HoverEvent.ShowItem(stack))
                        .withClickEvent(new ClickEvent.RunCommand("/viastyle_view " + id)));
        return new Replacement(component, plainLabel);
    }

    private static String stripColorFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String noMiniTags = input.replaceAll("<[^>]+>", "");
        return noMiniTags.replaceAll("(?i)&[0-9A-FK-OR]", "");
    }

    private static Replacement buildPosReplacement(ServerPlayerEntity sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderPosEnabled) return null;

        int x = sender.getBlockX();
        int y = sender.getBlockY();
        int z = sender.getBlockZ();
        String world = sender.getEntityWorld().getRegistryKey().getValue().toString();
        RegistryEntry<net.minecraft.world.biome.Biome> biomeEntry = sender.getEntityWorld().getBiome(sender.getBlockPos());
        String biome = biomeEntry.getKey().map(key -> key.getValue().toString()).orElse("unknown");

        String prepared = cfg.chatPlaceholderPosFormat
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{world}", world)
                .replace("{biome}", biome);

        MutableText component = PlaceholderHelper.parseFormat(prepared, sender).copy();

        if (cfg.chatPlaceholderPosHover != null && !cfg.chatPlaceholderPosHover.isBlank()) {
            String hoverPrepared = cfg.chatPlaceholderPosHover
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z))
                    .replace("{world}", world)
                    .replace("{biome}", biome);
            Text hoverText = Text.literal(stripColorFormatting(hoverPrepared)
                .replace("<newline>", "\n")
                .replace("<br>", "\n"));
            component = component.styled(s -> s.withHoverEvent(new HoverEvent.ShowText(hoverText)));
        }

        if (cfg.chatPlaceholderPosClickSuggest != null && !cfg.chatPlaceholderPosClickSuggest.isBlank()) {
            String command = cfg.chatPlaceholderPosClickSuggest
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z))
                    .replace("{world}", world)
                    .replace("{biome}", biome);
            component = component.styled(s -> s.withClickEvent(new ClickEvent.SuggestCommand(command)));
        }

        return new Replacement(component, x + " " + y + " " + z);
    }

    private static Replacement buildInventoryReplacement(ServerPlayerEntity sender,
                                                         ViaStyleConfig cfg,
                                                         boolean enderChest) {
        return enderChest ? buildEnderReplacement(sender, cfg) : buildInvReplacement(sender, cfg);
    }

    private static boolean hasTokenPermission(ServerPlayerEntity sender, String token, ViaStyleConfig cfg) {
        String node = switch (token) {
            case "item" -> cfg.chatPlaceholderItemPermission;
            case "pos" -> cfg.chatPlaceholderPosPermission;
            case "inv" -> cfg.chatPlaceholderInvPermission;
            case "ec" -> cfg.chatPlaceholderEcPermission;
            default -> "";
        };
        if (node == null || node.isBlank()) return true;
        return LuckPermsHelper.checkPlayerPermission(sender, node, 2);
    }

    private static boolean canUseMiniMessage(ServerPlayerEntity sender, ViaStyleConfig cfg) {
        if (cfg == null || sender == null) return false;
        if (!cfg.chatMiniMessageEnabled) return false;
        if (!cfg.chatMiniMessageRequirePermission) return true;

        String node = cfg.chatMiniMessagePermission;
        if (node == null || node.isBlank()) return true;
        return LuckPermsHelper.checkPlayerPermission(sender, node, 2);
    }

    private static void appendLiteral(MutableText out,
                                      StringBuilder plainOut,
                                      String tokenRaw,
                                      TextColor baseColor) {
        out.append(Text.literal(tokenRaw).styled(s -> s.withColor(baseColor)));
        plainOut.append(tokenRaw);
    }

    private static Replacement buildInvReplacement(ServerPlayerEntity sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderInvEnabled) return null;

        PlayerInventory pInv = sender.getInventory();
        SimpleInventory view = new SimpleInventory(45);
        ItemStack filler = createFiller();

        view.setStack(0, pInv.getStack(39).copy());
        view.setStack(1, pInv.getStack(38).copy());
        view.setStack(2, pInv.getStack(37).copy());
        view.setStack(3, pInv.getStack(36).copy());
        view.setStack(4, filler.copy());
        view.setStack(5, sender.getOffHandStack().copy());
        for (int i = 6; i < 9; i++) {
            view.setStack(i, filler.copy());
        }

        for (int i = 0; i < 36; i++) {
            view.setStack(9 + i, pInv.getStack(i).copy());
        }

        String id = storeSnapshot(
            Text.literal(sender.getName().getString() + " — Inventory").styled(s -> s.withColor(TextColor.fromRgb(0xFFC64C))),
                view,
                5,
                "inv",
                sender.getName().getString(),
                cfg);

        String invTemplate = (cfg.chatPlaceholderInvFormat == null || cfg.chatPlaceholderInvFormat.isBlank())
            ? "[inventory]"
            : cfg.chatPlaceholderInvFormat;
        String invPrepared = invTemplate.replace("{player}", sender.getName().getString());
        MutableText label = PlaceholderHelper.parseFormat(invPrepared, sender).copy();
        String hover = "ru".equalsIgnoreCase(cfg.defaultLanguage)
            ? "Нажмите, чтобы открыть инвентарь"
            : "Click to view inventory";
        label = label.styled(s -> s
            .withHoverEvent(new HoverEvent.ShowText(Text.literal(hover).styled(c -> c.withColor(TextColor.fromRgb(0xD9D0D5)))))
            .withClickEvent(new ClickEvent.RunCommand("/viastyle_view " + id)));
        return new Replacement(label, stripColorFormatting(invPrepared));
    }

    private static Replacement buildEnderReplacement(ServerPlayerEntity sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderEcEnabled) return null;

        EnderChestInventory ender = sender.getEnderChestInventory();
        SimpleInventory view = new SimpleInventory(27);
        for (int i = 0; i < ender.size() && i < 27; i++) {
            view.setStack(i, ender.getStack(i).copy());
        }

        String id = storeSnapshot(
            Text.literal(sender.getName().getString() + " — Ender Chest").styled(s -> s.withColor(TextColor.fromRgb(0xC8A2C8))),
                view,
                3,
                "ec",
                sender.getName().getString(),
                cfg);

        String ecTemplate = (cfg.chatPlaceholderEcFormat == null || cfg.chatPlaceholderEcFormat.isBlank())
            ? "[enderchest]"
            : cfg.chatPlaceholderEcFormat;
        String ecPrepared = ecTemplate.replace("{player}", sender.getName().getString());
        MutableText label = PlaceholderHelper.parseFormat(ecPrepared, sender).copy();
        String hover = "ru".equalsIgnoreCase(cfg.defaultLanguage)
            ? "Нажмите, чтобы открыть эндер-сундук"
            : "Click to view ender chest";
        label = label.styled(s -> s
            .withHoverEvent(new HoverEvent.ShowText(Text.literal(hover).styled(c -> c.withColor(TextColor.fromRgb(0xD9D0D5)))))
            .withClickEvent(new ClickEvent.RunCommand("/viastyle_view " + id)));
        return new Replacement(label, stripColorFormatting(ecPrepared));
    }

    private static String storeSnapshot(Text title,
                                        SimpleInventory inventory,
                                        int rows,
                                        String type,
                                        String ownerName,
                                        ViaStyleConfig cfg) {
        pruneExpiredViews(System.currentTimeMillis());
        String id = randomId();
        long expiresAt = System.currentTimeMillis() + Math.max(1, cfg.chatPlaceholderExpireSeconds) * 1000L;
        sharedViews.put(id, new SharedView(id, type, ownerName, inventory, rows, expiresAt));
        return id;
    }

    private static String randomId() {
        String id;
        do {
            id = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFL);
        } while (sharedViews.containsKey(id));
        return id;
    }

    private static ItemStack createFiller() {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return pane;
    }

    private static void pruneExpiredViews(long nowMillis) {
        sharedViews.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < nowMillis);
    }

    private static final class ReadOnlyContainer extends GenericContainerScreenHandler {
        private ReadOnlyContainer(ScreenHandlerType<?> type,
                                  int syncId,
                                  PlayerInventory playerInventory,
                                  SimpleInventory inventory,
                                  int rows) {
            super(type, syncId, playerInventory, inventory, rows);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }
    }
}
