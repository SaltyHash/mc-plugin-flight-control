package me.saltyhash.flightcontrol;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Task to keep track of total ticks.
 */
class TickCounter extends BukkitRunnable {
    private long ticks;

    TickCounter(final long ticks) {
        this.ticks = ticks;
    }

    long getTicks() {
        return ticks;
    }

    @Override
    public void run() {
        ticks += 1L;
    }
}