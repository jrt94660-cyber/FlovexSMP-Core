package net.flovex.core.shop;

import net.flovex.core.FlovexCore;
import net.flovex.core.economy.EconomyHook;
import net.flovex.core.util.MoneyFormat;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class ShopService implements Listener {
    private enum Menu { SHOP, SELL, CONFIRM_BUY, CONFIRM_SELL }
    private record Pending(boolean buy, List<ItemStack> items, Material material, int amount, double total, Inventory sourceInventory) {}
    private record View(Menu menu, int page, List<Material> materials, List<Integer> inventorySlots) {}

    private final FlovexCore plugin;
    private final EconomyHook economy;
    private final Map<Material, Double> worth = new EnumMap<>(Material.class);
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final double shopMultiplier;
    private final double sellFraction;
    private final Set<String> blacklist = new HashSet<>();
    private final int maxItems;
    private final boolean autoBalanceMissing;
    private final double autoBalanceMultiplier;
    private final String shopTitle;

    public ShopService(FlovexCore plugin, EconomyHook economy,
                       org.bukkit.configuration.file.FileConfiguration shop,
                       org.bukkit.configuration.file.FileConfiguration worthConfig) {
        this.plugin = plugin;
        this.economy = economy;
        this.shopMultiplier = Math.max(0, shop.getDouble("QUICK-BUY.PRICING.WORTH-MULTIPLIER", 20.0));
        this.sellFraction = Math.max(0, Math.min(1, plugin.getConfig().getDouble("sell.price-fraction", 0.5)));
        this.blacklist.addAll(shop.getStringList("QUICK-BUY.CHOOSE-ITEM.BLACKLIST").stream().map(x -> x.toUpperCase(Locale.ROOT)).toList());
        this.maxItems = Math.max(45, shop.getInt("QUICK-BUY.CHOOSE-ITEM.MAX-ITEMS", 2000));
        this.autoBalanceMissing = shop.getBoolean("QUICK-BUY.PRICING.AUTO-BALANCE-MISSING", true);
        this.autoBalanceMultiplier = Math.max(0, shop.getDouble("QUICK-BUY.PRICING.AUTO-BALANCE-MULTIPLIER", shopMultiplier));
        this.shopTitle = ChatColor.translateAlternateColorCodes('&', shop.getString("QUICK-BUY.TITLE", "&8Quick Buy"));
        for (String category : worthConfig.getConfigurationSection("TYPE").getKeys(false)) {
            var section = worthConfig.getConfigurationSection("TYPE." + category);
            if (section == null) continue;
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null && material.isItem()) worth.putIfAbsent(material, section.getDouble(key));
            }
        }
        if (autoBalanceMissing) {
            double ratio = shopMultiplier <= 0 ? 1.0 : autoBalanceMultiplier / shopMultiplier;
            for (Material material : Material.values()) {
                if (material.isItem() && !blocked(material) && !worth.containsKey(material)) {
                    worth.put(material, estimateWorth(material) * ratio);
                }
            }
        }
    }

    private double estimateWorth(Material material) {
        String name = material.name();
        if (name.contains("DIAMOND") || name.contains("NETHERITE") || name.endsWith("_INGOT") || name.endsWith("_GEM") || name.endsWith("_ORE")) return 12.0;
        if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) return 2.0;
        if (name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) return 20.0;
        if (name.contains("POTION") || name.contains("TOTEM") || name.contains("ELYTRA") || name.contains("TRIDENT") || name.contains("MACE")) return 30.0;
        if (name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO") || name.contains("BEETROOT") || name.contains("BREAD") || name.contains("APPLE")) return 1.5;
        if (name.endsWith("_BLOCK")) return 3.0;
        return 1.0;
    }

    public double shopMultiplier() { return shopMultiplier; }
    public double sellFraction() { return sellFraction; }
    public double buyUnitPrice(Material material) { return worth.getOrDefault(material, 0.0) * shopMultiplier; }
    public double sellUnitPrice(Material material) { return buyUnitPrice(material) * sellFraction; }
    public boolean isSellable(Material material) { return sellUnitPrice(material) > 0 && material.isItem() && !blocked(material); }

    public void openShop(Player player, int page) {
        List<Material> materials = worth.keySet().stream().filter(m -> m.isItem() && buyUnitPrice(m) > 0)
                .sorted(Comparator.comparing(Material::name)).toList();
        int maxPage = Math.max(0, (materials.size() - 1) / 45);
        page = Math.max(0, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(null, 54, shopTitle);
        int start = page * 45;
        for (int i = 0; i < 45 && start + i < materials.size(); i++) {
            Material material = materials.get(start + i);
            ItemStack item = new ItemStack(material, Math.min(material.getMaxStackSize(), 64));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.WHITE + pretty(material));
            meta.setLore(List.of(ChatColor.GRAY + "Quantity: " + item.getAmount(),
                    ChatColor.GREEN + "$ " + ChatColor.WHITE + money(buyUnitPrice(material) * item.getAmount()),
                    ChatColor.YELLOW + "Click to review purchase"));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        inv.setItem(45, button(Material.ARROW, "&fPrevious Page", "&7Page " + (page + 1)));
        inv.setItem(49, button(Material.GOLD_INGOT, "&6Shop", "&7Prices are based on worth x " + shopMultiplier));
        inv.setItem(53, button(Material.ARROW, "&fNext Page", "&7Page " + (page + 1)));
        views.put(player.getUniqueId(), new View(Menu.SHOP, page, materials, List.of()));
        player.openInventory(inv);
    }

    public void openSell(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Flovex Sell");
        List<Integer> slots = new ArrayList<>();
        int display = 0;
        for (int source = 0; source < player.getInventory().getStorageContents().length && display < 45; source++) {
            ItemStack stack = player.getInventory().getItem(source);
            if (stack == null || stack.getType().isAir() || !isSellable(stack.getType())) continue;
            ItemStack shown = stack.clone();
            ItemMeta meta = shown.getItemMeta();
            meta.setLore(List.of(ChatColor.GREEN + "$ " + ChatColor.WHITE + money(sellUnitPrice(stack.getType()) * stack.getAmount()),
                    ChatColor.GRAY + "Quantity: " + stack.getAmount(),
                    ChatColor.YELLOW + "Click to review sale"));
            shown.setItemMeta(meta);
            inv.setItem(display++, shown);
            slots.add(source);
        }
        inv.setItem(49, button(Material.GOLD_INGOT, "&6Sell Items", "&7You receive 50% of shop price"));
        views.put(player.getUniqueId(), new View(Menu.SELL, 0, List.of(), slots));
        player.openInventory(inv);
    }

    public void sellHand(Player player, int requested) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !isSellable(hand.getType())) { player.sendMessage("§cThat item cannot be sold."); return; }
        int amount = Math.max(1, Math.min(requested, hand.getAmount()));
        ItemStack snapshot = hand.clone(); snapshot.setAmount(amount);
        confirm(player, new Pending(false, List.of(snapshot), null, amount, sellUnitPrice(hand.getType()) * amount, null));
    }

    public void sellContainer(Player player, Inventory container) {
        List<ItemStack> items = new ArrayList<>();
        double total = 0;
        for (ItemStack stack : container.getContents()) {
            if (stack == null || stack.getType().isAir() || !isSellable(stack.getType())) continue;
            items.add(stack.clone());
            total += sellUnitPrice(stack.getType()) * stack.getAmount();
        }
        if (items.isEmpty()) { player.sendMessage("§cThis container has no sellable items."); return; }
        confirm(player, new Pending(false, items, null, items.stream().mapToInt(ItemStack::getAmount).sum(), total, container));
    }

    public void sellAll(Player player) {
        List<ItemStack> items = new ArrayList<>();
        double total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir() || !isSellable(stack.getType())) continue;
            items.add(stack.clone());
            total += sellUnitPrice(stack.getType()) * stack.getAmount();
        }
        if (items.isEmpty()) { player.sendMessage("§cYou have no sellable items."); return; }
        confirm(player, new Pending(false, items, null, items.stream().mapToInt(ItemStack::getAmount).sum(), total, null));
    }

    private void confirm(Player player, Pending transaction) {
        pending.put(player.getUniqueId(), transaction);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + (transaction.buy() ? "Confirm Purchase" : "Confirm Sale"));
        if (transaction.buy()) {
            inv.setItem(13, new ItemStack(transaction.material(), transaction.amount()));
        } else {
            ItemStack display = transaction.items().isEmpty() ? new ItemStack(Material.PAPER) : transaction.items().getFirst().clone();
            ItemMeta meta = display.getItemMeta();
            meta.setLore(List.of(ChatColor.GRAY + "Items: " + transaction.amount(),
                    ChatColor.GRAY + "Sell value: " + ChatColor.GREEN + "$ " + ChatColor.WHITE + money(transaction.total())));
            display.setItemMeta(meta);
            inv.setItem(13, display);
        }
        inv.setItem(11, button(Material.LIME_DYE, "&aConfirm", "&7Total: $" + money(transaction.total())));
        inv.setItem(15, button(Material.RED_DYE, "&cCancel", "&7Return without trading"));
        views.put(player.getUniqueId(), new View(transaction.buy() ? Menu.CONFIRM_BUY : Menu.CONFIRM_SELL, 0, List.of(), List.of()));
        player.openInventory(inv);
    }

    private void buy(Player player, Material material) {
        int amount = Math.min(material.getMaxStackSize(), 64);
        double total = buyUnitPrice(material) * amount;
        if (!economy.available()) { player.sendMessage("§cEconomy provider unavailable."); return; }
        confirm(player, new Pending(true, List.of(), material, amount, total, null));
    }

    private void finish(Player player) {
        Pending transaction = pending.remove(player.getUniqueId());
        if (transaction == null) return;
        if (transaction.buy()) {
            if (!economy.withdraw(player.getUniqueId(), transaction.total())) {
                player.sendMessage("§cInsufficient funds.");
                return;
            }
            ItemStack item = new ItemStack(transaction.material(), transaction.amount());
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                if (plugin.getConfig().getBoolean("shop.drop-items-if-full", true)) overflow.values().forEach(x -> player.getWorld().dropItemNaturally(player.getLocation(), x));
                else {
                    economy.deposit(player.getUniqueId(), transaction.total());
                    player.sendMessage("§cYour inventory is full. Purchase refunded.");
                    return;
                }
            }
            player.sendMessage("§aPurchased " + transaction.amount() + " " + pretty(transaction.material()) + " for §f$" + money(transaction.total()));
        } else {
            if (!removeSnapshots(player, transaction.items(), transaction.sourceInventory())) { player.sendMessage("§cThe items changed. Sale cancelled."); return; }
            if (!economy.deposit(player.getUniqueId(), transaction.total())) {
                transaction.items().forEach(x -> { if (transaction.sourceInventory() == null) player.getInventory().addItem(x); else transaction.sourceInventory().addItem(x); });
                player.sendMessage("§cCould not deposit money. Items returned.");
                return;
            }
            plugin.getStorage().update("INSERT INTO sell_history(uuid,amount,total,created) VALUES(?,?,?,?)",
                    player.getUniqueId().toString(), transaction.amount(), transaction.total(), System.currentTimeMillis());
            plugin.getStorage().update("INSERT INTO sell_stats(uuid,items_sold,total_earned) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET items_sold=items_sold+excluded.items_sold,total_earned=total_earned+excluded.total_earned",
                    player.getUniqueId().toString(), transaction.amount(), transaction.total());
            player.sendMessage("§aSold " + transaction.amount() + " items for §f$" + money(transaction.total()));
        }
    }

    private boolean removeSnapshots(Player player, List<ItemStack> snapshots, Inventory source) {
        Inventory inventory = source == null ? player.getInventory() : source;
        for (ItemStack wanted : snapshots) {
            int found = 0;
            for (ItemStack current : inventory.getContents()) if (current != null && current.isSimilar(wanted)) found += current.getAmount();
            if (found < wanted.getAmount()) return false;
        }
        for (ItemStack wanted : snapshots) {
            int remaining = wanted.getAmount();
            for (int slot = 0; slot < inventory.getContents().length && remaining > 0; slot++) {
                ItemStack current = inventory.getItem(slot);
                if (current == null || !current.isSimilar(wanted)) continue;
                int take = Math.min(remaining, current.getAmount());
                current.setAmount(current.getAmount() - take);
                if (current.getAmount() <= 0) inventory.setItem(slot, null);
                else inventory.setItem(slot, current);
                remaining -= take;
            }
        }
        return true;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        View view = views.get(player.getUniqueId());
        if (view == null) return;
        String title = event.getView().getTitle();
        if (!title.equals(shopTitle) &&
                !title.startsWith(ChatColor.DARK_GRAY + "Flovex Sell") &&
                !title.startsWith(ChatColor.DARK_GRAY + "Confirm Purchase") &&
                !title.startsWith(ChatColor.DARK_GRAY + "Confirm Sale")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        switch (view.menu()) {
            case SHOP -> {
                if (slot == 45 && view.page() > 0) { openShop(player, view.page() - 1); return; }
                if (slot == 53) { openShop(player, view.page() + 1); return; }
                if (slot >= 0 && slot < 45 && slot + view.page() * 45 < view.materials().size()) buy(player, view.materials().get(slot + view.page() * 45));
            }
            case SELL -> {
                if (slot >= 0 && slot < view.inventorySlots().size()) {
                    ItemStack stack = player.getInventory().getItem(view.inventorySlots().get(slot));
                    if (stack != null && isSellable(stack.getType())) {
                        ItemStack snapshot = stack.clone();
                        confirm(player, new Pending(false, List.of(snapshot), null, snapshot.getAmount(), sellUnitPrice(snapshot.getType()) * snapshot.getAmount(), null));
                    }
                }
            }
            case CONFIRM_BUY, CONFIRM_SELL -> {
                if (slot == 11) { player.closeInventory(); finish(player); }
                else if (slot == 15) { pending.remove(player.getUniqueId()); views.remove(player.getUniqueId()); player.closeInventory(); }
            }
        }
    }

    public void close(Player player) { views.remove(player.getUniqueId()); }
    public double worth(Material material) { return worth.getOrDefault(material, 0.0); }
    public List<String> materials() { return worth.keySet().stream().filter(m -> !blocked(m)).map(Material::name).sorted().toList(); }
    private boolean blocked(Material material) {
        String name = material.name();
        if (name.endsWith("_SPAWN_EGG") || name.equals("SPAWNER") || name.equals("TRIAL_SPAWNER") || name.equals("VAULT")) return true;
        for (String rule : blacklist) {
            if (rule.endsWith("*") && name.startsWith(rule.substring(0, rule.length() - 1))) return true;
            if (name.equals(rule)) return true;
        }
        return false;
    }
    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore)));
        item.setItemMeta(meta); return item;
    }
    private String pretty(Material material) { String s=material.name().toLowerCase(Locale.ROOT).replace('_',' '); return Character.toUpperCase(s.charAt(0))+s.substring(1); }
    private String money(double value) { return MoneyFormat.compact(value); }
}
