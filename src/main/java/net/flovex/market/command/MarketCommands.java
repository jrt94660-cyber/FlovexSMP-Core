package net.flovex.market.command;

import net.flovex.market.FlovexMarket;
import net.flovex.market.market.MarketManager;
import net.flovex.market.ui.MarketUI;
import net.flovex.market.scoreboard.ScoreboardManager;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class MarketCommands implements CommandExecutor,TabCompleter {
    private final FlovexMarket plugin;private final MarketManager market;private final MarketUI ui;private final ScoreboardManager scoreboard;
    public MarketCommands(FlovexMarket p,MarketManager m,MarketUI u,ScoreboardManager s){plugin=p;market=m;ui=u;scoreboard=s;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String cmd=command.getName().toLowerCase(Locale.ROOT);
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        switch(cmd){
            case "ah" -> {if(args.length==0){ui.openAh(p,0);return true;}if(args[0].equalsIgnoreCase("sell")){if(args.length<2){p.sendMessage("§cUsage: /ah sell <price>");return true;}try{if(!market.list(p,Double.parseDouble(args[1])))p.sendMessage("§cCould not list item. Check price, fee, listing limit and held item.");}catch(NumberFormatException ex){p.sendMessage("§cInvalid price.");}return true;}if(args[0].equalsIgnoreCase("claim")){p.sendMessage("§aClaimed "+market.claim(p)+" item stack(s).");return true;}if(args[0].equalsIgnoreCase("search")){ui.openAh(p,0);p.sendMessage("§7Search filter is not yet exposed in this GUI; use the listing list.");return true;}}
            case "settings" -> {ui.openSettings(p);return true;}
            case "orders" -> {if(args.length==0){p.sendMessage("§b/orders create <material> <amount> <unit-price>");p.sendMessage("§b/orders cancel <id>");for(var o:market.orders(null))p.sendMessage("§7#"+o.get("id")+" §f"+o.get("material")+" x"+o.get("remaining")+" §a$"+o.get("unit_price"));return true;}if(args[0].equalsIgnoreCase("create")&&args.length>=4){try{Material mat=Material.valueOf(args[1].toUpperCase(Locale.ROOT));if(!market.createOrder(p,mat,Integer.parseInt(args[2]),Double.parseDouble(args[3])))p.sendMessage("§cOrder could not be created (check balance and limits).");}catch(Exception ex){p.sendMessage("§cUsage: /orders create <material> <amount> <unit-price>");}return true;}if(args[0].equalsIgnoreCase("cancel")){p.sendMessage("§cCancellation is not available in this build.");return true;}}
            case "sell","sellhand" -> {ItemStack hand=p.getInventory().getItemInMainHand();double earned=market.sell(p,hand);p.sendMessage(earned>0?"§aSold items for §f$"+String.format("%,.2f",earned):"§cNo compatible buy orders or worth price found.");return true;}
            case "sellall","sellmulti" -> {double total=0;for(int i=0;i<p.getInventory().getStorageContents().length;i++){ItemStack item=p.getInventory().getItem(i);if(item!=null&&!item.getType().isAir())total+=market.sell(p,item);}p.sendMessage("§aTotal earned: §f$"+String.format("%,.2f",total));return true;}
            case "worth" -> {Material mat=args.length>0?Material.matchMaterial(args[0]):p.getInventory().getItemInMainHand().getType();if(mat==null){p.sendMessage("§cUnknown material.");return true;}p.sendMessage("§e"+mat.name()+" §7worth: §a$"+market.worth(mat)+" §7per item");return true;}
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return switch(command.getName().toLowerCase(Locale.ROOT)){case "ah"->List.of("sell","claim","search");case "orders"->List.of("create","cancel");default->List.of();};
        if(command.getName().equalsIgnoreCase("orders")&&args.length==2&&args[0].equalsIgnoreCase("create"))return Arrays.stream(Material.values()).filter(m->m.isItem()&&!m.isAir()).map(m->m.name().toLowerCase(Locale.ROOT)).filter(s->s.startsWith(args[1].toLowerCase(Locale.ROOT))).limit(50).toList();
        return List.of();
    }
}