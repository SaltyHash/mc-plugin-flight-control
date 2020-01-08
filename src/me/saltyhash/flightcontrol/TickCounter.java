package me.saltyhash.flightcontrol;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task to keep track of total ticks.
 */
class TickCounter extends BukkitRunnable {
    public long ticks;

    public TickCounter(long ticks) {
        this.ticks = ticks;
    }

    public void run() {
        this.ticks += 1L;
    }
}