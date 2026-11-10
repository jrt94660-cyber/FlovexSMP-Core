package net.flovex.market.ui;

import net.flovex.market.FlovexMarket;
import net.flovex.market.data.Storage;
import net.flovex.market.market.MarketManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class MarketUI implements Listener {
    private final FlovexMarket plugin; private final MarketManager market; private final Storage db;
    public MarketUI(FlovexMarket p,MarketManager m,Storage d){plugin=p;market=m;db=d;}
    private ItemStack item(Material type,String name,List<String> lore){ItemStack i=new ItemStack(type);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;}
    public void openAh(Player p,int page){
        Inventory inv=Bukkit.createInventory(null,54,"§8Flovex Market §7- §f"+(page+1));
        int slot=0;for(var a:market.auctions(page)){try{ItemStack stack=Storage.decode((byte[])a.get("item"));ItemMeta meta=stack.getItemMeta();List<String> lore=meta==null||meta.getLore()==null?new ArrayList<>():new ArrayList<>(meta.getLore());lore.add("§7Listing: §f#"+a.get("id"));lore.add("§7Price: §a$"+a.get("price"));lore.add("§eClick to purchase");meta.setLore(lore);stack.setItemMeta(meta);inv.setItem(slot++,stack);}catch(Exception ignored){}}
        inv.setItem(45,item(Material.ARROW,"§ePrevious",List.of("§7Page "+page)));
        inv.setItem(49,item(Material.BOOK,"§bMarket",List.of("§7Use /ah sell <price>","§7Use /ah claim")));
        inv.setItem(53,item(Material.ARROW,"§eNext",List.of("§7Page "+(page+2))));
        p.openInventory(inv);
    }
    public void openSettings(Player p){
        Inventory inv=Bukkit.createInventory(null,27,"§8Flovex Settings");
        putToggle(inv,10,Material.MAP,"scoreboard-visible","Scoreboard");
        putToggle(inv,11,Material.NAME_TAG,"money-display","Money display");
        putToggle(inv,12,Material.AMETHYST_SHARD,"shards-display","Shards display");
        putToggle(inv,13,Material.BELL,"auction-notifications","Auction notifications");
        putToggle(inv,14,Material.PAPER,"order-notifications","Order notifications");
        putToggle(inv,15,Material.GOLD_INGOT,"quick-buy","Quick buy");
        putToggle(inv,16,Material.HOPPER,"quick-sell","Quick sell");
        inv.setItem(22,item(Material.COMPARATOR,"§bScoreboard style",List.of("§7Current: "+db.preference(p.getUniqueId(),"scoreboard-style","modern"),"§eClick to switch modern / legacy")));
        p.openInventory(inv);
    }
    private void putToggle(Inventory inv,int slot,Material mat,String key,String label){inv.setItem(slot,item(mat,"§f"+label,List.of("§7Current: "+(db.preference(UUID.nameUUIDFromBytes(new byte[0]),key,"") ))));}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();
        if(!title.startsWith("§8Flovex"))return;e.setCancelled(true);int slot=e.getRawSlot();if(slot<0||slot>=e.getInventory().getSize())return;
        if(title.startsWith("§8Flovex Market")){
            if(slot==45){openAh(p,0);return;}if(slot==53){openAh(p,1);return;}
            ItemStack clicked=e.getCurrentItem();if(clicked==null||clicked.getType().isAir())return;
            if(clicked.hasItemMeta()&&clicked.getItemMeta().hasLore())for(String line:clicked.getItemMeta().getLore())if(line.startsWith("§7Listing: §f#")){try{long id=Long.parseLong(line.substring(line.lastIndexOf('#')+1));if(market.buy(p,id))p.sendMessage("§aPurchase successful. /ah claim to collect.");else p.sendMessage("§cPurchase failed or listing is unavailable.");}catch(NumberFormatException ignored){}break;}
        } else if(title.equals("§8Flovex Settings")){
            if(slot==22){String next=db.preference(p.getUniqueId(),"scoreboard-style","modern").equalsIgnoreCase("modern")?"legacy":"modern";db.preference(p.getUniqueId(),"scoreboard-style",next);p.sendMessage("§aScoreboard style: "+next);openSettings(p);return;}
            String[] keys={"scoreboard-visible","money-display","shards-display","auction-notifications","order-notifications","quick-buy","quick-sell"};int[] slots={10,11,12,13,14,15,16};
            for(int i=0;i<slots.length;i++)if(slot==slots[i]){String key=keys[i];boolean next=!Boolean.parseBoolean(db.preference(p.getUniqueId(),key,plugin.getConfig().getString("settings.defaults."+key,"true")));db.preference(p.getUniqueId(),key,String.valueOf(next));p.sendMessage("§a"+key+": "+next);openSettings(p);break;}
        }
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTitle().startsWith("§8Flovex"))e.setCancelled(true);}
}