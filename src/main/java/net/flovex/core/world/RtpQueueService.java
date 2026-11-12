package net.flovex.core.world;

import net.flovex.core.FlovexCore;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public final class RtpQueueService {
    private final FlovexCore plugin;
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
    private BukkitTask worker;

    public RtpQueueService(FlovexCore plugin) { this.plugin = plugin; }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("rtp.queue-interval-ticks", 100L));
        worker = Bukkit.getScheduler().runTaskTimer(plugin, this::process, interval, interval);
    }

    public void stop() { if (worker != null) worker.cancel(); }

    public void open(Player player) {
        int position = position(player.getUniqueId());
        String state = position == 0 ? "You are not in the queue." : "Your queue position: " + position + " / " + queue.size();
        try {
            player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("RTP Queue"))
                    .body(List.of(DialogBody.plainMessage(Component.text(state + "\nQueue for a safe Overworld random teleport."))))
                    .build())
                .type(DialogType.multiAction(List.of(
                    ActionButton.create(Component.text("Join Queue"), Component.text("Wait for your random teleport"), 120, DialogAction.commandTemplate("/rtpq join")),
                    ActionButton.create(Component.text("Leave Queue"), Component.text("Remove yourself from the queue"), 120, DialogAction.commandTemplate("/rtpq leave"))
                ), null, 2))));
        } catch (Throwable error) {
            player.sendMessage("§bRTP Queue §7- " + state);
            player.sendMessage("§a/rtpq join §7or §c/rtpq leave");
        }
    }

    public void join(Player player) {
        if (queue.add(player.getUniqueId())) {
            player.sendMessage("§aAdded to the RTP queue. Position: §f" + position(player.getUniqueId()));
        } else player.sendMessage("§eYou are already in the RTP queue. Position: §f" + position(player.getUniqueId()));
    }

    public void leave(Player player) {
        if (queue.remove(player.getUniqueId())) player.sendMessage("§cRemoved from the RTP queue.");
        else player.sendMessage("§7You are not in the RTP queue.");
    }

    public int position(UUID uuid) {
        int index = 1;
        for (UUID queued : queue) {
            if (queued.equals(uuid)) return index;
            index++;
        }
        return 0;
    }

    public int size() { return queue.size(); }

    private void process() {
        Iterator<UUID> iterator = queue.iterator();
        if (!iterator.hasNext()) return;
        UUID next = iterator.next();
        iterator.remove();
        Player player = Bukkit.getPlayer(next);
        if (player == null || !player.isOnline()) return;
        player.sendMessage("§bYour place in the RTP queue is ready. Finding a safe location...");
        plugin.getRtp().teleport(player, "overworld");
    }
}
