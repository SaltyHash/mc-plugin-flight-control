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
    private final FlightControl plugin;
    private Economy economy;

    EconManager(final FlightControl plugin) {
        this.plugin = plugin;

        // Set up economy integration
        // - Vault not found?
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning(
                    "Economy support disabled; dependency \"Vault\" not found.");
        }
        // - Vault found?
        else {
            final RegisteredServiceProvider<Economy> rsp =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);

            // No economy plugin found
            if (rsp == null) {
                plugin.getLogger().warning(
                        "Economy integration disabled; economy plugin not found.");
            }
            // Economy plugin found
            else {
                economy = rsp.getProvider();
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

            if (owningPlugin.getDescription().getName().equals(plugin.getDescription().getName()))
                return value.value();
        }

        return null;
    }

    /**
     * Sets the object for key in given player.
     */
    @SuppressWarnings("SameParameterValue")
    private void setMetadata(final Player player, final String key, final Object object) {
        player.setMetadata(key, new FixedMetadataValue(plugin, object));
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
    int charge(final Player player, double amount) {
        if (!isEnabled()) return 3;

        // Make sure player has an account
        if (!economy.hasAccount(player))
            if (!economy.createPlayerAccount(player)) return 2;

        // Withdraw from account?
        if (amount > 0.0) {
            // Make sure player does not have a zero balance
            final double balance = getBalance(player);
            if (balance == 0.0) return 1;

            // Withdraw either the specified amount, or the remainder of the player's balance, whichever is smaller
            final double withdraw = Math.min(amount, balance);
            final EconomyResponse result = economy.withdrawPlayer(player, withdraw);

            // Log error if the withdrawal was not successful
            if (!result.transactionSuccess())
                plugin.getLogger().severe(result.errorMessage);
        }
        // Deposit to account?
        else if (amount < 0.0) {
            amount = -amount;
            economy.depositPlayer(player, amount);
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
    private void chargeForFlying(final Player player, final long ticks) {
        final double costPerTick = plugin.getConfig().getDouble("flying.cost.per_tick");
        charge(player, ticks * costPerTick);
    }

    /**
     * Uses the flying timer to appropriately charge the player.
     */
    void chargeForFlyingFromTimer(final Player player) {
        chargeForFlying(player, getFlyingTimer(player));
    }

    /**
     * Returns the player's balance.
     */
    private double getBalance(final Player player) {
        return economy.getBalance(player);
    }

    /**
     * Returns the number of ticks that the player has been flying.
     */
    private long getFlyingTimer(final Player player) {
        final Long flyingTimer = (Long) getMetadata(player, "flying_timer");
        if (flyingTimer == null || flyingTimer < 0L) return 0L;

        final long ticks = plugin.getTicks() - flyingTimer;
        return Math.max(ticks, 0);
    }

    /**
     * Returns true if the player does not have at least the specified amount.
     * If economy is not available or enabled, then this returns false.
     */
    boolean insufficientFunds(final Player player, final double amount) {
        return isEnabled() && !economy.has(player, amount);
    }

    /**
     * Returns true if economy integration is enabled.
     */
    private boolean isEnabled() {
        return economy != null;
    }

    /**
     * Starts the timer to keep track of how long the player is flying.
     */
    void startFlyingTimer(final Player player) {
        setMetadata(player, "flying_timer", plugin.getTicks());
    }

    /**
     * Stops the timer keeping track of how long the player is flying.
     */
    void stopFlyingTimer(final Player player) {
        setMetadata(player, "flying_timer", -1L);
    }
}