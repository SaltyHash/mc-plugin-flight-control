package me.saltyhash.flightcontrol;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task to increase the exhaustion of each flying player each run.
 */
class ExhaustionTask extends BukkitRunnable {
    private final float exhaustion;
    private final boolean ignore_creative;

    public ExhaustionTask(float exhaustion, boolean ignore_creative) {
        this.exhaustion = exhaustion;
        this.ignore_creative = ignore_creative;
    }

    public void run() {
        // Iterate over each player
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Ignore player if not flying, or if in creative mode
            if (!player.isFlying() ||
                    ((player.getGameMode() == GameMode.CREATIVE) && this.ignore_creative))
                continue;
            // Increase exhaustion
            player.setExhaustion(player.getExhaustion() + this.exhaustion);
        }
    }
}