package me.saltyhash.flightcontrol;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * FlightControl plugin.
 */
public class FlightControl extends JavaPlugin {
    private BukkitTask exhaustionTask;
    private BukkitTask hasteTask;
    private FlightControlEventHandler eventHandler;
    private TickCounter tickCounter;
    EconManager econMgr;

    // Command usage strings
    private static final String CMD_FC_USAGE = String.join("\n",
            "FlightControl Commands:",
            "/fc - Show FlightControl commands",
            "/fc reload - Reload config",
            "/fly [player] [on | off] - Toggle / set flight mode"
    );
    private static final String CMD_FLY_USAGE =
            "/fly [player] [on | off] - Toggle / set flight mode";

    long getTicks() {
        /* Returns the number of ticks since the plugin was loaded. */
        return tickCounter.getTicks();
    }

    /**
     * Called when a command is issued.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        // Command: "fc ..."
        if (cmd.getName().equalsIgnoreCase("fc")) {

            // Command: "/fc"
            if (args.length == 0) {
                sender.sendMessage(CMD_FC_USAGE);
                return true;
            }

            // Command: "/fc reload"
            else if (args[0].equalsIgnoreCase("reload")) {
                // Check permissions
                if (!sender.hasPermission("fc.reload")) {
                    sender.sendMessage(ChatColor.DARK_RED +
                            "You cannot reload the FlightControl config");
                    return true;
                }
                reload();
                sender.sendMessage("FlightControl config reloaded");
                getLogger().info(sender.getName() + " reloaded config");
                return true;
            }

            // Unknown command
            else {
                sender.sendMessage(CMD_FC_USAGE);
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
                final Player player = (Player) sender;

                // Toggle flight mode
                // - Flight mode is enabled
                if (player.getAllowFlight()) {
                    // Check permissions
                    if (!player.hasPermission("fc.fly.disable")) {
                        player.sendMessage("Not allowed to disable flight mode");
                        return true;
                    }

                    // Charge player.  Insufficient funds?
                    if (econMgr.charge(
                            player,
                            getConfig().getDouble("flight_mode.cost.disable")
                    ) == 1) {
                        player.sendMessage("You cannot afford to disable flight mode");
                        return true;
                    }

                    setFlightMode(player, false);
                    player.sendMessage("Flight mode " + ChatColor.DARK_RED + "disabled");
                }

                // - Flight mode is disabled
                else {
                    // Check permissions
                    if (!player.hasPermission("fc.fly.enable")) {
                        player.sendMessage("Not allowed to enable flight mode");
                        return true;
                    }

                    // Charge player.  Insufficient funds?
                    if (econMgr.charge(player,
                            getConfig().getDouble("flight_mode.cost.enable")) == 1) {
                        player.sendMessage("You cannot afford to enable flight mode");
                        return true;
                    }

                    setFlightMode(player, true);
                    player.sendMessage("Flight mode " + ChatColor.DARK_GREEN + "enabled");
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
                final Player player = (Player) sender;

                // Check permissions
                if (!player.hasPermission("fc.fly.enable")) {
                    player.sendMessage("Not allowed to enable flight mode");
                    return true;
                }

                // Charge player.  Insufficient funds?
                if (econMgr.charge(player,
                        getConfig().getDouble("flight_mode.cost.enable")) == 1) {
                    player.sendMessage("You cannot afford to enable flight mode");
                    return true;
                }

                // Turn flight mode on
                setFlightMode(player, true);
                player.sendMessage("Flight mode " + ChatColor.DARK_GREEN + "enabled");
                return true;
            }

            // Command: "/fly off"
            else if (args[0].equalsIgnoreCase("off")) {
                // Must be a player
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Must be a player");
                    return true;
                }
                final Player player = (Player) sender;

                // Check permissions
                if (!player.hasPermission("fc.fly.disable")) {
                    player.sendMessage("Not allowed to disable flight mode");
                    return true;
                }

                // Charge player.  Insufficient funds?
                if (econMgr.charge(player,
                        getConfig().getDouble("flight_mode.cost.disable")) == 1) {
                    player.sendMessage("You cannot afford to disable flight mode");
                    return true;
                }

                // Turn flight mode off
                setFlightMode(player, false);
                player.sendMessage("Flight mode " + ChatColor.DARK_RED + "disabled");
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
            final String playerName = args[0];

            // Iterate over each player
            final Player[] players = getServer().getOnlinePlayers().toArray(new Player[0]);
            for (final Player p : players) {
                player = p;
                if (player.getName().equalsIgnoreCase(playerName)) break;
            }

            // Player offline?
            if (player == null) {
                sender.sendMessage("Player " + playerName + " is not online");
                return true;
            }

            // Command: "/fly [player]"
            if (args.length == 1) {
                // Toggle flight mode for player
                // - Flight mode is enabled
                if (player.getAllowFlight()) {
                    // Disable flight mode
                    setFlightMode(player, false);
                    player.sendMessage("Flight mode " + ChatColor.DARK_RED
                            + "disabled" + ChatColor.RESET + " by " + sender.getName());
                    sender.sendMessage("Flight mode " + ChatColor.DARK_RED
                            + "disabled" + ChatColor.RESET + " for " + playerName);
                }
                // - Flight mode is disabled
                else {
                    // Enable flight mode
                    setFlightMode(player, true);
                    player.sendMessage("Flight mode " + ChatColor.DARK_GREEN
                            + "enabled" + ChatColor.RESET + " by " + sender.getName());
                    sender.sendMessage("Flight mode " + ChatColor.DARK_GREEN
                            + "enabled" + ChatColor.RESET + " for " + playerName);
                }

                return true;
            }

            // Command: "/fly [player] on"
            else if (args[1].equalsIgnoreCase("on")) {
                // Turn flight mode on for player
                setFlightMode(player, true);
                player.sendMessage("Flight mode " + ChatColor.DARK_GREEN
                        + "enabled" + ChatColor.RESET + " by " + sender.getName());
                sender.sendMessage("Flight mode " + ChatColor.DARK_GREEN
                        + "enabled" + ChatColor.RESET + " for " + playerName);

                return true;
            }

            // Command: "/fly [player] off"
            else if (args[1].equalsIgnoreCase("off")) {
                // Turn flight mode off for player
                player.setFallDistance(0);
                setFlightMode(player, false);
                player.sendMessage("Flight mode " + ChatColor.DARK_RED
                        + "disabled" + ChatColor.RESET + " by " + sender.getName());
                sender.sendMessage("Flight mode " + ChatColor.DARK_RED
                        + "disabled" + ChatColor.RESET + " for " + playerName);
                return true;
            }

            // Unknown command
            else {
                sender.sendMessage(CMD_FLY_USAGE);
                return true;
            }
        }

        return false;
    }

    /**
     * Called to disable the plugin.
     */
    @Override
    public void onDisable() {
        getLogger().info("Disabled");
    }

