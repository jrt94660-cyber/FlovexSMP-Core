package net.flovex.core.data;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class Storage implements AutoCloseable {
 private final Connection connection;
 public Storage(Path folder,String file) throws Exception {
  Files.createDirectories(folder); Class.forName("org.sqlite.JDBC");
  connection=DriverManager.getConnection("jdbc:sqlite:"+folder.resolve(file));
  try(Statement s=connection.createStatement()){
   s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA busy_timeout=5000");
   s.execute("CREATE TABLE IF NOT EXISTS player_data(uuid TEXT PRIMARY KEY,last_name TEXT,first_join INTEGER,play_ticks INTEGER DEFAULT 0,last_seen INTEGER DEFAULT 0)");
   s.execute("CREATE TABLE IF NOT EXISTS player_stats(uuid TEXT PRIMARY KEY,kills INTEGER DEFAULT 0,deaths INTEGER DEFAULT 0,blocks_broken INTEGER DEFAULT 0,blocks_placed INTEGER DEFAULT 0)");
   s.execute("CREATE TABLE IF NOT EXISTS social(owner TEXT NOT NULL,target TEXT NOT NULL,kind TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(owner,target,kind))");
   s.execute("CREATE TABLE IF NOT EXISTS requests(target TEXT NOT NULL,sender TEXT NOT NULL,kind TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(target,sender,kind))");
   s.execute("CREATE TABLE IF NOT EXISTS auctions(id INTEGER PRIMARY KEY AUTOINCREMENT,seller TEXT NOT NULL,item BLOB NOT NULL,price REAL NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL,sold INTEGER DEFAULT 0)");
   s.execute("CREATE TABLE IF NOT EXISTS orders(id INTEGER PRIMARY KEY AUTOINCREMENT,buyer TEXT NOT NULL,material TEXT NOT NULL,remaining INTEGER NOT NULL,unit_price REAL NOT NULL,reserved REAL NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL)");
   s.execute("CREATE TABLE IF NOT EXISTS claims(id INTEGER PRIMARY KEY AUTOINCREMENT,owner TEXT NOT NULL,kind TEXT NOT NULL,item BLOB,amount REAL DEFAULT 0,source TEXT,created INTEGER NOT NULL,claimed INTEGER DEFAULT 0)");
   s.execute("CREATE TABLE IF NOT EXISTS reports(id INTEGER PRIMARY KEY AUTOINCREMENT,reporter TEXT NOT NULL,target TEXT NOT NULL,reason TEXT NOT NULL,created INTEGER NOT NULL,status TEXT DEFAULT 'OPEN')");
   s.execute("CREATE TABLE IF NOT EXISTS chat_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT NOT NULL,name TEXT NOT NULL,message TEXT NOT NULL,created INTEGER NOT NULL)");
   s.execute("CREATE TABLE IF NOT EXISTS blacklists(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,staff TEXT NOT NULL,reason TEXT NOT NULL,created INTEGER NOT NULL,active INTEGER DEFAULT 1)");
   s.execute("CREATE TABLE IF NOT EXISTS meta_state(id INTEGER PRIMARY KEY CHECK(id=1),current_item TEXT NOT NULL,next_rotation INTEGER NOT NULL)");
   s.execute("CREATE TABLE IF NOT EXISTS portals(name TEXT PRIMARY KEY,world TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,z REAL NOT NULL,radius REAL NOT NULL,server TEXT NOT NULL,creator TEXT NOT NULL,created INTEGER NOT NULL)");
   try{s.execute("ALTER TABLE bounties ADD COLUMN reward BLOB");}catch(SQLException ignored){}
   s.execute("CREATE INDEX IF NOT EXISTS idx_auctions_active ON auctions(sold,expires)");
   s.execute("CREATE INDEX IF NOT EXISTS idx_orders_material ON orders(material,remaining,expires)");
   s.execute("CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims(owner,claimed)");
   s.execute("CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status,created)");
   s.execute("CREATE INDEX IF NOT EXISTS idx_chat_logs_name ON chat_logs(name,created)");
   s.execute("CREATE TABLE IF NOT EXISTS punishments(id INTEGER PRIMARY KEY AUTOINCREMENT,target TEXT NOT NULL,staff TEXT NOT NULL,type TEXT NOT NULL,reason TEXT,expires INTEGER NOT NULL,created INTEGER NOT NULL,active INTEGER DEFAULT 1)");
   s.execute("CREATE TABLE IF NOT EXISTS bounties(id INTEGER PRIMARY KEY AUTOINCREMENT,target TEXT NOT NULL,creator TEXT NOT NULL,description TEXT NOT NULL,created INTEGER NOT NULL,active INTEGER DEFAULT 1,reward BLOB)");
   s.execute("CREATE TABLE IF NOT EXISTS preferences(uuid TEXT NOT NULL,key TEXT NOT NULL,value TEXT NOT NULL,PRIMARY KEY(uuid,key))");
   s.execute("CREATE TABLE IF NOT EXISTS teams(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,owner TEXT NOT NULL,home_world TEXT,home_x REAL,home_y REAL,home_z REAL,home_yaw REAL,home_pitch REAL,pvp INTEGER DEFAULT 1,created INTEGER NOT NULL)");
   s.execute("CREATE TABLE IF NOT EXISTS team_members(team_id INTEGER NOT NULL,uuid TEXT NOT NULL,role TEXT NOT NULL,joined INTEGER NOT NULL,PRIMARY KEY(team_id,uuid),FOREIGN KEY(team_id) REFERENCES teams(id) ON DELETE CASCADE)");
   s.execute("CREATE TABLE IF NOT EXISTS team_invites(team_id INTEGER NOT NULL,inviter TEXT NOT NULL,target TEXT NOT NULL,expires INTEGER NOT NULL,PRIMARY KEY(team_id,target),FOREIGN KEY(team_id) REFERENCES teams(id) ON DELETE CASCADE)");
   s.execute("CREATE TABLE IF NOT EXISTS warps(name TEXT PRIMARY KEY,world TEXT NOT NULL,x REAL,y REAL,z REAL,yaw REAL,pitch REAL,creator TEXT NOT NULL,created INTEGER NOT NULL)");
  }
 }
 public synchronized int update(String sql,Object...args){try(PreparedStatement p=connection.prepareStatement(sql)){bind(p,args);return p.executeUpdate();}catch(SQLException e){throw new IllegalStateException(e);}}
 public synchronized List<Map<String,Object>> rows(String sql,Object...args){try(PreparedStatement p=connection.prepareStatement(sql)){bind(p,args);try(ResultSet r=p.executeQuery()){List<Map<String,Object>> out=new ArrayList<>();ResultSetMetaData m=r.getMetaData();while(r.next()){Map<String,Object> row=new HashMap<>();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}catch(SQLException e){throw new IllegalStateException(e);}}
 public synchronized <T>T transaction(SqlWork<T> work){boolean old=true;try{old=connection.getAutoCommit();connection.setAutoCommit(false);T result=work.run(connection);connection.commit();return result;}catch(Exception e){try{connection.rollback();}catch(SQLException ignored){}throw new IllegalStateException(e);}finally{try{connection.setAutoCommit(old);}catch(SQLException ignored){}}}
 public String preference(UUID uuid,String key,String fallback){List<Map<String,Object>> r=rows("SELECT value FROM preferences WHERE uuid=? AND key=?",uuid.toString(),key);return r.isEmpty()?fallback:String.valueOf(r.getFirst().get("value"));}
 public void setPreference(UUID uuid,String key,String value){update("INSERT INTO preferences(uuid,key,value) VALUES(?,?,?) ON CONFLICT(uuid,key) DO UPDATE SET value=excluded.value",uuid.toString(),key,value);}
 private static void bind(PreparedStatement p,Object...args)throws SQLException{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
 @FunctionalInterface public interface SqlWork<T>{T run(Connection connection)throws Exception;}
 @Override public synchronized void close(){try{connection.close();}catch(SQLException ignored){}}
}