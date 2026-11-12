package net.flovex.core.world;

import net.flovex.core.FlovexCore;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import java.util.*;

public final class RtpService implements Listener {
    private final FlovexCore plugin;
    private final Random random = new Random();

    public RtpService(FlovexCore plugin) { this.plugin = plugin; }

    public void open(Player player) {
        List<ActionButton> buttons = List.of(
            ActionButton.create(Component.text("Overworld"), Component.text("Random teleport"), 120, DialogAction.commandTemplate("/rtp overworld")),
            ActionButton.create(Component.text("Nether"), Component.text("Random teleport"), 120, DialogAction.commandTemplate("/rtp nether")),
            ActionButton.create(Component.text("End"), Component.text("Random teleport"), 120, DialogAction.commandTemplate("/rtp end"))
        );
        try {
            player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Random Teleport"))
                    .body(List.of(DialogBody.plainMessage(Component.text("Choose a world to teleport to."))))
                    .build())
                .type(DialogType.multiAction(buttons, null, 3))));
        } catch (Throwable error) {
            openFallback(player);
        }
    }

    private void openFallback(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Random Teleport");
        inventory.setItem(11, option(Material.GRASS_BLOCK, "&aOverworld", "&7Random teleport in the Overworld"));
        inventory.setItem(13, option(Material.NETHERRACK, "&cNether", "&7Random teleport in the Nether"));
        inventory.setItem(15, option(Material.END_STONE, "&eEnd", "&7Random teleport in the End"));
        player.openInventory(inventory);
    }

    @EventHandler public void onDialogFallbackClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().equals(ChatColor.DARK_GRAY + "Random Teleport")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String target = switch (event.getSlot()) { case 11 -> "overworld"; case 13 -> "nether"; case 15 -> "end"; default -> null; };
        if (target != null) { player.closeInventory(); teleport(player, target); }
    }

    private ItemStack option(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore)));
        item.setItemMeta(meta);
        return item;
    }

    public void teleport(Player player, String target) {
        String worldName = switch (target.toLowerCase(Locale.ROOT)) {
            case "overworld", "world" -> plugin.getConfig().getString("rtp.worlds.overworld", "world");
            case "nether" -> plugin.getConfig().getString("rtp.worlds.nether", "world_nether");
            case "end" -> plugin.getConfig().getString("rtp.worlds.end", "world_the_end");
            default -> null;
        };
        if (worldName == null) { player.sendMessage(Component.text("Choose overworld, nether, or end.")); return; }
        World world = Bukkit.getWorld(worldName);
        if (world == null) { player.sendMessage(Component.text("That RTP world is not loaded.")); return; }
        int min = plugin.getConfig().getInt("rtp.min-radius", 1000);
        int max = plugin.getConfig().getInt("rtp.max-radius", 10000);
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int radius = min + random.nextInt(Math.max(1, max - min + 1));
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            Location destination = findSafeLocation(world, x, z);
            if (destination == null || !world.getWorldBorder().isInside(destination)) continue;
            plugin.getMessages().actionBar(player,"&bFinding a safe location...");
            player.teleportAsync(destination).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getMessages().actionBar(player,success ? "&aRandom teleport complete." : "&cTeleport failed.")));
            return;
        }
        player.sendMessage(Component.text("Could not find a safe location. Try again."));
    }
    private Location findSafeLocation(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            int minY = world.getMinHeight() + 8;
            int maxY = Math.min(world.getMaxHeight() - 4, 120);
            for (int attempt = 0; attempt < 80; attempt++) {
                int y = minY + random.nextInt(Math.max(1, maxY - minY));
                Location location = safeAt(world, x, y, z);
                if (location != null) return location;
            }
            return null;
        }
        int y = world.getHighestBlockYAt(x, z);
        return safeAt(world, x, y, z);
    }

    private Location safeAt(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) return null;
        Block floor = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        Material ground = floor.getType();
        if (!ground.isSolid() || floor.isLiquid() || ground == Material.BEDROCK ||
            ground == Material.MAGMA_BLOCK || ground == Material.CACTUS ||
            ground == Material.CAMPFIRE || ground == Material.SOUL_CAMPFIRE ||
            ground == Material.FIRE || ground == Material.SOUL_FIRE ||
            ground == Material.WITHER_ROSE || ground == Material.SWEET_BERRY_BUSH ||
            ground == Material.POWDER_SNOW || feet.isLiquid() || head.isLiquid() ||
            !feet.isPassable() || !head.isPassable()) return null;
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}
