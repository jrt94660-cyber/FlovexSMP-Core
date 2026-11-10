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
 private static final String TITLE=ChatColor.DARK_GRAY+"FlovexCore Menu";
 private static final Map<Integer,String> COMMANDS=Map.ofEntries(Map.entry(10,"homes"),Map.entry(11,"ah"),Map.entry(12,"orders"),Map.entry(13,"sell"),Map.entry(14,"worth"),Map.entry(15,"friends"),Map.entry(16,"stats"),Map.entry(21,"settings"),Map.entry(22,"rtp"),Map.entry(23,"pvp"),Map.entry(24,"ffa"));
 public MenuService(FlovexCore p,PlatformService s){plugin=p;platform=s;}
 public void show(Player p,String title,String body){
  if(!platform.isBedrock(p)&&plugin.getConfig().getBoolean("menus.java-native-dialogs",true)){
   try{
    List<ActionButton> buttons=new ArrayList<>();
    add(buttons,"Homes","/homes");add(buttons,"Auction House","/ah");add(buttons,"Orders","/orders");add(buttons,"Sell","/sell");add(buttons,"Worth","/worth");add(buttons,"Friends","/friends");add(buttons,"Stats","/stats");add(buttons,"Settings","/settings");add(buttons,"RTP","/rtp");add(buttons,"PvP","/pvp");add(buttons,"FFA","/ffa");
    p.showDialog(Dialog.create(b->b.empty().base(DialogBase.builder(Component.text(title)).body(List.of(DialogBody.plainMessage(Component.text(body)))).build()).type(DialogType.multiAction(buttons,null,2))));
    return;
   }catch(Throwable ex){plugin.getLogger().warning("Native dialog unavailable: "+ex.getMessage());}
  }
  Inventory inv=Bukkit.createInventory(null,27,TITLE);
  put(inv,10,Material.MAP,"Homes","/homes");put(inv,11,Material.DIAMOND,"Auction House","/ah");put(inv,12,Material.WRITABLE_BOOK,"Orders","/orders");put(inv,13,Material.HOPPER,"Sell","/sell");put(inv,14,Material.GOLD_INGOT,"Worth","/worth");put(inv,15,Material.PLAYER_HEAD,"Friends","/friends");put(inv,16,Material.PAPER,"Stats","/stats");put(inv,21,Material.COMPARATOR,"Settings","/settings");put(inv,22,Material.ENDER_PEARL,"Random Teleport","/rtp");put(inv,23,Material.IRON_SWORD,"PvP","/pvp");put(inv,24,Material.SHIELD,"FFA","/ffa");
  p.openInventory(inv);
 }
 private void add(List<ActionButton> b,String label,String command){b.add(ActionButton.create(Component.text(label),Component.text("Open "+label),150,DialogAction.commandTemplate(command)));}
 private void put(Inventory inv,int slot,Material type,String label,String command){ItemStack i=new ItemStack(type);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.AQUA+label);m.setLore(List.of(ChatColor.GRAY+"Click to open",ChatColor.DARK_GRAY+command));i.setItemMeta(m);inv.setItem(slot,i);}
 @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!e.getView().getTitle().equals(TITLE))return;e.setCancelled(true);String command=COMMANDS.get(e.getRawSlot());if(command!=null){p.closeInventory();Bukkit.dispatchCommand(p,command);}}
}