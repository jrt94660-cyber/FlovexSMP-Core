package net.flovex.market.scoreboard;

import net.flovex.market.FlovexMarket;
import net.flovex.market.economy.EconomyHook;
import net.flovex.market.data.Storage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import java.time.LocalDate;
import java.util.*;

public final class ScoreboardManager {
    private final FlovexMarket plugin; private final EconomyHook economy; private final Storage db; private int task;
    public ScoreboardManager(FlovexMarket p,EconomyHook e,Storage d){plugin=p;economy=e;db=d;}
    public void start(){if(!plugin.getConfig().getBoolean("scoreboard.enabled",true))return;task=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,()->Bukkit.getOnlinePlayers().forEach(this::update),20,Math.max(20,plugin.getConfig().getInt("scoreboard.update-ticks",20)));}
    public void stop(){if(task!=0)Bukkit.getScheduler().cancelTask(task);}
    public void toggle(Player p){boolean v=!db.preference(p.getUniqueId(),"scoreboard-visible","true").equalsIgnoreCase("false");db.preference(p.getUniqueId(),"scoreboard-visible",String.valueOf(!v));if(v)p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());else update(p);}
    public void update(Player p){
        if(db.preference(p.getUniqueId(),"scoreboard-visible","true").equalsIgnoreCase("false"))return;
        Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard(); Objective obj=board.registerNewObjective("flovex","dummy",color(title(p)));obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        String style=db.preference(p.getUniqueId(),"scoreboard-style","modern");String path=style.equalsIgnoreCase("legacy")?"legacy.lines":"modern.lines";
        List<String> lines=plugin.getConfig().getStringList("scoreboard."+path);
        int score=lines.size(); Set<String> used=new HashSet<>();
        for(String raw:lines){String line=replace(p,raw);line=color(line);while(!used.add(line))line=line+"§"+(score%10);obj.getScore(line).setScore(score--);}
        p.setScoreboard(board);
    }
    private String title(Player p){String style=db.preference(p.getUniqueId(),"scoreboard-style","modern");return plugin.getConfig().getString("scoreboard."+style+".title","&b&lFLOVEX");}
    private String replace(Player p,String s){return s.replace("%player%",p.getName()).replace("%money%",economy.format(economy.balance(p.getUniqueId()))).replace("%shards%", "0").replace("%kills%",String.valueOf(p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS))).replace("%deaths%",String.valueOf(p.getStatistic(org.bukkit.Statistic.DEATHS))).replace("%playtime%",String.format("%dh",p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE)/72000)).replace("%ping%",String.valueOf(p.getPing())).replace("%date%",LocalDate.now().toString());}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s.replaceAll("(?i)&#([0-9a-f]{6})","§x§$1"));}
}