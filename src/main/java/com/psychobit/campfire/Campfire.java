package com.psychobit.campfire;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Campfire is a plugin to remove spawn camping on PvP enabled servers.
 *
 * It prevents new players from being killed by more experienced players as they
 * try to leave the spawn area. Campfire disables PvP damage, as well as other
 * player based sources of damage until the new player has found their feet.
 *
 * @author psychobit
 *
 */
public class Campfire extends JavaPlugin implements Listener {
    /**
     * Logger for exceptions.
     */
    private static final Logger log = Logger.getLogger("Minecraft");
    /**
     * Player data Contains all the info Campfire needs for a specific player
     */
    private HashMap<String, PlayerData> playerData;
    /**
     * Scheduled repeating task Updates player data on an interval
     */
    private int thread;
    /**
     * Time in seconds a player should be protected by campfire Configurable in
     * the config.yml - defaults to 20 min
     */
    private int duration;
    /**
     * Distance around a player that can't be lava'd or set on fire Configurable
     * in the config.yml - defaults to 5 blocks
     */
    private int bufferDist;
    /**
     * Should a player's data be reset upon death?
     */
    private boolean resetOnDeath;
    /**
     * Cache for the instance of worldguard.
     */
    WorldGuardPlugin wg;

    /**
     * Load player data 
     * Register event listener 
     * Start the update player data task
     */
    @Override
    public void onEnable() {
        // Load the player data
        playerData = new HashMap<String, PlayerData>();
        
        loadData();

        // Define default config values if not set
        if (!getConfig().contains("Duration")) {
            getConfig().set("Duration", 60 * 20);
            getConfig().set("Buffer", 5);
            getConfig().set("ResetOnDeath", true);
            getConfig().set("WorldGuardAreas", true);
            
            saveConfig();
        }

        // Set the duration and buffer as defined in the config
        duration = getConfig().getInt("Duration", 60 * 20);
        bufferDist = getConfig().getInt("Buffer", 5);
        resetOnDeath = getConfig().getBoolean("ResetOnDeath", true);
        
        if (getConfig().getBoolean("WorldGuardAreas", true)) {
            Plugin p = getServer().getPluginManager().getPlugin("WorldGuard");
            
            if (p != null && p instanceof WorldGuardPlugin) {
                wg = (WorldGuardPlugin) p;
            }
        }
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Start the task to update player data
        final Campfire plugin = this;
        
        thread = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                plugin.updatePlayerData();
            }
        }, 20L, 20L); // Update every second
    }

    /**
     * Process commands
     *
     * @param sender Who sent the command
     * @param command Command that was sent
     * @param label
     * @param args Arguments
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check arguments
        if (args.length == 0) {
            sender.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Usage: ");
            sender.sendMessage("/campfire terminate ");
            sender.sendMessage(ChatColor.GRAY + "Removes your protection early");
            sender.sendMessage("/campfire timeleft [player] ");
            sender.sendMessage(ChatColor.GRAY + "Gives the duration left for a player's protection");
            
            return true;
        }

        // Check if the sender was a player
        Player player;
        
        if (sender instanceof Player) {
            player = (Player) sender;
        } else {
            sender.sendMessage("Only in-game players can use this command!");
            
            return true;
        }

        // Parse the action as defined by the first argument
	/*
         * Terminate the player's protection early
         * Allows players to use chests and deal damage before the protection duration is reached
         */
        if (args[0].equalsIgnoreCase("terminate")) {
            // Terminate the player's protection if it has not expired
            String playerName = player.getName();
            PlayerData data = playerData.get(playerName);
            
            if (data.isEnabled()) {
                // Tell them to confirm
                data.setConfirmed();
                
                player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] You will be vulnerable to PvP if you");
                player.sendMessage("terminate your protection! If you understand the risk, ");
                player.sendMessage("type '/campfire confirm' to terminate...");
                
                return true;
            } else {
                // Tell them they are already expired
                player.sendMessage("Your protection has already expired!");
                
                return true;
            }
        } else if (args[0].equalsIgnoreCase("confirm")) {
            // Actually terminates protection after they confirm it
            String playerName = player.getName();
            PlayerData data = playerData.get(playerName);
            
            if (data.isEnabled()) {
                // Check for terminate command
                if (!data.confirmed()) {
                    player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Use /campfire terminate first!");
                    
                    return true;
                }
                
                // Disable their protection
                data.setEnabled(false);

                // Announce it to the server
                getServer().broadcastMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] " + playerName + " Terminated their protection!");
                player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] You are now vulnerable!");
                
                return true;
            } else {
                // Tell them they are already expired
                player.sendMessage("Your protection has already expired!");
                
                return true;
            }
        } else if (args[0].equalsIgnoreCase("timeleft")) {
            /*
             * Allows players to check how much time they have left on their protection,
             * as well as check the time remaining for other players
             */
            
            // Determine who the player they want to check is
            String target = "";
            
            if (args.length == 2) {
                // Search for a target
                Player targetPlayer = getServer().getPlayer(args[1]);
                
                if (targetPlayer != null) {
                    target = targetPlayer.getName();
                }
            } else if (player != null) {
                // Default to the issuer's name
                target = player.getName();
            } else {
                // Must have a target
                sender.sendMessage("You must specify a target!");
                
                return true;
            }

            // Alert if no player was found
            if (target.equals("")) {
                sender.sendMessage(ChatColor.RED + "Player not found!");
                
                return true;
            }

            // Check if they have already expired
            PlayerData data = playerData.get(target);
            
            if (!data.isEnabled()) {
                sender.sendMessage(target + ": protection expired!");
                
                return true;
            }

            // Give them the time left
            int timeLeft = duration - data.getTimeElapsed();
            int min = (timeLeft / 60);
            
            sender.sendMessage(target + ": " + min + " min of protection left!");
            
            return true;

        }

        // Default to usage
        sender.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Usage: ");
        sender.sendMessage("/campfire terminate ");
        sender.sendMessage(ChatColor.GRAY + "Removes your protection early");
        sender.sendMessage("/campfire timeleft [player] ");
        sender.sendMessage(ChatColor.GRAY + "Gives the duration left for a player's protection");
        
        return true;
    }

    /**
     * Save player data and stop the scheduled task
     */
    @Override
    public void onDisable() {
        saveData();
        
        getServer().getScheduler().cancelTask(thread);
    }

    /**
     * Save config and player data to disk
     */
    public void saveData() {
        // Save player data
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getDataFolder() + "/players.dat"));
            oos.writeObject(playerData);
            oos.flush();
            oos.close();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception while saving data", e);
        }
    }

    /**
     * Load data from disk
     */
    @SuppressWarnings("unchecked")
    public void loadData() {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(getDataFolder() + "/players.dat"));
            playerData = (HashMap<String, PlayerData>) ois.readObject();
            ois.close();
        } catch (FileNotFoundException e) { // Ignore it
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception while loading data", e);
        }
    }

    /**
     * Update players' elapsed time
     */
    public void updatePlayerData() {
        // Loop through online players
        Player[] players = getServer().getOnlinePlayers();
        
        for (Player player: players) {
            // Ignore ops and dead guys
            if (player.isOp() || player.isDead()) {
                continue;
            }

            // Check if the player is already on the list
            String playerName = player.getName();
            
            if (!playerData.containsKey(playerName)) {
                // Add them to the list
                PlayerData data = new PlayerData();
                
                playerData.put(playerName, data);
                playerData.get(playerName).setUpdateTime();
                
                player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Starting protection!");
                player.sendMessage("Type '/campfire' for info on PvP Protection");
            } else if (playerData.get(playerName).isEnabled()) {
                // Alias
                PlayerData data = playerData.get(playerName);

                // Check to see if they are in a WorldGuard region if the config says to
                if (wg != null) {
                    ApplicableRegionSet regions = wg.getRegionManager(player.getWorld()).getApplicableRegions(player.getLocation());
                    
                    boolean inNoPvP = !regions.allows(DefaultFlag.PVP);
                    boolean inInvincible = regions.allows(DefaultFlag.INVINCIBILITY);

                    // Send messages on state change and don't update if in a protected zone
                    if (inNoPvP || inInvincible) {
                        data.setUpdateTime();
                        
                        if (!data.inProtectedZone()) {
                            player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Entering protected zone.");
                            player.sendMessage("Protection timer paused!");
                            
                            data.setProtectedZone(true);
                        }
                        
                        continue;
                    } else if (data.inProtectedZone()) {
                        player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Leaving protected zone.");
                        player.sendMessage("Protection timer resumed!");
                        
                        data.setProtectedZone(false);
                    }
                }

                // Increment their time and update their last updated time
                data.update();

                // Check for expiration
                int timeLeft = duration - data.getTimeElapsed();
                
                if (timeLeft <= 0) {
                    getServer().broadcastMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Protection for " + playerName + " Expired!");
                    player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] You are vulnerable!");
                    
                    data.setEnabled(false);
                } else if (timeLeft % 60 == 0) {
                    int min = timeLeft / 60;
                    player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Expires in " + min + " minute" + (min != 1 ? "s" : "") + "!");
                }
            }
        }

        // Save
        saveData();
    }

    /**
     * Prevent PvP damage for protected players
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent e) {
        // Make sure the entity is a player
        Player target;
        
        if (e.getEntity() instanceof Player) {
            target = (Player) e.getEntity();
        } else {
            return;
        }

        // Ignore ops
        if (target.isOp()) {
            return;
        }

        // Ensure player was damaged by an entity
        EntityDamageByEntityEvent e2;
        
        if (e instanceof EntityDamageByEntityEvent) {
            e2 = (EntityDamageByEntityEvent) e;
        } else {
            return;
        }

        // Finally, make sure it was another player or an arrow from a player
        Player attacker;
        
        if (e2.getDamager() instanceof Arrow) {
            // Get the arrow's owner
            Arrow arrow = (Arrow) e2.getDamager();
            
            if (!(arrow.getShooter() instanceof Player)) {
                return;
            }
            
            attacker = (Player) arrow.getShooter();
        } else if (!(e2.getDamager() instanceof Player)) {
            return;
        } else {
            attacker = (Player) e2.getDamager();
        }

        // Ignore ops
        if (attacker.isOp()) {
            return;
        }

        // If the attacker or the victim are under protection, cancel the event
        boolean attackerEnabled = false;
        PlayerData attackerData = playerData.get(attacker.getName());
        
        if (attackerData != null && attackerData.isEnabled()) {
            attackerEnabled = true;
        }
        
        boolean targetEnabled = false;
        PlayerData targetData = playerData.get(target.getName());
        
        if (targetData != null && targetData.isEnabled()) {
            targetEnabled = true;
        }
        
        if (attackerEnabled || targetEnabled) {
            String message;
            
            if (attackerEnabled) {
                message = "You are under protection! No PvP!";
            } else {
                message = "This player is under protection! No PvP!";
            }
            
            attacker.sendMessage(ChatColor.GRAY + "[" + ChatColor.GOLD + "PvP Protection" + ChatColor.GRAY + "] " + ChatColor.RED + message);
            
            e.setCancelled(true);
        }
    }

    /**
     * Reset player data when a player dies Gives them protection back if they
     * die Disable in the config
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent e) {
        // Only reset if config says to
        if (!resetOnDeath) {
            return;
        }
        
        Player target = e.getEntity();

        // Ignore ops
        if (target.isOp()) {
            return;
        }

        // Reset them
        PlayerData data = playerData.get(target.getName());
        
        if (data != null) {
            data.reset();
        }
        
        saveData();

        // Let them know they have been reset
        target.sendMessage(ChatColor.GRAY + "[" + ChatColor.GOLD + "PvP Protection" + ChatColor.GRAY + "] " + "You have died! Resetting Protection!");
    }

    /**
     * Add players to the player data map if they are new to the server
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent e) {
        // Get player object
        Player player = e.getPlayer();
        String playerName = player.getName();

        // Ignore ops
        if (player.isOp()) {
            return;
        }

        // Add them to the list if they are not on it
        if (!playerData.containsKey(playerName)) {
            // Add them to the list
            PlayerData data = new PlayerData();
            playerData.put(playerName, data);
            
            player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] Starting protection!");
            player.sendMessage("Type '/campfire' for info on PvP Protection");
        }

        // Update the player
        playerData.get(playerName).setUpdateTime();
    }

    /**
     * Prevent the use of lava buckets and flint and steel around protected
     * players
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        // Get player object
        Player player = e.getPlayer();
        Material itemInHand = player.getItemInHand().getType();

        // Ignore ops
        if (player.isOp()) {
            return;
        }

        // If they are under protection, check if they are trying to use a prohibited item
        if (playerData.get(player.getName()).isEnabled()) {
            // Check for flint and steel
            if (itemInHand == Material.FLINT_AND_STEEL) {
                player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] " + ChatColor.RED + "You cannot use flint and steel!");
                player.sendMessage("Use '/campfire terminate' to end your protection early!");
                
                e.setCancelled(true);
                
                return;
            }

            // Check for lava buckets
            if (itemInHand == Material.LAVA_BUCKET) {
                player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] " + ChatColor.RED + "You cannot use lava buckets!");
                player.sendMessage("Use '/campfire terminate' to end your protection early!");
                
                e.setCancelled(true);
                
                return;
            }

            // Check for chests
            if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CHEST) {
                player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] " + ChatColor.RED + "You cannot open or break chests!");
                player.sendMessage("Use '/campfire terminate' to end your protection early!");
                
                e.setCancelled(true);
                
                return;
            }

            return; // The code below only applies to non-protected players
        }

        // Check that the player clicked on a block and that they are holding flint and steel or lava 
        if (e.getClickedBlock() != null && 
                (itemInHand == Material.FLINT_AND_STEEL ||
                itemInHand == Material.LAVA_BUCKET)) {
            // Check if they are within the buffer range of protection of a protected player
            Player[] players = getServer().getOnlinePlayers();
            
            for (Player target: players) {
                if (target.equals(player) || 
                        target.isOp() ||
                        target.getWorld() != player.getWorld() ||
                        playerData.get(target.getName()) == null ||
                        !playerData.get(target.getName()).isEnabled()) {
                    continue;
                }
                
                // Check distance
                double dist = e.getClickedBlock().getLocation().distance(target.getLocation());
                
                if (dist <= bufferDist) {
                    player.sendMessage("[" + ChatColor.GOLD + "PvP Protection" + ChatColor.WHITE + "] " + ChatColor.RED + "Player is protected! No burning!");
                    
                    e.setCancelled(true);
                    
                    return;
                }
            }
        }
    }
}
