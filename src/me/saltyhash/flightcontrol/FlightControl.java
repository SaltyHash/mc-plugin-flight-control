package me.saltyhash.flightcontrol;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** FlightControl plugin. */
public class FlightControl extends JavaPlugin {
    private BukkitTask exhaustionTask;
    private BukkitTask hasteTask;
    private FlightControlEventHandler eventHandler;
    private TickCounter tickCounter;
    public  EconManager econMgr;
    
    // Usage strings
    public static String cmd_fc_usage =
        "FlightControl Commands:\n"+
        "/fc - Show FlightControl commands\n"+
        "/fc reload - Reload config\n"+
        "/fly [player] [on | off] - Toggle / set flight mode";
    public static String cmd_fly_usage =
        "/fly [player] [on | off] - Toggle / set flight mode";
    
    public long getTicks() {
        /* Returns the number of ticks since the plugin was loaded. */
        return this.tickCounter.ticks;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        /* Called when a command is issued. */
        // Command: "fc ..."
        if (cmd.getName().equalsIgnoreCase("fc")) {
            
            // Command: "/fc"
            if (args.length == 0) { 
                sender.sendMessage(cmd_fc_usage);
                return true;
            }
            
            // Command: "/fc reload"
            else if (args[0].equalsIgnoreCase("reload")) {
                // Check permissions
                if (!sender.hasPermission("fc.reload")) {
                    sender.sendMessage(ChatColor.DARK_RED+
                        "You cannot reload the FlightControl config");
                    return true;
                }
                this.reload();
                sender.sendMessage("FlightControl config reloaded");
                this.getLogger().info(sender.getName()+" reloaded config");
                return true;
            }
            
            // Unknown command
            else {
                sender.sendMessage(cmd_fc_usage);
                return true;
            }
        }
        
        // Command: "/fly ..."
        else if (cmd.getName().equalsIgnoreCase("fly")) {

            // Command: "/fly"
            if (args.length == 0) {

                // Must be a player
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Must be a player");
                    return true;
                }
                Player player = (Player)sender;

                // Toggle flight mode
                // - Flight mode is enabled
                if (player.getAllowFlight()) {
                    // Check permissions
                    if (!player.hasPermission("fc.fly.disable")) {
                        player.sendMessage("Not allowed to disable flight mode");
                        return true;
                    }
                    // Charge player.  Insufficient funds?
                    if (this.econMgr.charge(player,
                        this.getConfig().getDouble("flight_mode.cost.disable"), false) == 1) {
                        player.sendMessage("You cannot afford to disable flight mode");
                        return true;
                    }
                    this.setFlightMode(player, false);
                    player.sendMessage("Flight mode "+ChatColor.DARK_RED+"disabled");
                }

                // - Flight mode is disabled
                else {
                    // Check permissions
                    if (!player.hasPermission("fc.fly.enable")) {
                        player.sendMessage("Not allowed to enable flight mode");
                        return true;
                    }
                    // Charge player.  Insufficient funds?
                    if (this.econMgr.charge(player,
                        this.getConfig().getDouble("flight_mode.cost.enable"), false) == 1) {
                        player.sendMessage("You cannot afford to enable flight mode");
                        return true;
                    }
                    this.setFlightMode(player, true);
                    player.sendMessage("Flight mode "+ChatColor.DARK_GREEN+"enabled");
                }
                return true;
            }

            // Command: "/fly on"
            else if (args[0].equalsIgnoreCase("on")) {
                // Must be a player
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Must be a player");
                    return true;
                }
                Player player = (Player)sender;
                // Check permissions
                if (!player.hasPermission("fc.fly.enable")) {
                    player.sendMessage("Not allowed to enable flight mode");
                    return true;
                }
                // Charge player.  Insufficient funds?
                if (this.econMgr.charge(player,
                    this.getConfig().getDouble("flight_mode.cost.enable"), false) == 1) {
                    player.sendMessage("You cannot afford to enable flight mode");
                    return true;
                }
                // Turn flight mode on
                this.setFlightMode(player, true);
                player.sendMessage("Flight mode "+ChatColor.DARK_GREEN+"enabled");
                return true;
            }

