package com.viameowts.viastyle;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    public record ProcessedMessage(Component component, String plainText) {}

    private record Replacement(Component component, String plainText) {}

    private record SharedView(
            String id,
            String type,
            String ownerName,
            SimpleContainer inventory,
            int rows,
            long expiresAtMillis
    ) {}

    public static ProcessedMessage processMessage(String message,
                                                  ServerPlayer sender,
                                                  MinecraftServer server,
                                                  TextColor baseColor) {
        ViaStyleConfig cfg = viaStyle.CONFIG;
        boolean useMiniMessage = canUseMiniMessage(sender, cfg);
        if (cfg == null || !cfg.chatPlaceholdersEnabled || message == null || message.isEmpty()) {
            Component plain = MentionHandler.highlightMentions(message == null ? "" : message, baseColor, server, sender, useMiniMessage);
            return new ProcessedMessage(plain, message == null ? "" : message);
        }

        Matcher matcher = TOKEN_PATTERN.matcher(message);
        if (!matcher.find()) {
            Component plain = MentionHandler.highlightMentions(message, baseColor, server, sender, useMiniMessage);
            return new ProcessedMessage(plain, message);
        }

        int tokenCount = 0;
        matcher.reset();
        while (matcher.find()) tokenCount++;

        long now = System.currentTimeMillis();
        int cooldownSeconds = Math.max(0, cfg.chatPlaceholderCooldownSeconds);
        Long lastUse = lastUseMillis.get(sender.getUUID());
        if (cooldownSeconds > 0 && lastUse != null) {
            long delta = now - lastUse;
            long cooldownMillis = cooldownSeconds * 1000L;
            if (delta < cooldownMillis) {
                long left = Math.max(1L, (cooldownMillis - delta + 999L) / 1000L);
                sender.sendSystemMessage(Lang.getMutable("chat.placeholder.cooldown")
                        .append(Component.literal(String.valueOf(left))));
                Component plain = MentionHandler.highlightMentions(message, baseColor, server, sender, useMiniMessage);
                return new ProcessedMessage(plain, message);
            }
        }

        int limit = cfg.chatPlaceholderMaxPerMessage <= 0
                ? Integer.MAX_VALUE : cfg.chatPlaceholderMaxPerMessage;

        MutableComponent out = Component.empty();
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
                    sender.sendSystemMessage(Lang.get("chat.placeholder.no_permission"));
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
            lastUseMillis.put(sender.getUUID(), now);
        }

        pruneExpiredViews(now);
        return new ProcessedMessage(out, plainOut.toString());
    }

    public static boolean openSharedView(ServerPlayer viewer, String id) {
        if (id == null || id.isBlank()) return false;
        SharedView view = sharedViews.get(id);
        if (view == null) return false;

        long now = System.currentTimeMillis();
        if (view.expiresAtMillis() < now) {
            sharedViews.remove(id);
            return false;
        }

        MenuType<?> type = switch (view.rows()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };

        viewer.openMenu(new SimpleMenuProvider((syncId, playerInventory, player) ->
                new ReadOnlyContainer(type, syncId, playerInventory, view.inventory(), view.rows()),
                buildViewTitle(view, viewer)));
        return true;
    }

    private static Component buildViewTitle(SharedView view, ServerPlayer viewer) {
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
        return Component.literal(stripColorFormatting(prepared));
    }

    private static Replacement buildItemReplacement(ServerPlayer sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderItemEnabled) return null;

        ItemStack stack = sender.getMainHandItem();

        if (stack.isEmpty()) {
            if (cfg.chatPlaceholderDenyIfNoItem) {
                sender.sendSystemMessage(Lang.get("chat.placeholder.no_item"));
                return null;
            }
            Component empty = Component.literal("[Empty Hand]").withStyle(s -> s.withColor(TextColor.fromRgb(0xD9D0D5)));
            return new Replacement(empty, "Empty Hand");
        }

        SimpleContainer inv = new SimpleContainer(9);
        ItemStack filler = createFiller();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler.copy());
        }
        inv.setItem(4, stack.copy());

        String id = storeSnapshot(
                stack.getHoverName().copy(),
                inv,
                1,
                "item",
                sender.getName().getString(),
                cfg);

        int count = stack.getCount();
        String plainName = stack.getHoverName().getString();
        String plainLabel = count > 1 ? plainName + " x" + count : plainName;

        MutableComponent component = Component.literal("[")
                .append(stack.getHoverName().copy())
                .append(count > 1 ? Component.literal(" x" + count) : Component.empty())
                .append(Component.literal("]"))
                .withStyle(s -> s
                        .withHoverEvent(new HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack)))
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

    private static Replacement buildPosReplacement(ServerPlayer sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderPosEnabled) return null;

        int x = sender.getBlockX();
        int y = sender.getBlockY();
        int z = sender.getBlockZ();
        String world = sender.level().dimension().identifier().toString();
        Holder<net.minecraft.world.level.biome.Biome> biomeEntry = sender.level().getBiome(sender.blockPosition());
        String biome = biomeEntry.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");

        String prepared = cfg.chatPlaceholderPosFormat
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{world}", world)
                .replace("{biome}", biome);

        MutableComponent component = PlaceholderHelper.parseFormat(prepared, sender).copy();

        if (cfg.chatPlaceholderPosHover != null && !cfg.chatPlaceholderPosHover.isBlank()) {
            String hoverPrepared = cfg.chatPlaceholderPosHover
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z))
                    .replace("{world}", world)
                    .replace("{biome}", biome);
            Component hoverText = Component.literal(stripColorFormatting(hoverPrepared)
                .replace("<newline>", "\n")
                .replace("<br>", "\n"));
            component = component.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hoverText)));
        }

        if (cfg.chatPlaceholderPosClickSuggest != null && !cfg.chatPlaceholderPosClickSuggest.isBlank()) {
            String command = cfg.chatPlaceholderPosClickSuggest
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z))
                    .replace("{world}", world)
                    .replace("{biome}", biome);
            component = component.withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand(command)));
        }

        return new Replacement(component, x + " " + y + " " + z);
    }

    private static Replacement buildInventoryReplacement(ServerPlayer sender,
                                                         ViaStyleConfig cfg,
                                                         boolean enderChest) {
        return enderChest ? buildEnderReplacement(sender, cfg) : buildInvReplacement(sender, cfg);
    }

    private static boolean hasTokenPermission(ServerPlayer sender, String token, ViaStyleConfig cfg) {
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

    private static boolean canUseMiniMessage(ServerPlayer sender, ViaStyleConfig cfg) {
        if (cfg == null || sender == null) return false;
        if (!cfg.chatMiniMessageEnabled) return false;
        if (!cfg.chatMiniMessageRequirePermission) return true;

        String node = cfg.chatMiniMessagePermission;
        if (node == null || node.isBlank()) return true;
        return LuckPermsHelper.checkPlayerPermission(sender, node, 2);
    }

    private static void appendLiteral(MutableComponent out,
                                      StringBuilder plainOut,
                                      String tokenRaw,
                                      TextColor baseColor) {
        out.append(Component.literal(tokenRaw).withStyle(s -> s.withColor(baseColor)));
        plainOut.append(tokenRaw);
    }

    private static Replacement buildInvReplacement(ServerPlayer sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderInvEnabled) return null;

        Inventory pInv = sender.getInventory();
        SimpleContainer view = new SimpleContainer(45);
        ItemStack filler = createFiller();

        view.setItem(0, pInv.getItem(39).copy());
        view.setItem(1, pInv.getItem(38).copy());
        view.setItem(2, pInv.getItem(37).copy());
        view.setItem(3, pInv.getItem(36).copy());
        view.setItem(4, filler.copy());
        view.setItem(5, sender.getOffhandItem().copy());
        for (int i = 6; i < 9; i++) {
            view.setItem(i, filler.copy());
        }

        for (int i = 0; i < 36; i++) {
            view.setItem(9 + i, pInv.getItem(i).copy());
        }

        String id = storeSnapshot(
            Component.literal(sender.getName().getString() + " — Inventory").withStyle(s -> s.withColor(TextColor.fromRgb(0xFFC64C))),
                view,
                5,
                "inv",
                sender.getName().getString(),
                cfg);

        String invTemplate = (cfg.chatPlaceholderInvFormat == null || cfg.chatPlaceholderInvFormat.isBlank())
            ? "[inventory]"
            : cfg.chatPlaceholderInvFormat;
        String invPrepared = invTemplate.replace("{player}", sender.getName().getString());
        MutableComponent label = PlaceholderHelper.parseFormat(invPrepared, sender).copy();
        String hover = "ru".equalsIgnoreCase(cfg.defaultLanguage)
            ? "Нажмите, чтобы открыть инвентарь"
            : "Click to view inventory";
        label = label.withStyle(s -> s
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover).withStyle(c -> c.withColor(TextColor.fromRgb(0xD9D0D5)))))
            .withClickEvent(new ClickEvent.RunCommand("/viastyle_view " + id)));
        return new Replacement(label, stripColorFormatting(invPrepared));
    }

    private static Replacement buildEnderReplacement(ServerPlayer sender, ViaStyleConfig cfg) {
        if (!cfg.chatPlaceholderEcEnabled) return null;

        PlayerEnderChestContainer ender = sender.getEnderChestInventory();
        SimpleContainer view = new SimpleContainer(27);
        for (int i = 0; i < ender.getContainerSize() && i < 27; i++) {
            view.setItem(i, ender.getItem(i).copy());
        }

        String id = storeSnapshot(
            Component.literal(sender.getName().getString() + " — Ender Chest").withStyle(s -> s.withColor(TextColor.fromRgb(0xC8A2C8))),
                view,
                3,
                "ec",
                sender.getName().getString(),
                cfg);

        String ecTemplate = (cfg.chatPlaceholderEcFormat == null || cfg.chatPlaceholderEcFormat.isBlank())
            ? "[enderchest]"
            : cfg.chatPlaceholderEcFormat;
        String ecPrepared = ecTemplate.replace("{player}", sender.getName().getString());
        MutableComponent label = PlaceholderHelper.parseFormat(ecPrepared, sender).copy();
        String hover = "ru".equalsIgnoreCase(cfg.defaultLanguage)
            ? "Нажмите, чтобы открыть эндер-сундук"
            : "Click to view ender chest";
        label = label.withStyle(s -> s
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover).withStyle(c -> c.withColor(TextColor.fromRgb(0xD9D0D5)))))
            .withClickEvent(new ClickEvent.RunCommand("/viastyle_view " + id)));
        return new Replacement(label, stripColorFormatting(ecPrepared));
    }

    private static String storeSnapshot(Component title,
                                        SimpleContainer inventory,
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
        ItemStack pane = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        pane.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));
        return pane;
    }

    private static void pruneExpiredViews(long nowMillis) {
        sharedViews.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < nowMillis);
    }

    private static final class ReadOnlyContainer extends ChestMenu {
        private ReadOnlyContainer(MenuType<?> type,
                                  int syncId,
                                  Inventory playerInventory,
                                  SimpleContainer inventory,
                                  int rows) {
            super(type, syncId, playerInventory, inventory, rows);
        }

        @Override
        public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }
    }
}
