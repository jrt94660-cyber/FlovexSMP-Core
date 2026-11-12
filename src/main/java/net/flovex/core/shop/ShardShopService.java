package net.flovex.core.shop;

import net.flovex.core.FlovexCore;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.util.*;

public final class ShardShopService implements Listener {
    private record Product(String key, Material material, String name, List<String> lore, List<String> enchants,
                           int price, int slot, String command, boolean giveItem, String tool, long duration) {}
    private final FlovexCore plugin;
    private final Map<UUID, Product> pending = new HashMap<>();
    private final Map<String, Product> products = new LinkedHashMap<>();
    private final NamespacedKey toolKey;

    public ShardShopService(FlovexCore plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "amethyst_tool");
        for (String key : config.getKeys(false)) {
            if (!key.endsWith("-ITEM")) continue;
            ConfigurationSection item = config.getConfigurationSection(key);
            if (item == null) continue;
            Material material = Material.matchMaterial(item.getString("MATERIAL", "PAPER"));
            if (material == null || !material.isItem()) continue;
            products.put(key, new Product(key, material, item.getString("DISPLAY-NAME", "&f" + key),
                item.getStringList("LORE"), item.getStringList("ENCHANTMENTS"),
                Math.max(0, item.getInt("PRICE-PER-UNIT", 0)), item.getInt("SLOT", 0),
                item.getString("COMMAND", ""), item.getBoolean("GIVE-ITEM", true),
                item.getString("AMETHYST-TOOL", ""), item.getLong("AMETHYST-DURATION", 0)));
        }
    }

    public long balance(UUID uuid) {
        plugin.getStorage().update("INSERT OR IGNORE INTO player_shards(uuid,balance) VALUES(?,0)", uuid.toString());
        List<Map<String,Object>> rows = plugin.getStorage().rows("SELECT balance FROM player_shards WHERE uuid=?", uuid.toString());
        return rows.isEmpty() ? 0L : ((Number) rows.getFirst().get("balance")).longValue();
    }
    public void add(UUID uuid, long amount) {
        if (amount <= 0) return;
        plugin.getStorage().update("INSERT INTO player_shards(uuid,balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=balance+excluded.balance", uuid.toString(), amount);
    }
    public boolean take(UUID uuid, long amount) {
        if (amount <= 0) return false;
        return plugin.getStorage().update("UPDATE player_shards SET balance=balance-? WHERE uuid=? AND balance>=?", amount, uuid.toString(), amount) == 1;
    }
    public boolean set(UUID uuid, long amount) {
        if (amount < 0) return false;
        plugin.getStorage().update("INSERT INTO player_shards(uuid,balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance", uuid.toString(), amount);
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Flovex Shard Shop");
        for (Product product : products.values()) {
            ItemStack item = createItem(product);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + String.format(Locale.US, "%,d Shards", product.price()));
            lore.add(ChatColor.YELLOW + "Click to review purchase");
            meta.setLore(lore);
            item.setItemMeta(meta);
            int slot = product.slot();
            if (slot >= 0 && slot < 45) inv.setItem(slot, item);
        }
        inv.setItem(49, button(Material.AMETHYST_SHARD, "&fYour Shards", "&d" + String.format(Locale.US, "%,d", balance(player.getUniqueId())) + " Shards"));
        inv.setItem(50, button(Material.PLAYER_HEAD, "&fRanks", "&7Rank purchases are managed separately"));
        player.openInventory(inv);
    }

    private void confirm(Player player, Product product) {
        pending.put(player.getUniqueId(), product);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Confirm Shard Purchase");
        inv.setItem(13, createItem(product));
        inv.setItem(11, button(Material.LIME_DYE, "&aConfirm", "&7Cost: " + String.format(Locale.US, "%,d", product.price()) + " Shards"));
        inv.setItem(15, button(Material.RED_DYE, "&cCancel", "&7No shards will be charged"));
        player.openInventory(inv);
    }

    private void purchase(Player player) {
        Product product = pending.remove(player.getUniqueId());
        if (product == null) return;
        if (!take(player.getUniqueId(), product.price())) {
            player.sendMessage("§cYou do not have enough shards.");
            return;
        }
        if (!product.command().isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), product.command().replace("%player%", player.getName()));
        }
        if (product.giveItem()) {
            ItemStack item = createItem(product);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        player.sendMessage("§aPurchased " + ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', product.name())) +
            " for §d" + String.format(Locale.US, "%,d", product.price()) + " Shards.");
    }

    private ItemStack createItem(Product product) {
        ItemStack stack = new ItemStack(product.material());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', product.name()));
        if (!product.lore().isEmpty()) meta.setLore(product.lore().stream().map(x -> ChatColor.translateAlternateColorCodes('&', x)).toList());
        for (String raw : product.enchants()) {
            String[] parts = raw.split(":");
            if (parts.length != 2) continue;
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (enchantment == null) continue;
            try { meta.addEnchant(enchantment, Integer.parseInt(parts[1]), true); } catch (NumberFormatException ignored) {}
        }
        if (!product.tool().isBlank()) meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, product.tool());
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(ChatColor.DARK_GRAY + "Flovex Shard Shop") && !title.equals(ChatColor.DARK_GRAY + "Confirm Shard Purchase")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        if (title.equals(ChatColor.DARK_GRAY + "Confirm Shard Purchase")) {
            if (slot == 11) { player.closeInventory(); purchase(player); }
            else if (slot == 15) { pending.remove(player.getUniqueId()); player.closeInventory(); }
            return;
        }
        for (Product product : products.values()) if (product.slot() == slot) { confirm(player, product); return; }
    }

    @EventHandler public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) return;
        int reward = Math.max(0, plugin.getConfig().getInt("shards.per-kill", 1));
        if (reward == 0) return;
        add(killer.getUniqueId(), reward);
        killer.sendMessage("§d+" + reward + " Shard" + (reward == 1 ? "" : "s"));
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore)));
        item.setItemMeta(meta);
        return item;
    }
}
