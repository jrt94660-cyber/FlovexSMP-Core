package net.flovex.core.text;
import net.flovex.core.FlovexCore;import net.flovex.core.util.ColorUtil;import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import net.kyori.adventure.text.Component;import net.kyori.adventure.title.Title;import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;import org.bukkit.configuration.file.YamlConfiguration;import java.io.File;import java.util.Map;
public final class MessageService{
 private final FlovexCore plugin;private final YamlConfiguration cfg;
 public MessageService(FlovexCore p){plugin=p;cfg=YamlConfiguration.loadConfiguration(new File(p.getDataFolder(),"messages.yml"));}
 public String get(String key,String fallback){return ColorUtil.color(cfg.getString("messages."+key,fallback));}
 public String prefix(){return ColorUtil.color(cfg.getString("prefix",plugin.getConfig().getString("prefix","")));}
 public void send(CommandSender sender,String key,String fallback,Map<String,String> values){String out=prefix()+get(key,fallback);for(var e:values.entrySet())out=out.replace("%"+e.getKey()+"%",e.getValue());sender.sendMessage(out);}
 public void title(Player player,String title,String subtitle){LegacyComponentSerializer serializer=LegacyComponentSerializer.legacySection();player.showTitle(Title.title(serializer.deserialize(ColorUtil.color(title)),serializer.deserialize(ColorUtil.color(subtitle))));}
 public void actionBar(Player player,String text){player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.color(text)));}
}