package me.saltyhash.flightcontrol;

import java.util.List;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Economy manager. */
class EconManager {
    private final FlightControl fc;
    private Economy econ;
    
    public EconManager(FlightControl fc) {
        this.fc = fc;
        
        // Set up economy integration
        // Vault not found
        if (fc.getServer().getPluginManager().getPlugin("Vault") == null)
            fc.getLogger().warning(
                "Economy support disabled; dependency \"Vault\" not found");
        // Vault found
        else {
            RegisteredServiceProvider<Economy> rsp =
                fc.getServer().getServicesManager().getRegistration(Economy.class);
            // No economy plugin found
            if (rsp == null)
                fc.getLogger().warning(
                    "Economy integration disabled; economy plugin not found");
            // Economy plugin found
            else {
                this.econ = rsp.getProvider();
                if (this.econ == null)
                    fc.getLogger().warning(
                        "Economy integration disabled; unknown reason");
            }
        }
    }
    
    /** Returns the object for key in given player (null if DNE). */
    @SuppressWarnings("SameParameterValue")
    private Object getMetadata(Player player, String key) {
        List<MetadataValue> values = player.getMetadata(key);
        for (MetadataValue value : values) {
            if (value.getOwningPlugin().getDescription().getName().equals(
                fc.getDescription().getName())) {
                return value.value();
            }
        }
        return null;
    }
    
    /** Sets the object for key in given player. */
    @SuppressWarnings("SameParameterValue")
    private void setMetadata(Player player, String key, Object object) {
        player.setMetadata(key, new FixedMetadataValue(this.fc, object));
    }
    
    /**
     * Charges player the specified amount (can be negative).
     * If force, then will bankrupt player if insufficient funds.
     *
     * Returns:
     *   0: Success
     *   1: Insufficient funds
     *   2: Failed to create account
     *   3: No economy support
     */
    int charge(Player player, double amount, boolean force) {
        if (!this.isEnabled()) return 3;
        
        // Make sure player has an account
        if (!this.econ.hasAccount(player))
            if (!this.econ.createPlayerAccount(player)) return 2;
        
        // Withdraw from account
        if (amount > 0.0) {
            EconomyResponse result = this.econ.withdrawPlayer(player, amount);
            // Insufficient funds?
            if (!result.transactionSuccess()) {
                // Bankrupt the player?
                if (force) return this.charge(player, this.getBalance(player), false);
                // Return insufficient funds
                else return 1;
            }
        }
        // Deposit to account
        else if (amount < 0.0) {
            amount = -amount;
            this.econ.depositPlayer(player, amount);
        }
        return 0;
    }
    
    /**
     * Uses the config to appropriately charge the player.
     * If force, then will bankrupt player if insufficient funds.
     *
     * Returns:
     *   0: Success
     *   1: Insufficient funds
     *   2: Failed to create account
     *   3: No economy support
     */
    int chargeForFlying(Player player, long ticks, boolean force) {
        double cost_per_tick = this.fc.getConfig().getDouble("flying.cost.per_tick");
        return this.charge(player, ticks*cost_per_tick, force);
    }
    
    /**
     * Uses the flying timer to appropriately charge the player.
     * If force, then will bankrupt player if insufficient funds.
     *
     * Returns:
     *   0: Success
     *   1: Insufficient funds
     *   2: Failed to create account
     *   3: No economy support
     */
    @SuppressWarnings("SameParameterValue")
    int chargeForFlyingFromTimer(Player player, boolean force) {
        long ticks = this.getFlyingTimer(player);
        if (ticks <= 0) return 0;
        return this.chargeForFlying(player, ticks, force);
    }
    
    /** Returns the player's balance. */
    public double getBalance(Player player) {
        return this.econ.getBalance(player);
    }
    
    /** Returns the number of ticks that the player has been flying. */
    public long getFlyingTimer(Player player) {
        Long flying_timer = (Long) getMetadata(player, "flying_timer");
        if (flying_timer == null || flying_timer < 0L) return 0L;
        long ticks = fc.getTicks() - flying_timer;
        return Math.max(ticks, 0);
    }
    
    /** Returns true if the player has given amount in their balance. */
    public boolean hasBalance(Player player, double amount) {
        // TODO: This is kind of hackish -- it shouldn't return true if economy
        // TODO: is not enabled, but it does for reasons outside this class' scope.
        return !this.isEnabled() || this.econ.has(player, amount);
    }
    
    /** Returns true if economy integration is enabled. */
    public boolean isEnabled() {
        return (this.econ != null);
    }
    
    /** Starts the timer to keep track of how long the player is flying. */
    public void startFlyingTimer(Player player) {
        this.setMetadata(player, "flying_timer", this.fc.getTicks());
    }
    
    /** Stops the timer keeping track of how long the player is flying. */
    public void stopFlyingTimer(Player player) {
        this.setMetadata(player, "flying_timer", -1L);
    }
}