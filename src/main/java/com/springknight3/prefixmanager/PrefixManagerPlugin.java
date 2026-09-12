package com.springknight3.prefixmanager;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrefixManagerPlugin extends JavaPlugin {
    private static final String NBT_ROOT = "prefix_voucher";
    private static final String NBT_MARKER = "redeemable";
    private static final String FOLLOWS_GUIDELINES_MESSAGE =
        "&d★ Your prefix follows guidelines! It will be applied automatically.&r";
    private static final String DOES_NOT_FOLLOW_GUIDELINES_MESSAGE =
        "&c☠ Your prefix does not follow guidelines. Please change your prefix.&r";
    private static final String UNREDEEMABLE_ITEM_MESSAGE = "&4&l☠ This item is unredeemable...&r";
    private static final String ERROR_MESSAGE = "&4☠ Failed to redeem prefix... You have been refunded.&r";
    private static final String REMOVE_SUCCESS_MESSAGE = "&d★ Your prefix has successfully been removed.&r";
    private static final String REMOVE_FAILURE_MESSAGE = "&4☠ Failed to remove your prefix.&r";
    private static final String DECLINED_REASON_MESSAGE = "&4Reason: &f";
    private static final Set<String> RESERVED_PREFIXES = Set.of(
            "subowner", "admin", "subadmin", "moderator", "staff", "newstaff");
    private static final Set<String> INAPPROPRIATE_WORDS = Set.of(
            "ass", "asshole", "bastard", "bitch", "cunt", "dick", "fag", "faggot",
            "fuck", "nigga", "nigger", "porn", "pussy", "rape", "retard", "shit", "slut", "whore");
    private static final Map<String, String> COLOR_CODES = Map.ofEntries(
            Map.entry("black", "§0"), Map.entry("dark_blue", "§1"), Map.entry("dark_green", "§2"),
            Map.entry("dark_aqua", "§3"), Map.entry("dark_red", "§4"), Map.entry("dark_purple", "§5"),
            Map.entry("gold", "§6"), Map.entry("gray", "§7"), Map.entry("dark_gray", "§8"),
            Map.entry("blue", "§9"), Map.entry("green", "§a"), Map.entry("aqua", "§b"),
            Map.entry("red", "§c"), Map.entry("light_purple", "§d"), Map.entry("yellow", "§e"),
            Map.entry("white", "§f"));

    @Override
    public void onEnable() {
        var command = getCommand("redeemprefix");
        if (command == null) {
            getLogger().severe("/redeemprefix is missing from plugin.yml");
            return;
        }

        command.setExecutor((sender, ignoredCommand, ignoredLabel, args) -> executeRedeem(sender, args));

        var removeCommand = getCommand("removeprefix");
        if (removeCommand == null) {
            getLogger().severe("/removeprefix is missing from plugin.yml");
            return;
        }

        removeCommand.setExecutor((sender, ignoredCommand, ignoredLabel, args) -> executeRemove(sender));
    }

    private boolean executeRedeem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can redeem a prefix voucher.");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("Usage: /redeemprefix <prefix> <color>");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!hasRedeemableNbt(item)) {
            player.sendMessage(colorize(UNREDEEMABLE_ITEM_MESSAGE));
            return true;
        }

        String prefix = args[0];
        String colorCode = toColorCode(args[1]);
        if (colorCode == null) {
            player.sendMessage(colorize(ERROR_MESSAGE));
            return true;
        }

        String declineReason = getDeclineReason(prefix);
        boolean followsGuidelines = declineReason == null;
        String prefixString = colorCode.replace('§', '&') + prefix + "&r • ";
        getLogger().info(prefixString);
        if (followsGuidelines) {
            String command = "lp user " + player.getName() + " meta setprefix 50 \"" + prefixString + "\"";
            boolean commandSucceeded = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (!commandSucceeded) {
                player.sendMessage(colorize(ERROR_MESSAGE));
                return true;
            }

            item.setAmount(item.getAmount() - 1);
            player.sendMessage(colorize(FOLLOWS_GUIDELINES_MESSAGE));
        } else {
            player.sendMessage(colorize(DOES_NOT_FOLLOW_GUIDELINES_MESSAGE + " "
                + DECLINED_REASON_MESSAGE + declineReason));
        }
        return true;
    }

    private boolean executeRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can remove a prefix.");
            return true;
        }

        String command = "lp user " + player.getName() + " meta removeprefix";
        boolean commandSucceeded = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        player.sendMessage(colorize(commandSucceeded ? REMOVE_SUCCESS_MESSAGE : REMOVE_FAILURE_MESSAGE));
        return true;
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String getDeclineReason(String prefix) {
        if (prefix.indexOf('★') >= 0) {
            return "The star character is reserved for official prefixes.";
        }
        if (prefix.isEmpty()) {
            return "The prefix cannot be empty.";
        }
        if (!prefix.matches("[A-Za-z0-9_-]+")) {
            return "Only A-Z, a-z, 0-9, underscore, and hyphen are allowed.";
        }

        String normalized = prefix.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
        if (RESERVED_PREFIXES.contains(normalized)) {
            return "That prefix is reserved for staff or official use.";
        }

        for (String inappropriateWord : INAPPROPRIATE_WORDS) {
            if (normalized.contains(inappropriateWord)) {
                return "That prefix contains inappropriate language.";
            }
        }
        return null;
    }

    private String toColorCode(String color) {
        String normalized = color.toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.length() == 2 && (normalized.charAt(0) == '&' || normalized.charAt(0) == '§')) {
            char code = Character.toLowerCase(normalized.charAt(1));
            return "0123456789abcdefklmnor".indexOf(code) >= 0 ? "§" + code : null;
        }
        return COLOR_CODES.get(normalized);
    }

    private boolean hasRedeemableNbt(ItemStack item) {
        return getPrefixVoucherNbt(item, NBT_MARKER) != null;
    }

    private Object getPrefixVoucherNbt(ItemStack item, String key) {
        try {
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Object nmsItem = craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
            Class<?> dataComponentType = Class.forName("net.minecraft.core.component.DataComponentType");
            Class<?> dataComponents = Class.forName("net.minecraft.core.component.DataComponents");
            Object customDataType = dataComponents.getField("CUSTOM_DATA").get(null);
            Object customData = nmsItem.getClass().getMethod("get", dataComponentType)
                    .invoke(nmsItem, customDataType);
            if (customData == null) {
                return null;
            }

            Object rootTag = customData.getClass().getMethod("copyTag").invoke(customData);
            Object voucherTag = rootTag.getClass().getMethod("get", String.class).invoke(rootTag, NBT_ROOT);
            return voucherTag == null ? null : voucherTag.getClass().getMethod("get", String.class).invoke(voucherTag, key);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not inspect prefix voucher NBT: " + exception.getMessage());
            return null;
        }
    }
}