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

    public DisableFlightTask(Player player) {
        this.player = player;
    }

    public void run() {
        // Disable flight if necessary
        if ((this.player != null) && this.player.isFlying()) {
            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, false);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) this.player.setFlying(false);
        }
    }
}