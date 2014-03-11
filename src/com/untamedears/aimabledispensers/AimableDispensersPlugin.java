package com.untamedears.aimabledispensers;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

public class AimableDispensersPlugin extends JavaPlugin implements Listener {
	public static Logger log;
	public static String pluginName = "AimableDispensers";
	public static String version = "0.1";

	public void onEnable() {
		log = this.getLogger();
		getServer().getPluginManager().registerEvents(this, this);

		log.info(pluginName+" v"+version+" enabled!");
	}

	public void onDisable() {
		log.info(pluginName+" v"+version+" disabled!");
	}

	@EventHandler
	public void onBlockDispense(BlockDispenseEvent ev) {
		Player controlPlayer = findPlayer(ev.getBlock().getWorld(), ev.getBlock().getLocation().toVector().add(new Vector(0, 1, 0)).toBlockVector());
		if (controlPlayer != null) {
			double playerPitch = ((controlPlayer.getLocation().getPitch()) * Math.PI) / 180;
			double playerYaw  = ((controlPlayer.getLocation().getYaw())  * Math.PI) / 180;
			
			double x = ev.getVelocity().getX();
			double y = ev.getVelocity().getY();
			double z = ev.getVelocity().getZ();
			double velocity = Math.sqrt(x * x + y * y + z * z);
			
			Vector normalisedFiring;
			
			if (Math.abs(x) > Math.abs(z) && Math.abs(x) > Math.abs(y)) {
				// X dominant
				normalisedFiring = new Vector(Math.signum(x), 0, 0);
			} else if (Math.abs(z) > Math.abs(y) && Math.abs(z) > Math.abs(x)) {
				// Z dominant
				normalisedFiring = new Vector(0, 0, Math.signum(z));
			} else {
				// vertical dominant - can't control
				return;
			}
			
			double normalPitch = 0 - Math.atan2(normalisedFiring.getY(), 1);
			double normalYaw = Math.atan2(-normalisedFiring.getX(), normalisedFiring.getZ());
			
			double offsetYaw = normalYaw - playerYaw;
			double offsetPitch = normalPitch - playerPitch + (Math.PI / 6);

			if (offsetYaw < Math.PI) {
				offsetYaw += Math.PI * 2;
			}
			if (offsetYaw > Math.PI) {
				offsetYaw -= Math.PI * 2;
			}
			
			if (Math.abs(offsetYaw) < Math.PI * (40 / 180.0) && Math.abs(offsetPitch) < Math.PI * (40 / 180.0)) {
				x = 0 - Math.sin(normalYaw - offsetYaw) * velocity;
				y = Math.sin(normalPitch + offsetPitch) * velocity;
				z = Math.cos(normalYaw - offsetYaw) * velocity;				
				
				ev.setVelocity(new Vector(x, y, z));
			}
		}
	}
	
	private Player findPlayer(World world, BlockVector standing) {
		Player[] onlinePlayers = Bukkit.getOnlinePlayers();
		Player playerFound = null;
		int foundPlayers = 0;

		if (onlinePlayers.length > 0) {
			for (Player op : onlinePlayers) {
				if (op != null) {
					Location playerLocation = op.getLocation();
					if (playerLocation.getWorld().equals(world)) {
						BlockVector playerStanding = playerLocation.toVector().toBlockVector();
						if (playerStanding.equals(standing)) {
							return op;
						}
					}
				}
			}
		}
		return playerFound;
	}
}
