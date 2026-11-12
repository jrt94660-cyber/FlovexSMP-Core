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
import org.bukkit.entity.Player;
import java.util.*;

public final class RtpService {
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
            plugin.getLogger().warning("Could not open RTP dialog: " + error.getMessage());
            player.sendMessage(Component.text("Use /rtp overworld, /rtp nether, or /rtp end."));
        }
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
            int y = world.getHighestBlockYAt(x, z);
            Block floor = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (!floor.getType().isSolid() || floor.isLiquid() || feet.isLiquid() || head.isLiquid()
                    || !feet.isPassable() || !head.isPassable()) continue;
            Location destination = new Location(world, x + 0.5, y, z + 0.5);
            player.sendMessage(Component.text("Finding a safe location..."));
            player.teleportAsync(destination).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage(Component.text(success ? "Random teleport complete." : "Teleport failed."))));
            return;
        }
        player.sendMessage(Component.text("Could not find a safe location. Try again."));
    }
}
