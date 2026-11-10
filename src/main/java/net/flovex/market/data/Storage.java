package net.flovex.market.data;

import net.flovex.market.FlovexMarket;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class Storage implements AutoCloseable {
    private final Connection connection;
    public Storage(FlovexMarket plugin) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path file = plugin.getDataFolder().toPath().resolve(plugin.getConfig().getString("storage.file","market.db"));
        Files.createDirectories(file.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement s=connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS auctions(id INTEGER PRIMARY KEY AUTOINCREMENT,seller TEXT NOT NULL,item BLOB NOT NULL,price REAL NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL,sold INTEGER NOT NULL DEFAULT 0,buyer TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS orders(id INTEGER PRIMARY KEY AUTOINCREMENT,buyer TEXT NOT NULL,material TEXT NOT NULL,remaining INTEGER NOT NULL,unit_price REAL NOT NULL,reserved REAL NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS claims(id INTEGER PRIMARY KEY AUTOINCREMENT,owner TEXT NOT NULL,item BLOB NOT NULL,created INTEGER NOT NULL,claimed INTEGER NOT NULL DEFAULT 0,source TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS preferences(uuid TEXT NOT NULL,pref TEXT NOT NULL,value TEXT NOT NULL,PRIMARY KEY(uuid,pref))");
            s.execute("CREATE TABLE IF NOT EXISTS transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,actor TEXT NOT NULL,type TEXT NOT NULL,amount REAL NOT NULL,reference TEXT,created INTEGER NOT NULL)");
        }
    }
    public synchronized <T> T transaction(SqlWork<T> work) throws Exception {
        boolean old=connection.getAutoCommit(); connection.setAutoCommit(false);
        try { T result=work.run(connection); connection.commit(); return result; }
        catch(Exception e){ connection.rollback(); throw e; } finally { connection.setAutoCommit(old); }
    }
    public synchronized List<Map<String,Object>> rows(String sql,Object... args) throws SQLException {
        try(PreparedStatement p=connection.prepareStatement(sql)){ bind(p,args); try(ResultSet r=p.executeQuery()){
            List<Map<String,Object>> out=new ArrayList<>(); ResultSetMetaData m=r.getMetaData();
            while(r.next()){ Map<String,Object> row=new HashMap<>(); for(int i=1;i<=m.getColumnCount();i++) row.put(m.getColumnLabel(i),r.getObject(i)); out.add(row); } return out;
        }}
    }
    public synchronized int update(String sql,Object... args) throws SQLException { try(PreparedStatement p=connection.prepareStatement(sql)){bind(p,args);return p.executeUpdate();} }
    private static void bind(PreparedStatement p,Object... args)throws SQLException{ for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]); }
    public static byte[] encode(ItemStack item) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); try(BukkitObjectOutputStream out=new BukkitObjectOutputStream(b)){out.writeObject(item);} return b.toByteArray();
    }
    public static ItemStack decode(byte[] bytes) throws IOException,ClassNotFoundException {
        try(BukkitObjectInputStream in=new BukkitObjectInputStream(new ByteArrayInputStream(bytes))){return (ItemStack)in.readObject();}
    }
    public synchronized String preference(UUID uuid,String key,String fallback){
        try { var r=rows("SELECT value FROM preferences WHERE uuid=? AND pref=?",uuid.toString(),key); return r.isEmpty()?fallback:String.valueOf(r.getFirst().get("value")); } catch(SQLException e){return fallback;}
    }
    public synchronized void setPreference(UUID uuid,String key,String value){
        try { update("INSERT INTO preferences(uuid,pref,value) VALUES(?,?,?) ON CONFLICT(uuid,pref) DO UPDATE SET value=excluded.value",uuid.toString(),key,value); } catch(SQLException ignored){}
    }
    @Override public synchronized void close(){try{connection.close();}catch(SQLException ignored){}}
    @FunctionalInterface public interface SqlWork<T>{T run(Connection connection)throws Exception;}
}