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
    private final boolean ignoreCreative;

    public ExhaustionTask(final float exhaustion, final boolean ignoreCreative) {
        this.exhaustion = exhaustion;
        this.ignoreCreative = ignoreCreative;
    }

    public void run() {
        // Iterate over each player
        for (final Player player : Bukkit.getOnlinePlayers()) {
            // Ignore player if not flying, or if in creative mode
            if (!player.isFlying() ||
                    ((player.getGameMode() == GameMode.CREATIVE) && ignoreCreative))
                continue;
            // Increase exhaustion
            player.setExhaustion(player.getExhaustion() + exhaustion);
        }
    }
}