    /**
     * Called to enable the plugin.
     */
    @Override
    public void onEnable() {
        final Server server = getServer();
        final Logger logger = getLogger();

        // Warn if server is not configured to allow flight
        if (!server.getAllowFlight()) {
            logger.warning("Server is not configured to allow flight!");
            logger.warning("Set server.properties key:  allow-flight=true");
        }

        // Start tick counter
        tickCounter = new TickCounter(0L);
        tickCounter.runTaskTimer(this, 0L, 1L);

        // Make economy manager
        econMgr = new EconManager(this);

        // Create and register event handler
        eventHandler = new FlightControlEventHandler(this);
        server.getPluginManager().registerEvents(eventHandler, this);

        // Set exhaustion and haste tasks to nonexistent
        exhaustionTask = null;
        hasteTask = null;

        // This method takes care of much of the initialization,
        // and may be called multiple times.
        reload();

        logger.info("Enabled");
    }

    @Override
    public List<String> onTabComplete(
            final CommandSender sender,
            final Command command,
            final String alias,
            final String[] args
    ) {
        /* Request a list of possible tab completions for a command argument. */
        final String cmdName = command.getName();

        // Command: "/fc"
        if (cmdName.equalsIgnoreCase("fc")) {
            if (args.length == 0)
                return Collections.singletonList("reload");
        }

        return null;
    }

    /**
     * Reloads the plugin config and takes appropriate action.
     */
    private void reload() {
        // Reload configuration file
        saveDefaultConfig();
        reloadConfig();
        final FileConfiguration config = getConfig();

        // Reload event handler
        eventHandler.reload();

        // Stop previous tasks
        if (exhaustionTask != null)
            exhaustionTask.cancel();
        if (hasteTask != null)
            hasteTask.cancel();

        // Start exhaustion task, running once every second
        final float exhaustion = (float) config.getDouble(
                "flying.exhaustion.per_second_while_flying");
        if (exhaustion > 0.0) {
            final ExhaustionTask et = new ExhaustionTask(exhaustion, config.getBoolean("flying.ignore_creative"));
            exhaustionTask = et.runTaskTimer(this, 20L, 20L);
        } else
            exhaustionTask = null;

        // Start haste task if necessary, running once every 10 seconds
        final int haste = config.getInt("flying.haste");
        if (haste > 0) {
            final HasteTask ht = new HasteTask(haste, config.getBoolean("flying.ignore_creative"), 200);
            hasteTask = ht.runTaskTimer(this, 0L, 200L);
        } else
            hasteTask = null;

        // Apply flying speed to all players
        final Player[] players = getServer().getOnlinePlayers().toArray(new Player[0]);
        for (final Player player : players) {
            player.setFlySpeed((float) config.getDouble("flying.speed"));
        }
    }

    void setFlightMode(final Player player, final boolean mode) {
        /* Sets the flight mode of the player safely. */
        final FileConfiguration config = getConfig();

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