            // Command: "/fly off"
            else if (args[0].equalsIgnoreCase("off")) {
                // Must be a player
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Must be a player");
                    return true;
                }
                Player player = (Player)sender;
                // Check permissions
                if (!player.hasPermission("fc.fly.disable")) {
                    player.sendMessage("Not allowed to disable flight mode");
                    return true;
                }
                // Charge player.  Insufficient funds?
                if (this.econMgr.charge(player,
                    this.getConfig().getDouble("flight_mode.cost.disable"), false) == 1) {
                    player.sendMessage("You cannot afford to disable flight mode");
                    return true;
                }
                // Turn flight mode off
                this.setFlightMode(player, false);
                player.sendMessage("Flight mode "+ChatColor.DARK_RED+"disabled");
                return true;
            }

            // Command: "/fly [player] [on | off]"
            // Check permissions
            if (!sender.hasPermission("fc.fly.others")) {
                sender.sendMessage("Not allowed to change flight mode of other players");
                return true;
            }
            // Get player specified in command
            Player player = null;
            String player_name = args[0];
            // Iterate over each player
            Player[] players = this.getServer().getOnlinePlayers().toArray(new Player[0]);
            for (Player p : players) {
                player = p;
                if (player.getName().equalsIgnoreCase(player_name)) break;
            }
            // Player offline?
            if (player == null) {
                sender.sendMessage("Player "+player_name+" is not online");
                return true;
            }

            // Command: "/fly [player]"
            if (args.length == 1) {
                // Toggle flight mode for player
                // - Flight mode is enabled
                if (player.getAllowFlight()) {
                    // Disable flight mode
                    this.setFlightMode(player, false);
                    player.sendMessage("Flight mode "+ChatColor.DARK_RED
                        +"disabled"+ChatColor.RESET+" by "+sender.getName());
                    sender.sendMessage("Flight mode "+ChatColor.DARK_RED
                        +"disabled"+ChatColor.RESET+" for "+player_name);
                }
                // - Flight mode is disabled
                else {
                    // Enable flight mode
                    this.setFlightMode(player, true);
                    player.sendMessage("Flight mode "+ChatColor.DARK_GREEN
                        +"enabled"+ChatColor.RESET+" by "+sender.getName());
                    sender.sendMessage("Flight mode "+ChatColor.DARK_GREEN
                        +"enabled"+ChatColor.RESET+" for "+player_name);
                }
                return true;
            }

            // Command: "/fly [player] on"
            else if (args[1].equalsIgnoreCase("on")) {
                // Turn flight mode on for player
                this.setFlightMode(player, true);
                player.sendMessage("Flight mode "+ChatColor.DARK_GREEN
                    +"enabled"+ChatColor.RESET+" by "+sender.getName());
                sender.sendMessage("Flight mode "+ChatColor.DARK_GREEN
                    +"enabled"+ChatColor.RESET+" for "+player_name);
                return true;
            }

            // Command: "/fly [player] off
            else if (args[1].equalsIgnoreCase("off")) {
                // Turn flight mode off for player
                player.setFallDistance(0);
                this.setFlightMode(player, false);
                player.sendMessage("Flight mode "+ChatColor.DARK_RED
                    +"disabled"+ChatColor.RESET+" by "+sender.getName());
                sender.sendMessage("Flight mode "+ChatColor.DARK_RED
                    +"disabled"+ChatColor.RESET+" for "+player_name);
                return true;
            }

            // Unknown command
            else {
                sender.sendMessage(cmd_fly_usage);
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void onDisable() {
        /* Called on plugin disable. */
        this.getLogger().info("Disabled");
    }
    
    @Override
    public void onEnable() {
        /* Called on plugin enable. */
        Server server = this.getServer();
        Logger logger = this.getLogger();
        
        // Warn if server is not configured to allow flight
        if (!server.getAllowFlight()) {
            logger.warning("Server is not configured to allow flight!");
            logger.warning("Set server.properties key:  allow-flight=true");
        }
        
        // Start tick counter
        this.tickCounter = new TickCounter(0L);
        this.tickCounter.runTaskTimer(this, 0L, 1L);
        
        // Make economy manager
        this.econMgr = new EconManager(this);
        
        // Create and register event handler
        this.eventHandler = new FlightControlEventHandler(this);
        server.getPluginManager().registerEvents(eventHandler, this);
        
        // Set exhaustion and haste tasks to nonexistent
        this.exhaustionTask = null;
        this.hasteTask = null;
        
        // This method takes care of much of the initialization,
        // and may be called multiple times.
        this.reload();

        logger.info("Enabled");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
            String alias, String[] args) {
        /* Request a list of possible tab completions for a command argument. */
        String cmd_name = command.getName();
        
        // Command: "/fc"
        if (cmd_name.equalsIgnoreCase("fc")) {
            if (args.length == 0)
                return Collections.singletonList("reload");
        }
        
        return null;
    }

    /* Reloads the plugin config and takes appropriate action. */
    public void reload() {
        // Reload configuration file
        this.saveDefaultConfig();
        this.reloadConfig();
        FileConfiguration config = this.getConfig();
        
        // Reload event handler
        this.eventHandler.reload();
        
        // Stop previous tasks
        if (this.exhaustionTask != null)
            this.exhaustionTask.cancel();
        if (this.hasteTask != null)
            this.hasteTask.cancel();
        
        // Start exhaustion task, running once every second
        float exhaustion = (float)config.getDouble(
            "flying.exhaustion.per_second_while_flying");
        if (exhaustion > 0.0) {
            ExhaustionTask et = new ExhaustionTask(exhaustion, config.getBoolean("flying.ignore_creative"));
            this.exhaustionTask = et.runTaskTimer(this, 20L, 20L);
        }
        else
            this.exhaustionTask = null;
        
        // Start haste task if necessary, running once every 10 seconds
        int haste = config.getInt("flying.haste");
        if (haste > 0) {
            HasteTask ht = new HasteTask(haste, config.getBoolean("flying.ignore_creative"), 200);
            this.hasteTask = ht.runTaskTimer(this, 0L, 200L);
        }
        else
            this.hasteTask = null;
        
        // Apply flying speed to all players
        Player[] players = this.getServer().getOnlinePlayers().toArray(new Player[0]);
        for (Player player : players) {
            player.setFlySpeed((float) config.getDouble("flying.speed"));
        }
    }
    
    public void setFlightMode(Player player, boolean mode) {
        /* Sets the flight mode of the player safely. */
        FileConfiguration config = this.getConfig();
        
        // Enable flight mode?
        if (mode) {
            // Enable flight mode
            player.setAllowFlight(true);
            // Persistent flight is enabled, or player in the air?
            if (config.getBoolean("flying.persist")
                || !player.isOnGround()) {
                // Start player flying
                player.setFlying(true);
            }
        }
        
        // Disable flight mode?
        else {
            player.setAllowFlight(false);
            player.setFallDistance(0);
        }
    }
}