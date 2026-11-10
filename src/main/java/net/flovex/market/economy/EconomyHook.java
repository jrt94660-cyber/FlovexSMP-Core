package net.flovex.market.economy;

import net.flovex.market.FlovexMarket;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.UUID;

public final class EconomyHook {
    private final Economy economy;
    public EconomyHook(FlovexMarket plugin) {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        this.economy = registration == null ? null : registration.getProvider();
        if (economy == null) plugin.getLogger().warning("No Vault economy provider found. Install Vault and ExcellentEconomy (or another Vault provider).");
    }
    public boolean available(){ return economy != null; }
    public String providerName(){ return economy == null ? "none" : economy.getName(); }
    public double balance(UUID id){ return economy == null ? 0 : economy.getBalance(Bukkit.getOfflinePlayer(id)); }
    public boolean withdraw(UUID id, double amount){
        if (economy == null || amount < 0 || !Double.isFinite(amount)) return false;
        var response = economy.withdrawPlayer(Bukkit.getOfflinePlayer(id), amount);
        return response.transactionSuccess();
    }
    public boolean deposit(UUID id, double amount){
        if (economy == null || amount < 0 || !Double.isFinite(amount)) return false;
        return economy.depositPlayer(Bukkit.getOfflinePlayer(id), amount).transactionSuccess();
    }
    public String format(double amount){ return economy == null ? String.format("$%,.2f", amount) : economy.format(amount); }
}