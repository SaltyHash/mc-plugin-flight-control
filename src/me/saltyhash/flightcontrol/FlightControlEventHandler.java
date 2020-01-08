package me.saltyhash.flightcontrol;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event handler for FlightControl.
 */
class FlightControlEventHandler implements Listener {
    private final FlightControl fc;
    private FileConfiguration config;
    private EconManager econMgr;
    // Just for speeding up some things
    private boolean flying_allow_ascend;
    private boolean flying_allow_descend;
    private boolean flying_allow_sprint;
    private double flying_balance_min;
    private double flying_cost_on_start;
    private double flying_cost_on_stop;
    private double flying_cost_per_tick;
    private float flying_exhaustion_on_fly;
    private int flying_haste;
    private boolean flying_ignore_creative;
    private boolean flying_persist;
    private int flying_ticks;
    private int flying_time_start;
    private int flying_time_stop;
    private boolean flying_weather_clear;
    private boolean flying_weather_rain;
    private boolean flying_weather_thunder;
    // Map for keeping track of tasks scheduled to disable flight
    private Map<UUID, BukkitTask> disable_flight_tasks;

    public FlightControlEventHandler(FlightControl fc) {
        /* Event handler for FlightControl. */
        this.fc = fc;
        this.disable_flight_tasks = new HashMap<UUID, BukkitTask>();
        this.reload();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        /* Called on entity taking damage. */
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!player.isFlying()) return;

        // Ignore if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && this.flying_ignore_creative) return;

        FileConfiguration config = this.fc.getConfig();

