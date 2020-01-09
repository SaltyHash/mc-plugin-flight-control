package me.saltyhash.flightcontrol;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task to keep track of total ticks.
 */
class TickCounter extends BukkitRunnable {
    private long ticks;

    public TickCounter(final long ticks) {
        this.ticks = ticks;
    }

    public long getTicks() {
        return ticks;
    }

    public void run() {
        ticks += 1L;
    }
}