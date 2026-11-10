package net.flovex.core.ui;

import net.flovex.core.FlovexCore;
import net.flovex.core.platform.PlatformService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class MenuService implements Listener {
 private final FlovexCore plugin;private final PlatformService platform;
 private record Entry(int slot,Material icon,String label,String command){}
 public MenuService(FlovexCore p,PlatformService s){plugin=p;platform=s;}
 private List<Entry> entries(String title){
  String t=title.toLowerCase(Locale.ROOT);
  if(t.contains("auction"))return List.of(new Entry(10,Material.BOOK,"My Listings","ah my"),new Entry(11,Material.CHEST,"Claim Purchases","ah claim"),new Entry(12,Material.WRITABLE_BOOK,"Buy Orders","orders"),new Entry(13,Material.HOPPER,"Sell Items","sell"),new Entry(14,Material.GOLD_INGOT,"Worth Browser","worth"),new Entry(15,Material.COMPASS,"Main Menu","menu"));
  if(t.contains("sell"))return List.of(new Entry(10,Material.IRON_INGOT,"Sell Held Stack","sellhand"),new Entry(11,Material.HOPPER,"Sell Inventory","sellall"),new Entry(12,Material.CHEST,"Sell Multi","sellmulti"),new Entry(13,Material.BOOK,"Sell Progress","sellprogress"),new Entry(14,Material.PAPER,"Sell History","sellhistory"),new Entry(15,Material.GOLD_INGOT,"Top Sellers","topsell"),new Entry(22,Material.ARROW,"Back","menu"));
  if(t.toLowerCase(Locale.ROOT).contains("setting"))return List.of(new Entry(10,Material.MAP,"Scoreboard","scoreboard"),new Entry(11,Material.COMPARATOR,"Scoreboard Style","settings style"),new Entry(12,Material.ENDER_PEARL,"TPA Auto","tpauto"),new Entry(13,Material.ENDER_EYE,"TPAHere Auto","tpahereauto"),new Entry(14,Material.PAPER,"Private Messages","pm"),new Entry(15,Material.NAME_TAG,"Team Chat","team chat"),new Entry(16,Material.PLAYER_HEAD,"Hide Players","hide"),new Entry(22,Material.ARROW,"Back","menu"));
  if(t.contains("profile"))return List.of(new Entry(11,Material.PAPER,"Statistics","stats"),new Entry(13,Material.CLOCK,"Playtime","playtime"),new Entry(15,Material.ARROW,"Back","menu"));
  return List.of(new Entry(10,Material.MAP,"Homes","homes"),new Entry(11,Material.DIAMOND,"Auction House","ah"),new Entry(12,Material.WRITABLE_BOOK,"Orders","orders"),new Entry(13,Material.HOPPER,"Sell","sell"),new Entry(14,Material.GOLD_INGOT,"Worth","worth"),new Entry(15,Material.PLAYER_HEAD,"Friends","friends"),new Entry(16,Material.PAPER,"Stats","stats"),new Entry(19,Material.COMPARATOR,"Settings","settings"),new Entry(20,Material.ENDER_PEARL,"Random Teleport","rtp"),new Entry(21,Material.IRON_SWORD,"Duels","queue"),new Entry(22,Material.SHIELD,"FFA","ffa"),new Entry(23,Material.BOOK,"Rules","rules"),new Entry(24,Material.SHIELD,"Teams","team"),new Entry(25,Material.COMPASS,"Warps","warps"),new Entry(26,Material.WRITABLE_BOOK,"Help","help"));
 }
 private String menuKey(String title){String t=title.toLowerCase(Locale.ROOT);if(t.contains("auction"))return "auction-house";if(t.contains("home"))return "homes";if(t.contains("setting"))return "settings";return "main";}
 public void show(Player p,String title,String body){
  plugin.getSounds().play(p,"menu-open");
  String key=menuKey(title);String configured=plugin.menusConfig().getString("menus."+key+".title",title);String displayTitle=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',configured));
  List<Entry> entries=entries(title);
  if(!platform.isBedrock(p)&&plugin.getConfig().getBoolean("menus.java-native-dialogs",true)){
   try{List<ActionButton> buttons=new ArrayList<>();for(Entry e:entries)buttons.add(ActionButton.create(Component.text(e.label()),Component.text("Run /"+e.command()),150,DialogAction.commandTemplate("/"+e.command())));p.showDialog(Dialog.create(b->b.empty().base(DialogBase.builder(Component.text(displayTitle)).body(List.of(DialogBody.plainMessage(Component.text(body)))).build()).type(DialogType.multiAction(buttons,null,2))));return;}catch(Throwable ex){plugin.getLogger().warning("Native dialog unavailable: "+ex.getMessage());}
  }
  int rows=Math.max(1,Math.min(6,plugin.menusConfig().getInt("menus."+key+".rows",3)));String inventoryTitle=ChatColor.DARK_GRAY+"Flovex Menu: "+ChatColor.translateAlternateColorCodes('&',configured);Inventory inv=Bukkit.createInventory(null,rows*9,inventoryTitle);
  for(Entry e:entries){ItemStack item=new ItemStack(e.icon());ItemMeta meta=item.getItemMeta();meta.setDisplayName(ChatColor.AQUA+e.label());meta.setLore(List.of(ChatColor.GRAY+"Click to open","§0cmd:"+e.command()));item.setItemMeta(meta);inv.setItem(e.slot(),item);}
  p.openInventory(inv);
 }
 public void showAuction(Player p,String body,List<Map<String,String>> listings){
  plugin.getSounds().play(p,"menu-open");
  List<ActionButton> buttons=new ArrayList<>();
  for(Map<String,String> listing:listings)buttons.add(ActionButton.create(Component.text(listing.get("label")),Component.text("Buy this listing"),150,DialogAction.commandTemplate("/"+listing.get("command"))));
  buttons.add(ActionButton.create(Component.text("My Listings"),Component.text("Manage your auctions"),150,DialogAction.commandTemplate("/ah my")));
  buttons.add(ActionButton.create(Component.text("Claim Purchases"),Component.text("Collect purchases and proceeds"),150,DialogAction.commandTemplate("/ah claim")));
  buttons.add(ActionButton.create(Component.text("Buy Orders"),Component.text("View player buy orders"),150,DialogAction.commandTemplate("/orders")));
  buttons.add(ActionButton.create(Component.text("Sell"),Component.text("Open sell menu"),150,DialogAction.commandTemplate("/sell")));
  buttons.add(ActionButton.create(Component.text("Main Menu"),Component.text("Return to FlovexCore"),150,DialogAction.commandTemplate("/menu")));
  try{p.showDialog(Dialog.create(b->b.empty().base(DialogBase.builder(Component.text(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',plugin.menusConfig().getString("menus.auction-house.title","&8Flovex Auction House"))))).body(List.of(DialogBody.plainMessage(Component.text(body)))).build()).type(DialogType.multiAction(buttons,null,2))));}
  catch(Throwable ex){plugin.getLogger().warning("Auction dialog unavailable: "+ex.getMessage());show(p,"Flovex Auction House",body);}
 }
 public void showHomes(Player p,List<String> homes){
  plugin.getSounds().play(p,"menu-open");
  if(!platform.isBedrock(p)&&plugin.getConfig().getBoolean("menus.java-native-dialogs",true)){
   try{List<ActionButton> buttons=new ArrayList<>();for(String home:homes.stream().limit(12).toList())buttons.add(ActionButton.create(Component.text(home),Component.text("Teleport to "+home),120,DialogAction.commandTemplate("/home "+home)));buttons.add(ActionButton.create(Component.text("Set Home"),Component.text("Save your current location"),120,DialogAction.commandTemplate("/sethome home")));buttons.add(ActionButton.create(Component.text("Main Menu"),Component.text("Return to FlovexCore"),120,DialogAction.commandTemplate("/menu")));p.showDialog(Dialog.create(b->b.empty().base(DialogBase.builder(Component.text(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',plugin.menusConfig().getString("menus.homes.title","&8Your Homes"))))).body(List.of(DialogBody.plainMessage(Component.text(homes.isEmpty()?"You have no homes yet.":"Choose a home to teleport to.")))).build()).type(DialogType.multiAction(buttons,null,2))));return;}catch(Throwable ex){plugin.getLogger().warning("Homes dialog unavailable: "+ex.getMessage());}
  }
  int rows=Math.max(1,Math.min(6,plugin.menusConfig().getInt("menus.homes.rows",6)));String homesTitle=ChatColor.DARK_GRAY+"Flovex Homes: "+ChatColor.translateAlternateColorCodes('&',plugin.menusConfig().getString("menus.homes.title","&8Your Homes"));Inventory inv=Bukkit.createInventory(null,rows*9,homesTitle);int slot=0;for(String home:homes.stream().limit(45).toList()){ItemStack item=new ItemStack(Material.COMPASS);ItemMeta meta=item.getItemMeta();meta.setDisplayName(ChatColor.AQUA+home);meta.setLore(List.of(ChatColor.GRAY+"Click to teleport","§0cmd:home "+home));item.setItemMeta(meta);inv.setItem(slot++,item);}ItemStack set=new ItemStack(Material.MAP);ItemMeta sm=set.getItemMeta();sm.setDisplayName(ChatColor.GREEN+"Set Home");sm.setLore(List.of("§0cmd:sethome home"));set.setItemMeta(sm);inv.setItem(49,set);ItemStack back=new ItemStack(Material.ARROW);ItemMeta bm=back.getItemMeta();bm.setDisplayName(ChatColor.YELLOW+"Main Menu");bm.setLore(List.of("§0cmd:menu"));back.setItemMeta(bm);inv.setItem(53,back);p.openInventory(inv);
 }
 @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(!title.startsWith(ChatColor.DARK_GRAY+"Flovex Menu:")&&!title.startsWith(ChatColor.DARK_GRAY+"Flovex Homes:"))return;e.setCancelled(true);ItemStack item=e.getCurrentItem();if(item==null||!item.hasItemMeta()||!item.getItemMeta().hasLore())return;for(String line:item.getItemMeta().getLore())if(line.startsWith("§0cmd:")){String command=line.substring("§0cmd:".length());p.closeInventory();Bukkit.dispatchCommand(p,command);return;}}
}