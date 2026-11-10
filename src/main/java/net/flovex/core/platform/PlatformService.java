package net.flovex.core.platform;
import org.bukkit.Bukkit;import org.bukkit.entity.Player;import java.lang.reflect.Method;
public final class PlatformService{
 public boolean isBedrock(Player p){try{Class<?> api=Class.forName("org.geysermc.floodgate.api.FloodgateApi");Object instance=api.getMethod("getInstance").invoke(null);Method check=api.getMethod("isFloodgatePlayer",java.util.UUID.class);return Boolean.TRUE.equals(check.invoke(instance,p.getUniqueId()));}catch(Throwable ignored){return false;}}
 public boolean geyserAvailable(){return Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")||Bukkit.getPluginManager().isPluginEnabled("Geyser");}
 public boolean floodgateAvailable(){return Bukkit.getPluginManager().isPluginEnabled("floodgate");}
}