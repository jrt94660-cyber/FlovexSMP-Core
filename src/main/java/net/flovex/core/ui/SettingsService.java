package net.flovex.core.ui;

import net.flovex.core.FlovexCore;
import net.flovex.core.platform.PlatformService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class SettingsService implements Listener {
    private record Setting(String key, String label, String description, String defaultValue) {}
    private static final Map<String, List<Setting>> SETTINGS = Map.of(
        "chat", List.of(
            new Setting("pm", "Private Messages", "Allow incoming private messages", "true"),
            new Setting("teamchat", "Team Chat", "Send chat to your team", "false")
        ),
        "notifications", List.of(
            new Setting("server-sounds", "Server Sounds", "Play menu and notification sounds", "true")
        ),
        "pvp", List.of(
            new Setting("safety", "PvP Safety", "Block player-versus-player damage", "false")
        ),
        "visuals", List.of(
            new Setting("hide-players", "Hide Players", "Hide other players from your view", "false")
        ),
        "privacy", List.of(
            new Setting("tpauto", "TPA Auto", "Automatically accept teleport-to-you requests", "false"),
            new Setting("tpahereauto", "TPAHere Auto", "Automatically accept teleport-here requests", "false"),
            new Setting("pm", "Private Messages", "Allow incoming private messages", "true")
        ),
        "scoreboard", List.of(
            new Setting("scoreboard", "Sidebar Scoreboard", "Show or hide the sidebar", "true")
        ),
        "general", List.of(
            new Setting("phantom-enabled", "Phantom Spawning", "Allow phantoms to spawn near you", "true"),
            new Setting("voicechat-consent", "Voice Chat Consent", "Store your voice chat consent preference", "false")
        )
    );
    private final FlovexCore plugin;
    private final PlatformService platform;

    public SettingsService(FlovexCore plugin, PlatformService platform) {
        this.plugin = plugin;
        this.platform = platform;
    }

    public void open(Player player, String category) {
        if (category == null || category.isBlank()) openCategories(player);
        else openCategory(player, category.toLowerCase(Locale.ROOT));
    }

    public void toggle(Player player, String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        String fallback = defaultFor(normalized);
        boolean next = !Boolean.parseBoolean(plugin.getStorage().preference(player.getUniqueId(), normalized, fallback));
        plugin.getStorage().setPreference(player.getUniqueId(), normalized, Boolean.toString(next));
        if (normalized.equals("hide-players")) {
            for (Player other : Bukkit.getOnlinePlayers()) if (other != player) {
                if (next) player.hidePlayer(plugin, other); else player.showPlayer(plugin, other);
            }
        }
        if (normalized.equals("scoreboard")) {
            if (next) plugin.getScoreboard().update(player);
            else player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        if (normalized.equals("safety") && next) player.sendMessage("§aPvP safety is enabled.");
        else player.sendMessage("§7" + labelFor(normalized) + ": " + (next ? "§aON" : "§cOFF"));
    }

    private void openCategories(Player player) {
        List<String> categories = List.of("chat", "notifications", "pvp", "visuals", "privacy", "scoreboard", "general");
        List<ActionButton> buttons = new ArrayList<>();
        for (String category : categories) {
            buttons.add(ActionButton.create(Component.text(title(category)),
                Component.text(SETTINGS.get(category).size() + " settings"),
                120, DialogAction.commandTemplate("/settings " + category)));
        }
        if (!platform.isBedrock(player)) {
            try {
                player.showDialog(Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(Component.text("Settings"))
                        .body(List.of(DialogBody.plainMessage(Component.text("Select a settings category."))))
                        .build())
                    .type(DialogType.multiAction(buttons, null, 2))));
                return;
            } catch (Throwable ignored) {}
        }
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Flovex Settings: Main");
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < categories.size(); i++) inv.setItem(slots[i], icon(Material.BOOK, title(categories.get(i)), "Open " + title(categories.get(i)), "settings " + categories.get(i)));
        player.openInventory(inv);
    }

    private void openCategory(Player player, String category) {
        List<Setting> settings = SETTINGS.get(category);
        if (settings == null) { openCategories(player); return; }
        List<ActionButton> buttons = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (Setting setting : settings) {
            boolean enabled = Boolean.parseBoolean(plugin.getStorage().preference(player.getUniqueId(), setting.key(), setting.defaultValue()));
            body.append(setting.label()).append(": ").append(enabled ? "ON" : "OFF").append("\n");
            buttons.add(ActionButton.create(Component.text(setting.label() + ": " + (enabled ? "ON" : "OFF")),
                Component.text(setting.description()), 120,
                DialogAction.commandTemplate("/settings toggle " + setting.key() + " " + category)));
        }
        if (category.equals("scoreboard")) {
            String style = plugin.getStorage().preference(player.getUniqueId(), "scoreboard-style", "modern");
            buttons.add(ActionButton.create(Component.text("Layout: " + style.toUpperCase(Locale.ROOT)),
                Component.text("Switch between Modern and Legacy"), 120,
                DialogAction.commandTemplate("/settings style")));
            body.append("Layout: ").append(style).append("\n");
        }
        buttons.add(ActionButton.create(Component.text("Back"), Component.text("Settings categories"), 120, DialogAction.commandTemplate("/settings")));
        if (!platform.isBedrock(player)) {
            try {
                player.showDialog(Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(Component.text(title(category)))
                        .body(List.of(DialogBody.plainMessage(Component.text(body.toString()))))
                        .build())
                    .type(DialogType.multiAction(buttons, null, 2))));
                return;
            } catch (Throwable ignored) {}
        }
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Flovex Settings: " + title(category));
        int slot = 10;
        for (Setting setting : settings) {
            boolean enabled = Boolean.parseBoolean(plugin.getStorage().preference(player.getUniqueId(), setting.key(), setting.defaultValue()));
            inv.setItem(slot++, icon(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                setting.label() + (enabled ? " &aON" : " &cOFF"), setting.description(), "settings toggle " + setting.key() + " " + category));
        }
        if (category.equals("scoreboard")) inv.setItem(16, icon(Material.COMPARATOR, "Layout", "Switch Modern / Legacy", "settings style"));
        inv.setItem(22, icon(Material.ARROW, "Back", "Settings categories", "settings"));
        player.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(ChatColor.DARK_GRAY + "Flovex Settings:")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return;
        for (String line : item.getItemMeta().getLore()) if (line.startsWith("§0cmd:")) {
            player.closeInventory();
            Bukkit.dispatchCommand(player, line.substring("§0cmd:".length()));
            return;
        }
    }

    private ItemStack icon(Material material, String name, String description, String command) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(List.of(ChatColor.GRAY + description, "§0cmd:" + command));
        item.setItemMeta(meta);
        return item;
    }
    private String defaultFor(String key) {
        for (List<Setting> list : SETTINGS.values()) for (Setting setting : list) if (setting.key().equals(key)) return setting.defaultValue();
        return "false";
    }
    private String labelFor(String key) {
        for (List<Setting> list : SETTINGS.values()) for (Setting setting : list) if (setting.key().equals(key)) return setting.label();
        return key;
    }
    private String title(String category) { return Character.toUpperCase(category.charAt(0)) + category.substring(1); }
}
