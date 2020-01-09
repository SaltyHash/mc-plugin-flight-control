package me.saltyhash.flightcontrol;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task to disable a player from flying.
 */
class DisableFlightTask extends BukkitRunnable {
    private final Player player;

    DisableFlightTask(final Player player) {
        this.player = player;
    }

    @Override
    public void run() {
        // Disable flight if necessary
        if ((player != null) && player.isFlying()) {
            final PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, false);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) player.setFlying(false);
        }
    }
}