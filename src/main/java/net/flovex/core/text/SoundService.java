package net.flovex.core.text;
import net.flovex.core.FlovexCore;import org.bukkit.Sound;import org.bukkit.entity.Player;import org.bukkit.configuration.file.YamlConfiguration;import java.io.File;
public final class SoundService{
 private final YamlConfiguration cfg;
 private final FlovexCore plugin;
 public SoundService(FlovexCore p){plugin=p;cfg=YamlConfiguration.loadConfiguration(new File(p.getDataFolder(),"sounds.yml"));}
 public void play(Player p,String key){if(!Boolean.parseBoolean(plugin.getStorage().preference(p.getUniqueId(),"server-sounds","true")))return;String raw=cfg.getString("sounds."+key,"");if(raw==null||raw.isBlank())return;try{String[] a=raw.split("\\|");Sound s=Sound.valueOf(a[0].trim().toUpperCase());float volume=a.length>1?Float.parseFloat(a[1]):1f;float pitch=a.length>2?Float.parseFloat(a[2]):1f;p.playSound(p.getLocation(),s,volume,pitch);}catch(Exception ignored){}}
}