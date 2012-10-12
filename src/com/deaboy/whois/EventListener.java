package com.deaboy.whois;

import java.io.Closeable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.deaboy.whois.users.User;
import com.deaboy.whois.users.UserFile;
import com.deaboy.whois.users.User.Field;
import com.deaboy.whois.users.User.Stat;

public class EventListener implements Listener, Closeable
{
	public EventListener()
	{
		Bukkit.getPluginManager().registerEvents(this, Whois.getInstance());
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e)
	{
		if (!UserFile.exists(e.getPlayer().getName()) && Whois.getSettings().getBoolean("whitelist", false))
		{
			e.getPlayer().kickPlayer(Whois.getSettings().getString("deniedmessage", "You are not whitelisted on this server!"));
			e.setJoinMessage(null);
			Bukkit.getLogger().log(Level.INFO, "Oops, nevermind. Whois is in whitelist mode.");
			return;
		}
		
		User u = Whois.loadUser(e.getPlayer());
		
		String message = new String();
		message += ChatColor.YELLOW;
		message += e.getPlayer().getName() + " ";
		
		if (!u.getField(Field.REAL_NAME).isEmpty())
		{
			message += "(" + u.getField(Field.REAL_NAME) + ") ";
			e.getPlayer().setPlayerListName((e.getPlayer().getName() + " (" + u.getField(Field.REAL_NAME) + ")").substring(0, 16));
		}
		message += "has joined the game";
		
		if (u.getField(Field.TIME_JOINED).isEmpty())
		{
			message += " for the first time";
			
			u.setField(Field.TIME_JOINED, new SimpleDateFormat().format(new Date()));
		}
		e.setJoinMessage(message + ".");
		
		if (u.getField(Field.PREFIX_TEXT).isEmpty())
		{
			e.getPlayer().setDisplayName(
					ChatColor.getByChar(u.getField(Field.PLAYER_NAME_COLOR))
					+ e.getPlayer().getName());
		}
		else
		{
			e.getPlayer().setDisplayName(
					ChatColor.getByChar(u.getField(Field.PREFIX_COLOR))
					+ "[" + u.getField(Field.PREFIX_TEXT) + "] "
					+ ChatColor.getByChar(u.getField(Field.PLAYER_NAME_COLOR))
					+ e.getPlayer().getName());
		}
		u.incrementStat(Stat.LOG_INS);
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e)
	{
		if (!UserFile.exists(e.getPlayer().getName()) && Whois.getSettings().getBoolean("whitelist", false))
		{
			e.setQuitMessage(null);
			return;
		}
		else
		{
			User u = Whois.getUser(e.getPlayer());
			
			String message = new String();
			
			message += ChatColor.YELLOW;
			message += e.getPlayer().getName();
			
			if (!u.getField(Field.REAL_NAME).isEmpty())
			{
				message += " (" + u.getField(Field.REAL_NAME) + ")";
			}
			
			message += " has left the game.";
			
			e.setQuitMessage(message);
			
			Whois.unloadUser(e.getPlayer());
		}
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEntityEvent e)
	{
		if (!Whois.getSettings().getBoolean("stick", true))
		{
			return;
		}
		
		if (e.getRightClicked().getType() == EntityType.PLAYER && e.getPlayer().getItemInHand().getType() == Material.STICK)
		{
			Commands.sendWhoisInfo(e.getPlayer(), ((Player) e.getRightClicked()).getName());
		}
	}
	
