package net.flovex.market;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import net.flovex.market.data.Storage;
import net.flovex.market.economy.EconomyHook;
import net.flovex.market.market.MarketManager;
import net.flovex.market.ui.MarketUI;
import net.flovex.market.scoreboard.ScoreboardManager;
import net.flovex.market.command.MarketCommands;

public final class FlovexMarket extends JavaPlugin {
    private Storage storage; private EconomyHook economy; private MarketManager market; private MarketUI ui; private ScoreboardManager scoreboard;
    @Override public void onEnable() {
        saveDefaultConfig();
        for(String file:new String[]{"scoreboard.yml","settings.yml","auction-house.yml","orders.yml","worth.yml"})saveResource(file,false);
        try {
            storage=new Storage(this); economy=new EconomyHook(this); market=new MarketManager(this,storage,economy);
            ui=new MarketUI(this,market,storage); scoreboard=new ScoreboardManager(this,economy,storage);
            Bukkit.getPluginManager().registerEvents(ui,this); Bukkit.getPluginManager().registerEvents(market,this);
            MarketCommands commands=new MarketCommands(this,market,ui,scoreboard);
            for(String name:new String[]{"ah","orders","settings","sell","sellall","sellhand","sellmulti","worth"}){var command=getCommand(name);if(command!=null){command.setExecutor(commands);command.setTabCompleter(commands);}}
            scoreboard.start();
            Bukkit.getScheduler().runTaskTimer(this,market::cleanupExpired,1200L,1200L);
            getLogger().info("FlovexMarket 1.0.0 enabled. Economy: "+(economy.available()?economy.providerName():"unavailable"));
            getLogger().info("Optional integrations: PlaceholderAPI="+Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")+", Geyser="+(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")||Bukkit.getPluginManager().isPluginEnabled("Geyser"))+", Floodgate="+Bukkit.getPluginManager().isPluginEnabled("floodgate"));
        }catch(Exception ex){getLogger().severe("FlovexMarket could not initialize: "+ex.getMessage());Bukkit.getPluginManager().disablePlugin(this);}
    }
    public YamlConfiguration yaml(String name){return YamlConfiguration.loadConfiguration(new File(getDataFolder(),name));}
    @Override public void onDisable(){if(scoreboard!=null)scoreboard.stop();if(storage!=null)storage.close();}
    public Storage storage(){return storage;} public EconomyHook economy(){return economy;} public MarketManager market(){return market;}
}