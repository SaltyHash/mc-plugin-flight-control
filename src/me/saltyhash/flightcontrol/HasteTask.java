package me.saltyhash.flightcontrol;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task to give all flying players haste if necessary.
 */
class HasteTask extends BukkitRunnable {
    private final int hasteLevel;
    private final boolean ignoreCreative;
    private final int ticks;

    HasteTask(final int hasteLevel, final boolean ignoreCreative, final int ticks) {
        this.hasteLevel = hasteLevel;
        this.ignoreCreative = ignoreCreative;
        this.ticks = ticks;
    }

    public void run() {
        if (hasteLevel <= 0 || ticks <= 0) return;

        // Iterate over each player
        for (final Player player : Bukkit.getOnlinePlayers()) {
            // Ignore player if not flying, or if in creative mode
            if (!player.isFlying() ||
                    ((player.getGameMode() == GameMode.CREATIVE) && ignoreCreative))
                continue;

            // Give player haste
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.FAST_DIGGING,
                            ticks, hasteLevel - 1, true),
                    true);
        }
    }
}