        // Disable flying on damage or while starving?
        if ((config.getBoolean("flying.disable_on_damage")
                || ((event.getCause() == DamageCause.STARVATION))
                && config.getBoolean("flying.disable_while_starving"))) {
            // Fire PlayerToggleFlightEvent as if player is stopping flying
            PlayerToggleFlightEvent ptfe = new PlayerToggleFlightEvent(player, false);
            Bukkit.getPluginManager().callEvent(ptfe);
            if (!ptfe.isCancelled()) player.setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        /* Called when a player's game mode changes. */
        Player player = event.getPlayer();

        // Switching to creative mode?
        if (event.getNewGameMode() == GameMode.CREATIVE) {
            // Ignore creative mode?
            if (this.flying_ignore_creative) {
                // Charge player for time flying and stop the flying timer
                this.econMgr.chargeForFlyingFromTimer(player, true);
                this.econMgr.stopFlyingTimer(player);

                // Remove haste, if caused by this plugin
                if (this.flying_haste > 0)
                    player.removePotionEffect(PotionEffectType.FAST_DIGGING);

                // Cancel and remove any disable flight task for player
                BukkitTask task = this.disable_flight_tasks.remove(player.getUniqueId());
                if (task != null) task.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        /* Called on player join. */
        Player player = event.getPlayer();

        // Set flight speed
        player.setFlySpeed((float) this.config.getDouble("flying.speed"));

        // Stop flying timer
        this.econMgr.stopFlyingTimer(player);

        // Ignore the following if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && this.flying_ignore_creative) return;

        // Check if flight mode should be enabled on join
        if (
            // Enable flight mode on join?
                this.config.getBoolean("flight_mode.enable_on_join")
                        // Player is allowed to fly?
                        && player.hasPermission("fc.fly.allow")
                        // Charge player for enabling flight mode.  Insufficient funds?
                        && (this.econMgr.charge(player,
                        this.config.getDouble("flight_mode.cost.enable"), false) != 1)
        ) {
            // Enable flight mode
            this.fc.setFlightMode(player, true);

            // If the player is in the air, try to set them flying
            if (!player.isOnGround()) {
                // Fire PlayerToggleFlightEvent as if player is starting to fly
                PlayerToggleFlightEvent ptfe = new PlayerToggleFlightEvent(player, true);
                Bukkit.getPluginManager().callEvent(ptfe);
                player.setFlying(!ptfe.isCancelled());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        /* Called when player moves. */
        Player player = event.getPlayer();
        if (!player.isFlying()) return;

        // Ignore if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && this.flying_ignore_creative) return;

        double l_fm_y = event.getFrom().getY();
        Location l_to = event.getTo().clone();
        double l_to_y = l_to.getY();

        // Ascending
        if (l_to_y > l_fm_y) {
            // Player not allowed to ascend?
            if (!this.flying_allow_ascend) {
                l_to.setY(l_fm_y);
                event.setTo(l_to);
            }
        }

        // Descending
        else if (l_to_y < l_fm_y) {
            // Player not allowed to descend?
            if (!this.flying_allow_descend) {
                l_to.setY(l_fm_y);
                event.setTo(l_to);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        /* Called when player leaves the server. */
        // Call PlayerToggleFlightEvent locally as if player had stopped flying
        this.onPlayerToggleFlight(new PlayerToggleFlightEvent(event.getPlayer(), false));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        /* Called on player flight state change. */
        Player player = event.getPlayer();

        // Ignore if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && this.flying_ignore_creative) return;

        // Starting to fly
        if (event.isFlying()) {

            long world_time = player.getWorld().getTime();
            // Clear: 0    Rain: 1    Thunder: 2
            int world_weather = 0;
            if (player.getWorld().isThundering()) world_weather = 2;
            else if (player.getWorld().hasStorm()) world_weather = 1;

            // Check various parameters which may prevent player from flying
            if (
                // Player is not allowed to fly?
                    !player.hasPermission("fc.fly.allow")
                            // Is player starving?
                            || ((player.getFoodLevel() <= 0)
                            && this.config.getBoolean("flying.disable_while_starving"))
                            // Is player allowed to fly at this time of day?
                            || (world_time < this.flying_time_start)
                            || (world_time > this.flying_time_stop)
                            // Is player allowed to fly in this weather?
                            || ((world_weather == 0) && !this.flying_weather_clear)
                            || ((world_weather == 1) && !this.flying_weather_rain)
                            || ((world_weather == 2) && !this.flying_weather_thunder)
                            // Player does not have minimum required balance?
                            || this.econMgr.insufficientFunds(player, this.flying_balance_min)
                            // Player cannot afford cost of flying during flight?
                            || this.econMgr.insufficientFunds(player, this.flying_cost_per_tick)
                            // Charge player for flying.  Insufficient funds?
                            || (this.econMgr.charge(player, this.flying_cost_on_start, false) == 1)
            ) {
                // Prevent player from starting to fly
                event.setCancelled(true);
                return;
            }

            // Alright, at this point, the player is allowed to fly

            // Increment player exhaustion
            player.setExhaustion(player.getExhaustion() + this.flying_exhaustion_on_fly);

            // Increase mining speed to account for reduced mining speed
            if (this.flying_haste > 0)
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.FAST_DIGGING, 200, this.flying_haste - 1, true));

            // Check if sprinting is allowed
            if (!this.flying_allow_sprint && player.isSprinting())
                player.setSprinting(false);

            // Schedule flight to be disabled after certain number of ticks?
            if ((this.flying_ticks > 0) && !this.flying_persist) {
                // Create new task to disable flight after given ticks
                BukkitTask task = new DisableFlightTask(player)
                        .runTaskLater(this.fc, (long) this.flying_ticks);
                // Add task to dictionary used to keep track of them
                // so that it may be disabled in the future if necessary.
                this.disable_flight_tasks.put(player.getUniqueId(), task);
            }

            // Start the flying timer
            this.econMgr.startFlyingTimer(player);
        }

        // Stopping flight
        else {
            // Check parameters which may prevent player from stopping flying
            if (
                // Persistent flight is enabled?
                //(player.getAllowFlight() && this.flying_persist)
                    this.flying_persist
                            // Charge player for stopping flight.  Insufficient funds?
                            || (this.econMgr.charge(player, this.flying_cost_on_stop, false) == 1)
            ) {
                // Prevent player from stopping flying
                event.setCancelled(true);
                // If they are on the ground, move them off of it
                if (player.isOnGround()) {
                    Location l = player.getLocation();
                    l.setY(java.lang.Math.round(l.getY()));
                    player.teleport(l);
                }
                return;
            }

            // Alright, at this point, the player is allowed to stop flying

            // Charge player for time flying and stop the flying timer
            this.econMgr.chargeForFlyingFromTimer(player, true);
            this.econMgr.stopFlyingTimer(player);

            // Remove haste, if caused by this plugin
            if (this.flying_haste > 0)
                player.removePotionEffect(PotionEffectType.FAST_DIGGING);

            // Cancel and remove any disable flight task for player
            BukkitTask task = this.disable_flight_tasks.remove(player.getUniqueId());
            if (task != null) task.cancel();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        /* Called when player toggles sprinting state. */
        Player player = event.getPlayer();
        // Ignore creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && this.flying_ignore_creative) return;
        // Prevent player from sprinting while flying if necessary
        if (!this.flying_allow_sprint && event.getPlayer().isFlying() && event.isSprinting())
            event.setCancelled(true);
    }

    public void reload() {
        /* Reloads the event handler from the FlightControl config. */
        this.config = this.fc.getConfig();
        this.econMgr = this.fc.econMgr;

        // Get some config values to speed stuff up
        this.flying_allow_ascend =
                this.config.getBoolean("flying.allow_ascend");
        this.flying_allow_descend =
                this.config.getBoolean("flying.allow_descend");
        this.flying_allow_sprint =
                this.config.getBoolean("flying.allow_sprint");
        this.flying_balance_min =
                this.config.getDouble("flying.balance_min");
        this.flying_cost_on_start =
                this.config.getDouble("flying.cost.on_start");
        this.flying_cost_on_stop =
                this.config.getDouble("flying.cost.on_stop");
        this.flying_cost_per_tick =
                this.config.getDouble("flying.cost.per_tick");
        this.flying_exhaustion_on_fly =
                (float) this.config.getDouble("flying.exhaustion.on_fly");
        this.flying_haste =
                this.config.getInt("flying.haste");
        this.flying_ignore_creative =
                this.config.getBoolean("flying.ignore_creative");
        this.flying_persist =
                this.config.getBoolean("flying.persist");
        this.flying_ticks =
                this.config.getInt("flying.ticks");
        this.flying_time_start =
                this.config.getInt("flying.time.start");
        this.flying_time_stop =
                this.config.getInt("flying.time.stop");
        this.flying_weather_clear =
                this.config.getBoolean("flying.weather.clear");
        this.flying_weather_rain =
                this.config.getBoolean("flying.weather.rain");
        this.flying_weather_thunder =
                this.config.getBoolean("flying.weather.thunder");

        // Cancel all disable flight tasks if told to never disable
        if ((this.flying_ticks == 0) || this.flying_persist) {
            for (BukkitTask task : this.disable_flight_tasks.values())
                task.cancel();
            this.disable_flight_tasks.clear();
        }

        // Start all players flying if told to persist
        if (this.flying_persist) {
            for (Player player : this.fc.getServer().getOnlinePlayers()) {
                if (player.getAllowFlight()) player.setFlying(true);
            }
        }
    }
}