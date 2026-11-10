package net.flovex.core.util;
import org.bukkit.ChatColor;import java.util.regex.*;
public final class ColorUtil{
 private static final Pattern HEX=Pattern.compile("(?i)&#([0-9a-f]{6})");
 private ColorUtil(){}
 public static String color(String input){if(input==null)return "";Matcher m=HEX.matcher(input);StringBuffer out=new StringBuffer();while(m.find()){String h=m.group(1);StringBuilder code=new StringBuilder("§x");for(char ch:h.toCharArray())code.append('§').append(ch);m.appendReplacement(out,Matcher.quoteReplacement(code.toString()));}m.appendTail(out);return ChatColor.translateAlternateColorCodes('&',out.toString());}
}