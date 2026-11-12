package net.flovex.core.rank;

import net.flovex.core.FlovexCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public final class RankService {
    private final FlovexCore plugin;
    private List<String> priority = new ArrayList<>();

    public RankService(FlovexCore plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "luckpermsranks.yml");
        if (!file.exists()) plugin.saveResource("luckpermsranks.yml", false);
        reload();
    }

    public void reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "luckpermsranks.yml"));
        priority = yaml.getStringList("ranks").stream().filter(Objects::nonNull).map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    public String getPrimaryDisplayGroup(Player player) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return "default";

            Class<?> queryOptions = Class.forName("net.luckperms.api.query.QueryOptions");
            Object options = queryOptions.getMethod("defaultContextualOptions").invoke(null);
            Method inherited = Arrays.stream(user.getClass().getMethods())
                    .filter(m -> m.getName().equals("getInheritedGroups") && m.getParameterCount() == 1)
                    .findFirst().orElse(null);
            if (inherited != null) {
                Object groups = inherited.invoke(user, options);
                if (groups instanceof Collection<?> collection) {
                    return collection.stream().map(g -> {
                        try { return String.valueOf(g.getClass().getMethod("getName").invoke(g)); }
                        catch (Exception ignored) { return ""; }
                    }).filter(s -> !s.isBlank())
                     .min(Comparator.comparingInt(this::priorityOf).thenComparing(String.CASE_INSENSITIVE_ORDER))
                     .orElseGet(() -> primary(user));
                }
            }
            return primary(user);
        } catch (Throwable ignored) {
            return "default";
        }
    }

    private String primary(Object user) {
        try { return String.valueOf(user.getClass().getMethod("getPrimaryGroup").invoke(user)); }
        catch (Exception ignored) { return "default"; }
    }

    private int priorityOf(String group) {
        int index = priority.indexOf(group.toLowerCase(Locale.ROOT));
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    public String getDisplayPrefix(Player player) {
        String group = getPrimaryDisplayGroup(player);
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object manager = api.getClass().getMethod("getGroupManager").invoke(api);
            Object groupObject = manager.getClass().getMethod("getGroup", String.class).invoke(manager, group);
            if (groupObject == null) return "";
            Object cached = groupObject.getClass().getMethod("getCachedData").invoke(groupObject);
            Class<?> queryOptions = Class.forName("net.luckperms.api.query.QueryOptions");
            Object options = queryOptions.getMethod("defaultContextualOptions").invoke(null);
            Object meta = cached.getClass().getMethod("getMetaData", queryOptions).invoke(cached, options);
            Object prefix = meta.getClass().getMethod("getPrefix").invoke(meta);
            if (prefix != null && !String.valueOf(prefix).isBlank()) return String.valueOf(prefix).replace('§', '&');
        } catch (Throwable ignored) {}
        return "";
    }
    public void apply(Player player) {
        String group = getPrimaryDisplayGroup(player);
        player.playerListName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                .deserialize(getDisplayPrefix(player) + "&f" + player.getName()));
    }
}
