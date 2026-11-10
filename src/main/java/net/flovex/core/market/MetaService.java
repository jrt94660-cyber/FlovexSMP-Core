package net.flovex.core.market;
import net.flovex.core.data.Storage;import org.bukkit.Material;import org.bukkit.configuration.file.FileConfiguration;import java.util.*;
public final class MetaService{
 private final Storage db;private final FileConfiguration worth;private final List<Material> items;private final boolean enabled;private final double multiplier;private final long interval;private Material current;private long next;
 public MetaService(Storage d,FileConfiguration cfg){db=d;worth=cfg;enabled=cfg.getBoolean("meta.enabled",false);multiplier=Math.max(1.0,cfg.getDouble("meta.multiplier",1.15));interval=Math.max(1,cfg.getLong("meta.interval-days",14))*86400000L+Math.max(0,cfg.getLong("meta.interval-hours",0))*3600000L;List<Material> parsed=new ArrayList<>();for(String name:cfg.getStringList("meta.items")){Material m=Material.matchMaterial(name);if(m!=null&&cfg.getDouble("prices."+m.name(),0)>0)parsed.add(m);}items=List.copyOf(parsed);load();}
 private void load(){if(items.isEmpty()){current=null;next=0;return;}var r=db.rows("SELECT current_item,next_rotation FROM meta_state WHERE id=1");if(r.isEmpty()){current=items.getFirst();next=System.currentTimeMillis()+interval;save();return;}Material saved=Material.matchMaterial(String.valueOf(r.getFirst().get("current_item")));current=saved!=null&&items.contains(saved)?saved:items.getFirst();next=((Number)r.getFirst().get("next_rotation")).longValue();if(next<=0){next=System.currentTimeMillis()+interval;save();}}
 private void save(){if(current!=null)db.update("INSERT INTO meta_state(id,current_item,next_rotation) VALUES(1,?,?) ON CONFLICT(id) DO UPDATE SET current_item=excluded.current_item,next_rotation=excluded.next_rotation",current.name(),next);}
 public boolean enabled(){return enabled&&!items.isEmpty();}
 public Material current(){return current;}
 public double getMultiplierFor(Material material){return enabled()&&current==material?multiplier:1.0;}
 public long remainingSeconds(){return Math.max(0,(next-System.currentTimeMillis())/1000L);}
 public void checkRotation(){if(!enabled()||items.isEmpty()||System.currentTimeMillis()<next)return;int index=items.indexOf(current);current=items.get((index+1)%items.size());next=System.currentTimeMillis()+interval;save();}
 public String describe(){if(!enabled())return "Farming meta is disabled.";return "Current meta: "+current.name()+" | Sell multiplier: x"+multiplier+" | Next rotation in "+remainingSeconds()+"s";}
}