package net.flovex.core.scoreboard;

import net.flovex.core.FlovexCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TablistService {
    private final FlovexCore plugin;
    private int task;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public TablistService(FlovexCore plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) update(player);
        }, 1L, 20L);
    }

    public void stop() {
        if (task != 0) Bukkit.getScheduler().cancelTask(task);
    }

    public void update(Player player) {
        if (plugin.getRanks() != null) plugin.getRanks().apply(player);
        String header = plugin.getConfig().getString("tablist.header", "&b&lFLOVEXSMP");
        String footer = plugin.getConfig().getString("tablist.footer", "&f%online% Players &8• &7flovexsmp.play-mc.fun").replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        player.sendPlayerListHeaderAndFooter(legacy.deserialize(header), legacy.deserialize(footer));
    }
}
