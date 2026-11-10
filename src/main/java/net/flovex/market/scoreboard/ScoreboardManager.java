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
import java.util.concurrent.atomic.AtomicInteger;

public final class ScoreboardManager {
    private final FlovexMarket plugin; private final EconomyHook economy; private final Storage db; private int task; private final AtomicInteger frame=new AtomicInteger();
    public ScoreboardManager(FlovexMarket p,EconomyHook e,Storage d){plugin=p;economy=e;db=d;}
    public void start(){if(!plugin.getConfig().getBoolean("scoreboard.enabled",true))return;task=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,()->Bukkit.getOnlinePlayers().forEach(this::update),20,Math.max(20,plugin.getConfig().getInt("scoreboard.update-ticks",20)));}
    public void stop(){if(task!=0)Bukkit.getScheduler().cancelTask(task);}
    public void toggle(Player p){boolean visible=!db.preference(p.getUniqueId(),"scoreboard-visible","true").equalsIgnoreCase("false");db.preference(p.getUniqueId(),"scoreboard-visible",String.valueOf(!visible));if(visible)p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());else update(p);}
    public void update(Player p){
        if(db.preference(p.getUniqueId(),"scoreboard-visible","true").equalsIgnoreCase("false"))return;
        var cfg=plugin.yaml("scoreboard.yml");String style=db.preference(p.getUniqueId(),"scoreboard-style","modern").toLowerCase(Locale.ROOT);
        String title=cfg.getString(style+".title","&b&lFLOVEX");
        if(cfg.getBoolean("animation.enabled",false)){List<String> frames=cfg.getStringList("animation.frames");if(!frames.isEmpty())title=frames.get(Math.floorMod(frame.getAndIncrement(),frames.size()));}
        Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard();Objective obj=board.registerNewObjective("flovex","dummy",color(title));obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines=cfg.getStringList(style+".lines");int score=lines.size();Set<String> used=new HashSet<>();
        for(String raw:lines){String line=replace(p,raw);line=color(line);while(!used.add(line))line=line+"§"+(score%10);obj.getScore(line).setScore(score--);}
        p.setScoreboard(board);
    }
    private String replace(Player p,String s){
        if(!Boolean.parseBoolean(db.preference(p.getUniqueId(),"money-display","true")))s=s.replace("%money%","");
        if(!Boolean.parseBoolean(db.preference(p.getUniqueId(),"shards-display","true")))s=s.replace("%shards%","");
        return s.replace("%player%",p.getName()).replace("%money%",economy.format(economy.balance(p.getUniqueId()))).replace("%shards%",shards(p)).replace("%kills%",String.valueOf(p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS))).replace("%deaths%",String.valueOf(p.getStatistic(org.bukkit.Statistic.DEATHS))).replace("%playtime%",String.format("%dh",p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE)/72000)).replace("%ping%",String.valueOf(p.getPing())).replace("%date%",LocalDate.now().toString());
    }
    private String shards(Player p){
        if(!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))return "0";
        try{Class<?> api=Class.forName("me.clip.placeholderapi.PlaceholderAPI");return String.valueOf(api.getMethod("setPlaceholders",Player.class,String.class).invoke(null,p,"%coinsengine_balance_clean_shards%"));}
        catch(ReflectiveOperationException ex){return "0";}
    }
    private String color(String s){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)&#([0-9a-f]{6})").matcher(s);StringBuffer out=new StringBuffer();
        while(m.find()){String h=m.group(1);StringBuilder replacement=new StringBuilder("§x");for(char c:h.toCharArray())replacement.append('§').append(c);m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(replacement.toString()));}
        m.appendTail(out);return ChatColor.translateAlternateColorCodes('&',out.toString());
    }
}