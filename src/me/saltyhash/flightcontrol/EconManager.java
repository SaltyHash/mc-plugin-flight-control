package me.saltyhash.flightcontrol;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Economy manager.
 */
class EconManager {
    private final FlightControl fc;
    private Economy econ;

    public EconManager(final FlightControl fc) {
        this.fc = fc;

        // Set up economy integration
        // Vault not found
        if (fc.getServer().getPluginManager().getPlugin("Vault") == null)
            fc.getLogger().warning(
                    "Economy support disabled; dependency \"Vault\" not found");
            // Vault found
        else {
            final RegisteredServiceProvider<Economy> rsp =
                    fc.getServer().getServicesManager().getRegistration(Economy.class);

            // No economy plugin found
            if (rsp == null) {
                fc.getLogger().warning(
                        "Economy integration disabled; economy plugin not found");
            }
            // Economy plugin found
            else {
                econ = rsp.getProvider();
            }
        }
    }

    /**
     * Returns the object for key in given player (null if DNE).
     */
    @SuppressWarnings("SameParameterValue")
    private Object getMetadata(final Player player, final String key) {
        for (final MetadataValue value : player.getMetadata(key)) {
            final Plugin owningPlugin = value.getOwningPlugin();
            if (owningPlugin == null) continue;

            if (owningPlugin.getDescription().getName().equals(fc.getDescription().getName()))
                return value.value();
        }

        return null;
    }

    /**
     * Sets the object for key in given player.
     */
    @SuppressWarnings("SameParameterValue")
    private void setMetadata(final Player player, final String key, final Object object) {
        player.setMetadata(key, new FixedMetadataValue(fc, object));
    }

    /**
     * Charges player the specified amount (can be negative).
     * If force, then will bankrupt player if insufficient funds.
     * <p>
     * Returns:
     * 0: Success
     * 1: Insufficient funds
     * 2: Failed to create account
     * 3: No economy support
     */
    int charge(final Player player, double amount, final boolean force) {
        if (!isEnabled()) return 3;

        // Make sure player has an account
        if (!econ.hasAccount(player))
            if (!econ.createPlayerAccount(player)) return 2;

        // Withdraw from account
        if (amount > 0.0) {
            final EconomyResponse result = econ.withdrawPlayer(player, amount);
            // Insufficient funds?
            if (!result.transactionSuccess()) {
                // Bankrupt the player?
                if (force) return charge(player, getBalance(player), false);
                    // Return insufficient funds
                else return 1;
            }
        }
        // Deposit to account
        else if (amount < 0.0) {
            amount = -amount;
            econ.depositPlayer(player, amount);
        }
        return 0;
    }

    /**
     * Uses the config to appropriately charge the player.
     * If force, then will bankrupt player if insufficient funds.
     * <p>
     * Returns:
     * 0: Success
     * 1: Insufficient funds
     * 2: Failed to create account
     * 3: No economy support
     */
    int chargeForFlying(final Player player, final long ticks, final boolean force) {
        final double cost_per_tick = fc.getConfig().getDouble("flying.cost.per_tick");
        return charge(player, ticks * cost_per_tick, force);
    }

    /**
     * Uses the flying timer to appropriately charge the player.
     * If force, then will bankrupt player if insufficient funds.
     * <p>
     * Returns:
     * 0: Success
     * 1: Insufficient funds
     * 2: Failed to create account
     * 3: No economy support
     */
    @SuppressWarnings("SameParameterValue")
    int chargeForFlyingFromTimer(final Player player, final boolean force) {
        final long ticks = getFlyingTimer(player);
        if (ticks <= 0) return 0;
        return chargeForFlying(player, ticks, force);
    }

    /**
     * Returns the player's balance.
     */
    public double getBalance(final Player player) {
        return econ.getBalance(player);
    }

    /**
     * Returns the number of ticks that the player has been flying.
     */
    public long getFlyingTimer(final Player player) {
        final Long flying_timer = (Long) getMetadata(player, "flying_timer");
        if (flying_timer == null || flying_timer < 0L) return 0L;
        final long ticks = fc.getTicks() - flying_timer;
        return Math.max(ticks, 0);
    }

    /**
     * Returns true if the player has given amount in their balance.
     */
    public boolean insufficientFunds(final Player player, final double amount) {
        // TODO: This is kind of hackish -- it shouldn't return true if economy
        // TODO: is not enabled, but it does for reasons outside this class' scope.
        return isEnabled() && !econ.has(player, amount);
    }

    /**
     * Returns true if economy integration is enabled.
     */
    public boolean isEnabled() {
        return (econ != null);
    }

    /**
     * Starts the timer to keep track of how long the player is flying.
     */
    public void startFlyingTimer(final Player player) {
        setMetadata(player, "flying_timer", fc.getTicks());
    }

    /**
     * Stops the timer keeping track of how long the player is flying.
     */
    public void stopFlyingTimer(final Player player) {
        setMetadata(player, "flying_timer", -1L);
    }
}