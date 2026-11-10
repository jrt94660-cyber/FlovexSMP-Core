package net.flovex.core.text;
import net.flovex.core.FlovexCore;import net.flovex.core.util.ColorUtil;import org.bukkit.command.CommandSender;import org.bukkit.configuration.file.YamlConfiguration;import java.io.File;import java.util.Map;
public final class MessageService{
 private final FlovexCore plugin;private final YamlConfiguration cfg;
 public MessageService(FlovexCore p){plugin=p;cfg=YamlConfiguration.loadConfiguration(new File(p.getDataFolder(),"messages.yml"));}
 public String get(String key,String fallback){return ColorUtil.color(cfg.getString("messages."+key,fallback));}
 public String prefix(){return ColorUtil.color(cfg.getString("prefix",plugin.getConfig().getString("prefix","")));}
 public void send(CommandSender sender,String key,String fallback,Map<String,String> values){String out=prefix()+get(key,fallback);for(var e:values.entrySet())out=out.replace("%"+e.getKey()+"%",e.getValue());sender.sendMessage(out);}
}