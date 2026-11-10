package net.flovex.market.market;

import net.flovex.market.FlovexMarket;
import net.flovex.market.data.Storage;
import net.flovex.market.economy.EconomyHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import java.sql.*;
import java.util.*;

public final class MarketManager implements Listener {
    private final FlovexMarket plugin; private final Storage db; private final EconomyHook economy;
    public MarketManager(FlovexMarket plugin,Storage db,EconomyHook economy){this.plugin=plugin;this.db=db;this.economy=economy;}
    public List<Map<String,Object>> auctions(int page){
        try{return db.rows("SELECT id,seller,item,price,created,expires FROM auctions WHERE sold=0 AND expires>? ORDER BY created DESC LIMIT 45 OFFSET ?",System.currentTimeMillis(),Math.max(0,page)*45);}catch(SQLException e){return List.of();}
    }
    public int listingCount(UUID seller){try{return db.rows("SELECT id FROM auctions WHERE seller=? AND sold=0 AND expires>?",seller.toString(),System.currentTimeMillis()).size();}catch(SQLException e){return 0;}}
    public boolean list(Player player,double price){
        ItemStack hand=player.getInventory().getItemInMainHand();
        if(hand.getType().isAir()||hand.getAmount()<1||!Double.isFinite(price)||price<plugin.getConfig().getDouble("auction.min-price",1)||price>plugin.getConfig().getDouble("auction.max-price",1e12))return false;
        if(listingCount(player.getUniqueId())>=plugin.getConfig().getInt("auction.max-listings",10))return false;
        double fee=plugin.getConfig().getDouble("economy.listing-fee",0);
        if(fee>0&&!economy.withdraw(player.getUniqueId(),fee))return false;
        try{
            byte[] item=Storage.encode(hand.clone()); long now=System.currentTimeMillis(); long end=now+plugin.getConfig().getLong("auction.duration-hours",48)*3600000L;
            db.update("INSERT INTO auctions(seller,item,price,created,expires) VALUES(?,?,?,?,?)",player.getUniqueId().toString(),item,price,now,end);
            player.getInventory().setItemInMainHand(null); player.sendMessage("§aListed item for "+economy.format(price)+"."); return true;
        }catch(Exception e){if(fee>0)economy.deposit(player.getUniqueId(),fee);plugin.getLogger().warning("Auction listing failed: "+e.getMessage());return false;}
    }
    public boolean buy(Player buyer,long id){
        try{
            var rows=db.rows("SELECT * FROM auctions WHERE id=? AND sold=0 AND expires>?",id,System.currentTimeMillis()); if(rows.isEmpty())return false;
            var a=rows.getFirst(); UUID seller=UUID.fromString((String)a.get("seller")); double price=((Number)a.get("price")).doubleValue();
            if(seller.equals(buyer.getUniqueId())||!economy.withdraw(buyer.getUniqueId(),price))return false;
            byte[] payload=(byte[])a.get("item"); double tax=plugin.getConfig().getDouble("economy.auction-tax-percent",5);
            try{
                db.transaction(c->{
                    try(PreparedStatement p=c.prepareStatement("UPDATE auctions SET sold=1,buyer=? WHERE id=? AND sold=0")){p.setString(1,buyer.getUniqueId().toString());p.setLong(2,id);if(p.executeUpdate()!=1)throw new SQLException("Listing already sold");}
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO claims(owner,item,created,source) VALUES(?,?,?,?)")){p.setString(1,buyer.getUniqueId().toString());p.setBytes(2,payload);p.setLong(3,System.currentTimeMillis());p.setString(4,"auction:"+id);p.executeUpdate();}
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO claims(owner,item,created,source) VALUES(?,?,?,?)")){p.setString(1,seller.toString());p.setBytes(2,Storage.encode(new ItemStack(Material.PAPER)));p.setLong(3,System.currentTimeMillis());p.setString(4,"auction-proceeds:"+id+":"+price*(1-tax/100));p.executeUpdate();}
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO transactions(actor,type,amount,reference,created) VALUES(?,?,?,?,?)")){p.setString(1,buyer.getUniqueId().toString());p.setString(2,"AUCTION_BUY");p.setDouble(3,price);p.setString(4,String.valueOf(id));p.setLong(5,System.currentTimeMillis());p.executeUpdate();}
                    return null;
                });
            }catch(Exception ex){economy.deposit(buyer.getUniqueId(),price);throw ex;}
            double payout=price*(1-tax/100); economy.deposit(seller,payout);
            // Remove the proceeds marker; proceeds are paid directly through Vault.
            db.update("DELETE FROM claims WHERE owner=? AND source=?",seller.toString(),"auction-proceeds:"+id+":"+payout);
            buyer.sendMessage("§aPurchased listing #"+id+". Use §e/ah claim §ato collect it."); return true;
        }catch(Exception e){plugin.getLogger().warning("Auction purchase failed: "+e.getMessage());return false;}
    }
    public int claim(Player player){
        int count=0;
        try{
            for(var row:db.rows("SELECT id,item FROM claims WHERE owner=? AND claimed=0 ORDER BY id",player.getUniqueId().toString())){
                long id=((Number)row.get("id")).longValue();
                ItemStack item=Storage.decode((byte[])row.get("item"));
                if(item.getType()==Material.PAPER && db.rows("SELECT id FROM claims WHERE id=? AND source LIKE 'auction-proceeds:%'",id).size()>0) continue;
                if(player.getInventory().firstEmpty()<0)break;
                boolean marked=db.update("UPDATE claims SET claimed=1 WHERE id=? AND claimed=0",id)==1;
                if(marked){player.getInventory().addItem(item);count++;}
            }
        }catch(Exception e){plugin.getLogger().warning("Claim delivery failed: "+e.getMessage());}
        return count;
    }
    public List<Map<String,Object>> orders(String material){
        try{return material==null?db.rows("SELECT * FROM orders WHERE remaining>0 AND expires>? ORDER BY unit_price DESC",System.currentTimeMillis()):db.rows("SELECT * FROM orders WHERE material=? AND remaining>0 AND expires>? ORDER BY unit_price DESC",material,System.currentTimeMillis());}catch(SQLException e){return List.of();}
    }
    public boolean createOrder(Player buyer,Material material,int amount,double unitPrice){
        if(!economy.available()||material==null||material.isAir()||amount<1||amount>100000||!Double.isFinite(unitPrice)||unitPrice<plugin.getConfig().getDouble("orders.minimum-unit-price",1))return false;
        try{if(db.rows("SELECT id FROM orders WHERE buyer=? AND remaining>0 AND expires>?",buyer.getUniqueId().toString(),System.currentTimeMillis()).size()>=plugin.getConfig().getInt("orders.max-active-per-player",10))return false;}catch(SQLException e){return false;}
        double reserve=amount*unitPrice,fee=plugin.getConfig().getDouble("economy.order-fee",0);
        if(!economy.withdraw(buyer.getUniqueId(),reserve+fee))return false;
        try{long end=System.currentTimeMillis()+plugin.getConfig().getLong("orders.max-duration-hours",72)*3600000L;
            db.update("INSERT INTO orders(buyer,material,remaining,unit_price,reserved,created,expires) VALUES(?,?,?,?,?,?,?)",buyer.getUniqueId().toString(),material.name(),amount,unitPrice,reserve,System.currentTimeMillis(),end);
            buyer.sendMessage("§aBuy order created. Reserved "+economy.format(reserve)+".");return true;
        }catch(Exception e){economy.deposit(buyer.getUniqueId(),reserve+fee);return false;}
    }
    public double worth(Material material){return plugin.getConfig().getDouble("prices."+material.name(),0);}
    public double sell(Player seller,ItemStack stack){
        if(stack==null||stack.getType().isAir()||stack.getAmount()<1||!economy.available())return 0;
        ItemStack toSell=stack.clone(); int remaining=toSell.getAmount(); double earned=0;
        for(var order:orders(toSell.getType().name())){
            if(remaining<=0)break;
            UUID buyer=UUID.fromString((String)order.get("buyer")); if(buyer.equals(seller.getUniqueId()))continue;
            long id=((Number)order.get("id")).longValue(); int wanted=((Number)order.get("remaining")).intValue();
            double unit=((Number)order.get("unit_price")).doubleValue(); int qty=Math.min(remaining,wanted); double payment=qty*unit;
            if(!economy.deposit(seller.getUniqueId(),payment))continue;
            try{
                int newQty=wanted-qty; double newReserve=newQty*unit;
                byte[] payload=Storage.encode(new ItemStack(toSell.getType(),qty));
                db.transaction(c->{
                    try(PreparedStatement p=c.prepareStatement("UPDATE orders SET remaining=?,reserved=? WHERE id=? AND remaining=?")){p.setInt(1,newQty);p.setDouble(2,newReserve);p.setLong(3,id);p.setInt(4,wanted);if(p.executeUpdate()!=1)throw new SQLException("Order changed");}
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO claims(owner,item,created,source) VALUES(?,?,?,?)")){p.setString(1,buyer.toString());p.setBytes(2,payload);p.setLong(3,System.currentTimeMillis());p.setString(4,"order:"+id);p.executeUpdate();}
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO transactions(actor,type,amount,reference,created) VALUES(?,?,?,?,?)")){p.setString(1,seller.getUniqueId().toString());p.setString(2,"ORDER_FILL");p.setDouble(3,payment);p.setString(4,String.valueOf(id));p.setLong(5,System.currentTimeMillis());p.executeUpdate();}
                    return null;
                });
                remaining-=qty;earned+=payment;
                Player online=plugin.getServer().getPlayer(buyer); if(online!=null)online.sendMessage("§aYour buy order received "+qty+" "+toSell.getType()+". Collect with §e/ah claim§a.");
            }catch(Exception e){economy.withdraw(seller.getUniqueId(),payment);plugin.getLogger().warning("Order fulfillment rolled back: "+e.getMessage());}
        }
        double fixed=worth(toSell.getType()); if(remaining>0&&fixed>0){double payment=remaining*fixed;if(economy.deposit(seller.getUniqueId(),payment)){earned+=payment;remaining=0;}}
        int sold=toSell.getAmount()-remaining;
        if(sold>0){if(sold==stack.getAmount())stack.setAmount(0);else stack.setAmount(stack.getAmount()-sold);}
        return earned;
    }
    @EventHandler public void onJoin(PlayerJoinEvent e){ /* preferences are loaded lazily by the UI and scoreboard */ }
}