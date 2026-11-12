package net.flovex.core.shop;

import net.flovex.core.FlovexCore;
import net.flovex.core.economy.EconomyHook;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class ShopService implements Listener {
    private enum Menu { SHOP, SELL, CONFIRM_BUY, CONFIRM_SELL }
    private record Pending(boolean buy, List<ItemStack> items, Material material, int amount, double total) {}
    private record View(Menu menu, int page, List<Material> materials, List<Integer> inventorySlots) {}

    private final FlovexCore plugin;
    private final EconomyHook economy;
    private final Map<Material, Double> worth = new EnumMap<>(Material.class);
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final double shopMultiplier;
    private final double sellFraction;

    public ShopService(FlovexCore plugin, EconomyHook economy,
                       org.bukkit.configuration.file.FileConfiguration shop,
                       org.bukkit.configuration.file.FileConfiguration worthConfig) {
        this.plugin = plugin;
        this.economy = economy;
        this.shopMultiplier = Math.max(0, shop.getDouble("QUICK-BUY.PRICING.WORTH-MULTIPLIER", 20.0));
        this.sellFraction = Math.max(0, Math.min(1, plugin.getConfig().getDouble("sell.price-fraction", 0.5)));
        for (String category : worthConfig.getConfigurationSection("TYPE").getKeys(false)) {
            var section = worthConfig.getConfigurationSection("TYPE." + category);
            if (section == null) continue;
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null && material.isItem()) worth.putIfAbsent(material, section.getDouble(key));
            }
        }
    }

    public double buyUnitPrice(Material material) { return worth.getOrDefault(material, 0.0) * shopMultiplier; }
    public double sellUnitPrice(Material material) { return buyUnitPrice(material) * sellFraction; }
    public boolean isSellable(Material material) { return sellUnitPrice(material) > 0 && material.isItem(); }

    public void openShop(Player player, int page) {
        List<Material> materials = worth.keySet().stream().filter(m -> m.isItem() && buyUnitPrice(m) > 0)
                .sorted(Comparator.comparing(Material::name)).toList();
        int maxPage = Math.max(0, (materials.size() - 1) / 45);
        page = Math.max(0, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Flovex Shop");
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
        confirm(player, new Pending(false, List.of(snapshot), null, amount, sellUnitPrice(hand.getType()) * amount));
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
        confirm(player, new Pending(false, items, null, items.stream().mapToInt(ItemStack::getAmount).sum(), total));
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
        confirm(player, new Pending(true, List.of(), material, amount, total));
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
            if (!removeSnapshots(player, transaction.items())) { player.sendMessage("§cYour inventory changed. Sale cancelled."); return; }
            if (!economy.deposit(player.getUniqueId(), transaction.total())) {
                transaction.items().forEach(x -> player.getInventory().addItem(x));
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

    private boolean removeSnapshots(Player player, List<ItemStack> snapshots) {
        for (ItemStack wanted : snapshots) {
            int found = 0;
            for (ItemStack current : player.getInventory().getStorageContents()) if (current != null && current.isSimilar(wanted)) found += current.getAmount();
            if (found < wanted.getAmount()) return false;
        }
        for (ItemStack wanted : snapshots) {
            int remaining = wanted.getAmount();
            for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
                ItemStack current = player.getInventory().getItem(slot);
                if (current == null || !current.isSimilar(wanted)) continue;
                int take = Math.min(remaining, current.getAmount());
                current.setAmount(current.getAmount() - take);
                if (current.getAmount() <= 0) player.getInventory().setItem(slot, null);
                else player.getInventory().setItem(slot, current);
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
        if (!title.startsWith(ChatColor.DARK_GRAY + "Flovex Shop") &&
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
                        confirm(player, new Pending(false, List.of(snapshot), null, snapshot.getAmount(), sellUnitPrice(snapshot.getType()) * snapshot.getAmount()));
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
    public List<String> materials() { return worth.keySet().stream().map(Material::name).sorted().toList(); }
    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore)));
        item.setItemMeta(meta); return item;
    }
    private String pretty(Material material) { String s=material.name().toLowerCase(Locale.ROOT).replace('_',' '); return Character.toUpperCase(s.charAt(0))+s.substring(1); }
    private String money(double value) { return String.format(Locale.US, "%,.2f", value); }
}
