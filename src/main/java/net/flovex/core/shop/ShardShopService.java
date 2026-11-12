package net.flovex.core.shop;

import net.flovex.core.FlovexCore;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public final class ShardShopService implements Listener {
    private record Product(String key, Material material, String name, List<String> lore, List<String> enchants,
                           int price, int slot, String command, boolean giveItem, String tool, long duration,
                           boolean hideEnchants, boolean vanillaName, List<String> potionEffects, String potionColor) {}
    private final FlovexCore plugin;
    private final Map<UUID, Product> pending = new HashMap<>();
    private final Map<String, Product> products = new LinkedHashMap<>();
    private final NamespacedKey toolKey;
    private final NamespacedKey expiryKey;
    private final Set<UUID> extraBreak = new HashSet<>();
    private BukkitTask expiryTask;

    public ShardShopService(FlovexCore plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "amethyst_tool");
        this.expiryKey = new NamespacedKey(plugin, "amethyst_expires");
        if (config == null) throw new IllegalStateException("Missing SHARD-MENU section in shop.yml");
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
                item.getString("AMETHYST-TOOL", ""), item.getLong("AMETHYST-DURATION", 0),
                item.getBoolean("HIDE-ENCHANTS", false), item.getBoolean("VANILLA-NAME", false),
                item.getStringList("POTION-EFFECTS"), item.getString("POTION-COLOR", "")));
        }
    }

    public void start() {
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin,
            () -> Bukkit.getOnlinePlayers().forEach(this::expireItems), 1200L, 1200L);
    }
    public void stop() { if (expiryTask != null) expiryTask.cancel(); }

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
            if (product.slot() >= 0 && product.slot() < 45) inv.setItem(product.slot(), item);
        }
        inv.setItem(49, button(Material.AMETHYST_SHARD, "&fYour Shards", "&d" + String.format(Locale.US, "%,d", balance(player.getUniqueId())) + " Shards"));
        inv.setItem(50, button(Material.PLAYER_HEAD, "&fRanks", "&7Click to view rank information"));
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
            player.sendMessage(plugin.getMessages().prefix() + "§cYou do not have enough shards.");
            return;
        }
        if (!product.command().isBlank()) {
            String command = product.command().replace("%player%", player.getName()).replaceFirst("^/", "");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (product.giveItem()) {
            ItemStack item = createItem(product);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        plugin.getMessages().actionBar(player, "&aPurchased &f" + ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', product.name())) +
            " &afor &#A303F9" + String.format(Locale.US, "%,d", product.price()) + " Shards");
    }

    public boolean giveTool(Player player, String type, long durationOverride) {
        Product product = products.values().stream().filter(p -> p.tool().equalsIgnoreCase(type)).findFirst().orElse(null);
        if (product == null) return false;
        ItemStack item = createItem(product);
        if (durationOverride > 0) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, System.currentTimeMillis() + durationOverride * 1000L);
            item.setItemMeta(meta);
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        return true;
    }

    private ItemStack createItem(Product product) {
        ItemStack stack = new ItemStack(product.material());
        ItemMeta meta = stack.getItemMeta();
        String display = product.name();
        if (product.vanillaName()) display = "&f" + title(product.material().name());
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', display));
        if (!product.lore().isEmpty()) meta.setLore(product.lore().stream().map(x -> ChatColor.translateAlternateColorCodes('&', x)).toList());
        for (String raw : product.enchants()) {
            String[] parts = raw.split(":");
            if (parts.length != 2) continue;
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (enchantment == null) continue;
            try { meta.addEnchant(enchantment, Integer.parseInt(parts[1]), true); } catch (NumberFormatException ignored) {}
        }
        if (product.hideEnchants()) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (meta instanceof PotionMeta potionMeta) {
            for (String raw : product.potionEffects()) {
                String[] parts = raw.split(":");
                if (parts.length != 3) continue;
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
                if (type == null) continue;
                try {
                    int level = Math.max(1, Integer.parseInt(parts[1]));
                    int ticks = Math.max(1, Integer.parseInt(parts[2]));
                    potionMeta.addCustomEffect(new PotionEffect(type, ticks, level - 1), true);
                } catch (NumberFormatException ignored) {}
            }
            if (!product.potionColor().isBlank()) {
                try { potionMeta.setColor(Color.fromRGB(Integer.parseInt(product.potionColor().replace("#", ""), 16))); }
                catch (NumberFormatException ignored) {}
            }
            meta = potionMeta;
        }
        if (!product.tool().isBlank()) meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, product.tool());
        if (product.duration() > 0) meta.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, System.currentTimeMillis() + product.duration() * 1000L);
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
        if (slot == 50) { player.closeInventory(); Bukkit.dispatchCommand(player, "ranks"); return; }
        if (slot == 49) { open(player); return; }
        for (Product product : products.values()) if (product.slot() == slot) { confirm(player, product); return; }
    }

    @EventHandler(ignoreCancelled = true) public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (extraBreak.contains(player.getUniqueId())) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (expireHeld(player, tool)) return;
        String type = toolType(tool);
        if (type == null) return;
        if (!type.equals("DRILL") && !type.equals("SHOVEL") && !type.equals("CHOPPER")) return;
        extraBreak.add(player.getUniqueId());
        try {
            if (type.equals("CHOPPER")) breakTree(event.getBlock(), player, tool);
            else breakArea(event.getBlock(), player, tool);
        } finally {
            extraBreak.remove(player.getUniqueId());
        }
    }

    private void breakArea(Block center, Player player, ItemStack tool) {
        BlockFace facing = player.getFacing();
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            Block target;
            if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) target = center.getRelative(a, b, 0);
            else if (facing == BlockFace.EAST || facing == BlockFace.WEST) target = center.getRelative(0, b, a);
            else target = center.getRelative(a, 0, b);
            breakExtraBlock(target, player, tool);
        }
    }

    private void breakTree(Block start, Player player, ItemStack tool) {
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (BlockFace face : BlockFace.values()) if (face != BlockFace.SELF) queue.add(start.getRelative(face));
        int broken = 0;
        while (!queue.isEmpty() && broken < 128) {
            Block block = queue.removeFirst();
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!visited.add(key) || !isLog(block.getType())) continue;
            if (breakExtraBlock(block, player, tool)) {
                broken++;
                for (BlockFace face : BlockFace.values()) if (face != BlockFace.SELF) queue.add(block.getRelative(face));
            }
        }
    }

    private boolean breakExtraBlock(Block block, Player player, ItemStack tool) {
        if (block.getType().isAir() || !block.getType().isSolid() || block.getState() instanceof InventoryHolder) return false;
        BlockBreakEvent extra = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(extra);
        if (extra.isCancelled()) return false;
        return block.breakNaturally(tool);
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE") || name.equals("MUSHROOM_STEM");
    }

    @EventHandler(ignoreCancelled = true) public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (expireHeld(player, held)) { event.setCancelled(true); return; }
        if (!"SELL_AXE".equals(toolType(held))) return;
        if (event.getClickedBlock().getState() instanceof InventoryHolder holder) {
            event.setCancelled(true);
            plugin.getShop().sellContainer(player, holder.getInventory());
        }
    }

    @EventHandler public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        if (expired(next)) { event.setCancelled(true); player.getInventory().setItem(event.getNewSlot(), null); plugin.getMessages().actionBar(player, "&cYour amethyst item has expired."); }
    }

    private String toolType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
    }

    private boolean expireHeld(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Long expires = item.getItemMeta().getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        if (expires == null || expires > System.currentTimeMillis()) return false;
        player.getInventory().setItemInMainHand(null);
        plugin.getMessages().actionBar(player, "&cYour amethyst item has expired.");
        return true;
    }

    private void expireItems(Player player) {
        boolean removed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (expired(item)) { inventory.setItem(slot, null); removed = true; }
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) if (expired(armor[i])) { armor[i] = null; removed = true; }
        inventory.setArmorContents(armor);
        if (expired(inventory.getItemInOffHand())) { inventory.setItemInOffHand(null); removed = true; }
        if (removed) plugin.getMessages().actionBar(player, "&cAn expired amethyst item was removed.");
    }

    private boolean expired(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Long expires = item.getItemMeta().getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        return expires != null && expires <= System.currentTimeMillis();
    }

    @EventHandler public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) return;
        int reward = Math.max(0, plugin.getConfig().getInt("shards.per-kill", 1));
        if (reward == 0) return;
        add(killer.getUniqueId(), reward);
        plugin.getMessages().actionBar(killer, "&#A303F9+" + reward + " Shard" + (reward == 1 ? "" : "s"));
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore)));
        item.setItemMeta(meta);
        return item;
    }

    private String title(String material) {
        String value = material.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
