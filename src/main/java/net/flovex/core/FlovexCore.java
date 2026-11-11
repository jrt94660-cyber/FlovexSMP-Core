package net.flovex.core;

import net.flovex.core.command.CoreCommands;
import net.flovex.core.data.Storage;
import net.flovex.core.economy.EconomyHook;
import net.flovex.core.integration.FlovexExpansion;
import net.flovex.core.market.Marketplace;
import net.flovex.core.network.PortalService;
import net.flovex.core.platform.PlatformService;
import net.flovex.core.scoreboard.ScoreboardService;
import net.flovex.core.social.TeamService;
import net.flovex.core.text.MessageService;
import net.flovex.core.text.SoundService;
import net.flovex.core.ui.MenuService;
import net.flovex.core.world.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class FlovexCore extends JavaPlugin {
 private Storage storage;
 private EconomyHook economy;
 private Marketplace market;
 private PortalService portals;
 private PlatformService platform;
 private MenuService menus;
 private ScoreboardService scoreboard;
 private TeamService teams;
 private WarpService warps;
 private MessageService messages;
 private SoundService sounds;
 private YamlConfiguration scoreboardCfg,menusCfg,settingsCfg,auctionCfg,ordersCfg,teamsCfg,warpsCfg,teleportCfg,moderationCfg,networkCfg;

 @Override public void onEnable() {
  saveDefaultConfig();
  for(String file:new String[]{"database.yml","scoreboard.yml","teleport.yml","menus.yml","sounds.yml","moderation.yml","teams.yml","warps.yml","rules.yml","messages.yml","settings.yml","auction-house.yml","orders.yml","network.yml"})saveResource(file,false);
  try {
   YamlConfiguration databaseCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"database.yml"));
   scoreboardCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"scoreboard.yml"));
   menusCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"menus.yml"));
   settingsCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"settings.yml"));
   auctionCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"auction-house.yml"));
   ordersCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"orders.yml"));
   teamsCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"teams.yml"));
   warpsCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"warps.yml"));
   teleportCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"teleport.yml"));
   moderationCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"moderation.yml"));
   networkCfg=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"network.yml"));

   storage=new Storage(getDataFolder().toPath(),databaseCfg.getString("storage.file","flovexcore.db"));
   economy=new EconomyHook();
   platform=new PlatformService();
   market=new Marketplace(storage,economy,auctionCfg,ordersCfg);
   portals=new PortalService(this,storage,networkCfg);
   teams=new TeamService(storage,teamsCfg);
   warps=new WarpService(storage,warpsCfg);
   messages=new MessageService(this);
   sounds=new SoundService(this);
   menus=new MenuService(this,platform);
   scoreboard=new ScoreboardService(this);

   CoreCommands commands=new CoreCommands(this);
   for(String name:getDescription().getCommands().keySet()){
    var command=getCommand(name);
    if(command!=null){command.setExecutor(commands);command.setTabCompleter(commands);}
   }
   Bukkit.getPluginManager().registerEvents(commands,this);
   Bukkit.getPluginManager().registerEvents(menus,this);
   Bukkit.getPluginManager().registerEvents(portals,this);
   Bukkit.getMessenger().registerOutgoingPluginChannel(this,"BungeeCord");
   if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))new FlovexExpansion(this).register();

   scoreboard.start();
   Bukkit.getScheduler().runTaskTimer(this,()->market.cleanup(),1200L,1200L);
   Bukkit.getScheduler().runTaskTimer(this,()->Bukkit.getOnlinePlayers().forEach(player->{
    storage.update("INSERT INTO player_data(uuid,last_name,first_join,play_ticks,last_seen) VALUES(?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name,play_ticks=player_data.play_ticks+1200,last_seen=excluded.last_seen",player.getUniqueId().toString(),player.getName(),System.currentTimeMillis(),1200,System.currentTimeMillis());
   }),1200L,1200L);
   getLogger().info("FlovexCore enabled. Marketplace provider: "+economy.provider()+". Geyser: "+platform.geyserAvailable()+", Floodgate: "+platform.floodgateAvailable());
  } catch(Exception ex) {
   getLogger().severe("FlovexCore failed to initialize: "+ex.getMessage());
   ex.printStackTrace();
   Bukkit.getPluginManager().disablePlugin(this);
  }
 }

 @Override public void onDisable(){if(scoreboard!=null)scoreboard.stop();Bukkit.getMessenger().unregisterOutgoingPluginChannel(this,"BungeeCord");if(storage!=null)storage.close();}
 public Storage getStorage(){return storage;}
 public EconomyHook getEconomy(){return economy;}
 public Marketplace getMarket(){return market;}
 public PortalService getPortals(){return portals;}
 public YamlConfiguration networkConfig(){return networkCfg;}
 public PlatformService getPlatform(){return platform;}
 public MenuService getMenus(){return menus;}
 public ScoreboardService getScoreboard(){return scoreboard;}
 public YamlConfiguration scoreboardConfig(){return scoreboardCfg;}
 public TeamService getTeams(){return teams;}
 public WarpService getWarps(){return warps;}
 public MessageService getMessages(){return messages;}
 public SoundService getSounds(){return sounds;}
 public YamlConfiguration menusConfig(){return menusCfg;}
 public YamlConfiguration settingsConfig(){return settingsCfg;}
 
 public YamlConfiguration teleportConfig(){return teleportCfg;}
 public YamlConfiguration moderationConfig(){return moderationCfg;}public void saveModerationConfig(){try{moderationCfg.save(new File(getDataFolder(),"moderation.yml"));}catch(Exception e){getLogger().warning("Could not save moderation.yml: "+e.getMessage());}}
}