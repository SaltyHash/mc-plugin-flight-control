package info.saltyhash.flightcontrol;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/** Task to give all flying players haste if necessary. */
class HasteTask extends BukkitRunnable {
    private final int     haste_level;
    private final boolean ignore_creative;
    private final int     ticks;
    
    public HasteTask(int haste_level, boolean ignore_creative, int ticks) {
        this.haste_level     = haste_level;
        this.ignore_creative = ignore_creative;
        this.ticks           = ticks;
    }
    
    public void run() {
        if ((this.haste_level <= 0) || (this.ticks <= 0)) return;
        // Iterate over each player
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Ignore player if not flying, or if in creative mode
            if (!player.isFlying() ||
                    ((player.getGameMode() == GameMode.CREATIVE) && this.ignore_creative))
                continue;
            // Give player haste
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.FAST_DIGGING,
                            this.ticks, this.haste_level - 1, true),
                    true);
        }
    }
}