	@EventHandler
	public void onBlockBreak(BlockBreakEvent e)
	{
		if (!Whois.getSettings().getBoolean("track_stats", true) || e.isCancelled())
		{
			return;
		}
		
		User u = Whois.getUser(e.getPlayer());
		
		if (e.getBlock().getType() == Material.DIAMOND_ORE)
		{
			u.incrementStat(Stat.DIAMONDS);
		}
	}
	
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent e)
	{
		if (!Whois.getSettings().getBoolean("track_stats", true) )
		{
			return;
		}
		
		User u = Whois.getUser(e.getEntity());
		
		u.incrementStat(Stat.DEATH_COUNT);
		
	}
	
	@EventHandler
	public void onPlayerDamage(EntityDamageEvent e)
	{
		if (!Whois.getSettings().getBoolean("track_stats", true) || e.isCancelled() || e.getEntityType() != EntityType.PLAYER)
		{
			return;
		}
		
		User u = Whois.getUser((Player)e.getEntity());
		
		u.incrementStat(Stat.DAMAGE_TAKEN, (long)e.getDamage());
		
	}
	
	@EventHandler
	public void onEntityDeath(EntityDeathEvent e)
	{
		if ( !Whois.getSettings().getBoolean("track_stats", true))
		{
			return;
		}
		User u;
		if (e.getEntity().getLastDamageCause().getCause() == DamageCause.ENTITY_ATTACK)
		{
			if (((EntityDamageByEntityEvent) e.getEntity().getLastDamageCause()).getDamager().getType() == EntityType.PLAYER)
			{
				u = Whois.getUser((Player) ((EntityDamageByEntityEvent) e.getEntity().getLastDamageCause()).getDamager());
			}
			else if (((EntityDamageByEntityEvent) e.getEntity().getLastDamageCause()).getDamager().getType() == EntityType.ARROW
					&& ((Arrow) ((EntityDamageByEntityEvent) e.getEntity().getLastDamageCause()).getDamager()).getShooter().getType() == EntityType.PLAYER)
			{
				u = Whois.getUser((Player) ((Arrow) ((EntityDamageByEntityEvent) e.getEntity().getLastDamageCause()).getDamager()).getShooter());
			}
			else
			{
				return;
			}
		}
		else
		{
			return;
		}
		
		if ( isHostile(e.getEntityType()) )
		{
			u.incrementStat(Stat.KILLS_HOSTILE);
		}
		else if ( isPassive(e.getEntityType()))
		{
			u.incrementStat(Stat.KILLS_PASSIVE);
		}
		else if ( e.getEntityType() == EntityType.PLAYER)
		{
			u.incrementStat(Stat.KILLS_PLAYERS);
		}
		else
		{
			return;
		}
	}
	
	@EventHandler
	public void onEntityDamageByEntity( EntityDamageByEntityEvent e)
	{
		if ( !Whois.getSettings().getBoolean("track_stats", true))
		{
			return;
		}
		User u;
		if (e.getDamager().getType() == EntityType.PLAYER)
		{
			u = Whois.getUser((Player) e.getDamager());
		}
		else if ( e.getDamager().getType() == EntityType.ARROW
				&& ((Arrow) e.getDamager()).getShooter().getType() == EntityType.PLAYER)
		{
			u = Whois.getUser((Player) ((Arrow) e.getDamager()).getShooter());
		}
		else
		{
			return;
		}

		
		u.incrementStat(Stat.DAMAGE_DEALT, (long)e.getDamage());
	}
	
	public void close()
	{
		HandlerList.unregisterAll(this);
	}
	
	public static boolean isHostile(EntityType type)
	{
		

		return (type == EntityType.ZOMBIE
				|| type == EntityType.SKELETON
				|| type == EntityType.SPIDER
				|| type == EntityType.CREEPER
				|| type == EntityType.SLIME
				|| type == EntityType.ENDERMAN
				|| type == EntityType.CAVE_SPIDER
				|| type == EntityType.SILVERFISH
				|| type == EntityType.PIG_ZOMBIE
				|| type == EntityType.GHAST
				|| type == EntityType.MAGMA_CUBE
				|| type == EntityType.BLAZE
				|| type == EntityType.ENDER_DRAGON);
	}

	public static boolean isPassive(EntityType type)
	{

		return (type == EntityType.PIG
				|| type == EntityType.COW
				|| type == EntityType.SHEEP
				|| type == EntityType.CHICKEN
				|| type == EntityType.SQUID
				|| type == EntityType.WOLF
				|| type == EntityType.OCELOT
				|| type == EntityType.MUSHROOM_COW
				|| type == EntityType.VILLAGER);
	}
}
