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
    private boolean flyingAllowAscend;
    private boolean flyingAllowDescend;
    private boolean flyingAllowSprint;
    private double flyingBalanceMin;
    private double flyingCostOnStart;
    private double flyingCostOnStop;
    private double flyingCostPerTick;
    private float flyingExhaustionOnFly;
    private int flyingHaste;
    private boolean flyingIgnoreCreative;
    private boolean flyingPersist;
    private int flyingTicks;
    private int flyingTimeStart;
    private int flyingTimeStop;
    private boolean flyingWeatherClear;
    private boolean flyingWeatherRain;
    private boolean flyingWeatherThunder;
    // Map for keeping track of tasks scheduled to disable flight
    private final Map<UUID, BukkitTask> disableFlightTasks = new HashMap<UUID, BukkitTask>();

    public FlightControlEventHandler(final FlightControl fc) {
        /* Event handler for FlightControl. */
        this.fc = fc;
        reload();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        /* Called on entity taking damage. */
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (!player.isFlying()) return;

        // Ignore if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && flyingIgnoreCreative) return;

        final FileConfiguration config = fc.getConfig();

        // Disable flying on damage or while starving?
        if ((config.getBoolean("flying.disable_on_damage")
                || ((event.getCause() == DamageCause.STARVATION))
                && config.getBoolean("flying.disable_while_starving"))) {
            // Fire PlayerToggleFlightEvent as if player is stopping flying
            final PlayerToggleFlightEvent toggleFlightEvent = new PlayerToggleFlightEvent(player, false);
            Bukkit.getPluginManager().callEvent(toggleFlightEvent);
            if (!toggleFlightEvent.isCancelled()) player.setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {
        /* Called when a player's game mode changes. */
        final Player player = event.getPlayer();

        // Switching to creative mode?
        if (event.getNewGameMode() == GameMode.CREATIVE) {
            // Ignore creative mode?
            if (flyingIgnoreCreative) {
                // Charge player for time flying and stop the flying timer
                econMgr.chargeForFlyingFromTimer(player, true);
                econMgr.stopFlyingTimer(player);

                // Remove haste, if caused by this plugin
                if (flyingHaste > 0)
                    player.removePotionEffect(PotionEffectType.FAST_DIGGING);

                // Cancel and remove any disable flight task for player
                final BukkitTask task = disableFlightTasks.remove(player.getUniqueId());
                if (task != null) task.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        /* Called on player join. */
        final Player player = event.getPlayer();

        // Set flight speed
        player.setFlySpeed((float) config.getDouble("flying.speed"));

        // Stop flying timer
        econMgr.stopFlyingTimer(player);

        // Ignore the following if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && flyingIgnoreCreative) return;

        // Check if flight mode should be enabled on join
        if (
            // Enable flight mode on join?
                config.getBoolean("flight_mode.enable_on_join")
                        // Player is allowed to fly?
                        && player.hasPermission("fc.fly.allow")
                        // Charge player for enabling flight mode.  Insufficient funds?
                        && (econMgr.charge(player,
                        config.getDouble("flight_mode.cost.enable"), false) != 1)
        ) {
            // Enable flight mode
            fc.setFlightMode(player, true);

            // If the player is in the air, try to set them flying
            if (!player.isOnGround()) {
                // Fire PlayerToggleFlightEvent as if player is starting to fly
                final PlayerToggleFlightEvent toggleFlightEvent = new PlayerToggleFlightEvent(player, true);
                Bukkit.getPluginManager().callEvent(toggleFlightEvent);
                player.setFlying(!toggleFlightEvent.isCancelled());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        /* Called when player moves. */
        final Player player = event.getPlayer();
        if (!player.isFlying()) return;

        // Ignore if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && flyingIgnoreCreative) return;

        final double l_fm_y = event.getFrom().getY();
        // TODO: Clone?
//        final Location l_to = event.getTo().clone();
        final Location l_to = event.getTo();
        if (l_to == null) return;
        final double l_to_y = l_to.getY();

        // Ascending
        if (l_to_y > l_fm_y) {
            // Player not allowed to ascend?
            if (!flyingAllowAscend) {
                l_to.setY(l_fm_y);
                event.setTo(l_to);
            }
        }

        // Descending
        else if (l_to_y < l_fm_y) {
            // Player not allowed to descend?
            if (!flyingAllowDescend) {
                l_to.setY(l_fm_y);
                event.setTo(l_to);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        /* Called when player leaves the server. */
        // Call PlayerToggleFlightEvent locally as if player had stopped flying
        onPlayerToggleFlight(new PlayerToggleFlightEvent(event.getPlayer(), false));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleFlight(final PlayerToggleFlightEvent event) {
        /* Called on player flight state change. */
        final Player player = event.getPlayer();

        // Ignore if in creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && flyingIgnoreCreative) return;

        // Starting to fly
        if (event.isFlying()) {

            final long worldTime = player.getWorld().getTime();
            // Clear: 0    Rain: 1    Thunder: 2
            int worldWeather = 0;
            if (player.getWorld().isThundering()) worldWeather = 2;
            else if (player.getWorld().hasStorm()) worldWeather = 1;

            // Check various parameters which may prevent player from flying
            if (
                // Player is not allowed to fly?
                    !player.hasPermission("fc.fly.allow")
                            // Is player starving?
                            || ((player.getFoodLevel() <= 0)
                            && config.getBoolean("flying.disable_while_starving"))
                            // Is player allowed to fly at this time of day?
                            || (worldTime < flyingTimeStart)
                            || (worldTime > flyingTimeStop)
                            // Is player allowed to fly in this weather?
                            || ((worldWeather == 0) && !flyingWeatherClear)
                            || ((worldWeather == 1) && !flyingWeatherRain)
                            || ((worldWeather == 2) && !flyingWeatherThunder)
                            // Player does not have minimum required balance?
                            || econMgr.insufficientFunds(player, flyingBalanceMin)
                            // Player cannot afford cost of flying during flight?
                            || econMgr.insufficientFunds(player, flyingCostPerTick)
                            // Charge player for flying.  Insufficient funds?
                            || (econMgr.charge(player, flyingCostOnStart, false) == 1)
            ) {
                // Prevent player from starting to fly
                event.setCancelled(true);
                return;
            }

            // Alright, at this point, the player is allowed to fly

            // Increment player exhaustion
            player.setExhaustion(player.getExhaustion() + flyingExhaustionOnFly);

            // Increase mining speed to account for reduced mining speed
            if (flyingHaste > 0)
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.FAST_DIGGING, 200, flyingHaste - 1, true));

            // Check if sprinting is allowed
            if (!flyingAllowSprint && player.isSprinting())
                player.setSprinting(false);

            // Schedule flight to be disabled after certain number of ticks?
            if ((flyingTicks > 0) && !flyingPersist) {
                // Create new task to disable flight after given ticks
                final BukkitTask task = new DisableFlightTask(player).runTaskLater(fc, flyingTicks);
                // Add task to dictionary used to keep track of them
                // so that it may be disabled in the future if necessary.
                disableFlightTasks.put(player.getUniqueId(), task);
            }

            // Start the flying timer
            econMgr.startFlyingTimer(player);
        }

        // Stopping flight
        else {
            // Check parameters which may prevent player from stopping flying
            if (
                // Persistent flight is enabled?
                //(player.getAllowFlight() && this.flying_persist)
                    flyingPersist
                            // Charge player for stopping flight.  Insufficient funds?
                            || (econMgr.charge(player, flyingCostOnStop, false) == 1)
            ) {
                // Prevent player from stopping flying
                event.setCancelled(true);
                // If they are on the ground, move them off of it
                if (player.isOnGround()) {
                    final Location l = player.getLocation();
                    l.setY(java.lang.Math.round(l.getY()));
                    player.teleport(l);
                }
                return;
            }

            // Alright, at this point, the player is allowed to stop flying

            // Charge player for time flying and stop the flying timer
            econMgr.chargeForFlyingFromTimer(player, true);
            econMgr.stopFlyingTimer(player);

            // Remove haste, if caused by this plugin
            if (flyingHaste > 0)
                player.removePotionEffect(PotionEffectType.FAST_DIGGING);

            // Cancel and remove any disable flight task for player
            final BukkitTask task = disableFlightTasks.remove(player.getUniqueId());
            if (task != null) task.cancel();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleSprint(final PlayerToggleSprintEvent event) {
        /* Called when player toggles sprinting state. */
        final Player player = event.getPlayer();
        // Ignore creative mode
        if ((player.getGameMode() == GameMode.CREATIVE) && flyingIgnoreCreative) return;
        // Prevent player from sprinting while flying if necessary
        if (!flyingAllowSprint && event.getPlayer().isFlying() && event.isSprinting())
            event.setCancelled(true);
    }

    public void reload() {
        /* Reloads the event handler from the FlightControl config. */
        config = fc.getConfig();
        econMgr = fc.econMgr;

        // Get some config values to speed stuff up
        flyingAllowAscend =
                config.getBoolean("flying.allow_ascend");
        flyingAllowDescend =
                config.getBoolean("flying.allow_descend");
        flyingAllowSprint =
                config.getBoolean("flying.allow_sprint");
        flyingBalanceMin =
                config.getDouble("flying.balance_min");
        flyingCostOnStart =
                config.getDouble("flying.cost.on_start");
        flyingCostOnStop =
                config.getDouble("flying.cost.on_stop");
        flyingCostPerTick =
                config.getDouble("flying.cost.per_tick");
        flyingExhaustionOnFly =
                (float) config.getDouble("flying.exhaustion.on_fly");
        flyingHaste =
                config.getInt("flying.haste");
        flyingIgnoreCreative =
                config.getBoolean("flying.ignore_creative");
        flyingPersist =
                config.getBoolean("flying.persist");
        flyingTicks =
                config.getInt("flying.ticks");
        flyingTimeStart =
                config.getInt("flying.time.start");
        flyingTimeStop =
                config.getInt("flying.time.stop");
        flyingWeatherClear =
                config.getBoolean("flying.weather.clear");
        flyingWeatherRain =
                config.getBoolean("flying.weather.rain");
        flyingWeatherThunder =
                config.getBoolean("flying.weather.thunder");

        // Cancel all disable flight tasks if told to never disable
        if ((flyingTicks == 0) || flyingPersist) {
            for (final BukkitTask task : disableFlightTasks.values())
                task.cancel();
            disableFlightTasks.clear();
        }

        // Start all players flying if told to persist
        if (flyingPersist) {
            for (final Player player : fc.getServer().getOnlinePlayers()) {
                if (player.getAllowFlight()) player.setFlying(true);
            }
        }
    }
}