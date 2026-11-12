package net.flovex.core.economy;
import net.milkbowl.vault.economy.Economy;import org.bukkit.Bukkit;import org.bukkit.OfflinePlayer;import org.bukkit.plugin.RegisteredServiceProvider;import java.util.UUID;
public final class EconomyHook{
 private final Economy economy;
 public EconomyHook(){RegisteredServiceProvider<Economy> p=Bukkit.getServicesManager().getRegistration(Economy.class);economy=p==null?null:p.getProvider();}
 public boolean available(){return economy!=null;}public double balance(UUID id){return economy==null?0:economy.getBalance(Bukkit.getOfflinePlayer(id));}public String provider(){return economy==null?"unavailable":economy.getName();}
 public boolean withdraw(UUID id,double n){return economy!=null&&Double.isFinite(n)&&n>=0&&economy.withdrawPlayer(Bukkit.getOfflinePlayer(id),n).transactionSuccess();}
 public boolean deposit(UUID id,double n){return economy!=null&&Double.isFinite(n)&&n>=0&&economy.depositPlayer(Bukkit.getOfflinePlayer(id),n).transactionSuccess();}
 public String format(double n){return economy==null?String.format("$%,.2f",n):economy.format(n);}
}