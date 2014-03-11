package com.untamedears.aimabledispensers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

public class AimableDispensersPlugin extends JavaPlugin implements Listener {
	private static final long SAVE_CYCLE = 20 * 60 * 10;
	private static final Material TURRET_INTERACTION_MATERIAL = Material.STICK;
	public static Logger log;
	public static String pluginName = "AimableDispensers";
	public static String version = "0.2";
	
	private Map<World, Set<BlockVector>> turrets;

	public void onEnable() {
		log = this.getLogger();
		getServer().getPluginManager().registerEvents(this, this);

		turrets = new HashMap<World, Set<BlockVector>>();
		loadTurrets();

		log.info(pluginName+" v"+version+" enabled!");
	}

	private void loadTurrets() {
		try {
			File file = new File(getDataFolder(), "turrets.dat");
			if (file.exists()) {
				ObjectInputStream read = new ObjectInputStream(new FileInputStream(file));
				String magic = read.readUTF();
				int version = read.readInt();
				if (!magic.equals("Turrets") || version != 1) {
					System.err.println("Invalid turrets file");
					return;
				}
				int worldCount = read.readInt();
				for (int i = 0; i < worldCount; ++i) {
					String worldName = read.readUTF();
					World world = getServer().getWorld(worldName);
					if (world != null) {
						Set<BlockVector> worldTurrets = new HashSet<BlockVector>();
						int turretCount = read.readInt();
						for (int j = 0; j < turretCount; ++j) {
							long x = read.readLong();
							int y = read.readInt();
							long z = read.readLong();
							worldTurrets.add(new BlockVector(x, y, z));
						}
						turrets.put(world, worldTurrets);
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void saveTurrets() {
		log.info("Saving turret list");
		try {
			File file = new File(getDataFolder(), "turrets.dat");
			File tempFile = File.createTempFile("turrets", ".temp", getDataFolder());
			tempFile.deleteOnExit();
			
			ObjectOutputStream write = new ObjectOutputStream(new FileOutputStream(tempFile));
			write.writeUTF("Turrets");
			write.writeInt(1);
			int worldCount = turrets.size();
			write.writeInt(worldCount);
			for (World world : turrets.keySet()) {
				Set<BlockVector> worldTurrets = turrets.get(world);
				write.writeInt(worldTurrets.size());
				for (BlockVector location : worldTurrets) {
					write.writeLong(location.getBlockX());
					write.writeInt(location.getBlockY());
					write.writeLong(location.getBlockZ());
				}
			}
			write.close();
			tempFile.renameTo(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void periodicSaving()
	{
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override  
			public void run()
			{
				saveTurrets();
			}
		}, SAVE_CYCLE, SAVE_CYCLE);
	}

	public void onDisable() {
		saveTurrets();
		log.info(pluginName+" v"+version+" disabled!");
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

	/**
	 * Called when a block is hit
	 * For now, creates a turret on hitting with a stick
	 */
	@EventHandler
	public void playerInteractionEvent(PlayerInteractEvent e)
	{
		Block clicked = e.getClickedBlock();
		Player player = e.getPlayer();
		
		//if the player left clicked a block
		if (e.getAction().equals(Action.LEFT_CLICK_BLOCK))
		{
			//If the player was holding a item matching the interaction material
			if (player.getItemInHand().getType() == TURRET_INTERACTION_MATERIAL)
			{
				if (clicked.getType().equals(Material.DISPENSER)) {
					if (!turrets.containsKey(clicked.getWorld())) {
						turrets.put(clicked.getWorld(), new HashSet<BlockVector>());
					}
					log.info("Created turret at " + clicked.getLocation().toVector().toBlockVector().toString());
					turrets.get(clicked.getWorld()).add(clicked.getLocation().toVector().toBlockVector());
				}
			}
		}
	}

	/**
	 * Called when a block is broken
	 * If the block that is destroyed is a turret, will be removed from turret list.
	 */
	@EventHandler
	public void blockBreakEvent(BlockBreakEvent e)
	{
		Block block = e.getBlock();
		//Is the block part of a factory?
		if (turrets.containsKey(block.getWorld())) {
			BlockVector location = block.getLocation().toVector().toBlockVector();
			if (turrets.get(block.getWorld()).contains(location)) {
				log.info("Broke turret at " + location.toString());
				turrets.get(block.getWorld()).remove(location);
			}
		}
	}

	/*
	 * Called on firing, adjusts trajectory of controlled turrets
	 */
	@EventHandler
	public void onBlockDispense(BlockDispenseEvent ev) {
		World world = ev.getBlock().getWorld();
		BlockVector blockLocation = ev.getBlock().getLocation().toVector().toBlockVector();
		
		if (turrets.containsKey(world) && turrets.get(world).contains(blockLocation) && ev.getVelocity() != null) {
			Player controlPlayer = findPlayer(world, blockLocation.add(new Vector(0, 1, 0)).toBlockVector());
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
	}
}
