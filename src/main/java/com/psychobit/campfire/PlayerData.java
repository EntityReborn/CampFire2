package com.psychobit.campfire;

import java.io.Serializable;

/**
 * Holds data about players' campfire time
 *
 * @author psychobit
 *
 */
public class PlayerData implements Serializable {

    /**
     * Serializable ID
     */
    private static final long serialVersionUID = -295825367226483171L;
    /**
     * Timestamp of the last update
     */
    private long lastUpdated;
    /**
     * Is the player in a protected zone?
     */
    private boolean inProtectedZone;
    /**
     * Amount of time campfire has been enabled so far
     */
    private int timeElapsed;
    /**
     * Whether or not campfire is enabled for this person
     */
    private boolean disabled;
    /**
     * Whether or not the player has confirmed campfire termination
     */
    private boolean confirm;

    /**
     * Set the last updated time
     */
    public void setUpdateTime() {
        lastUpdated = (System.currentTimeMillis() / 1000);
    }

    /**
     * Get the last updated time
     *
     * @return Time player was last updated
     */
    public long getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Is the player in a protected zone?
     *
     * @return
     */
    public boolean inProtectedZone() {
        return inProtectedZone;
    }

    /**
     * Get the time elapsed
     *
     * @return Time elapsed
     */
    public int getTimeElapsed() {
        return timeElapsed;
    }

    /**
     * Is campfire enabled for this player
     *
     * @return Campfire enabled
     */
    public boolean isEnabled() {
        return !disabled;
    }

    /**
     * Set whether campfire is enabled or disabled for this player
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        disabled = !enabled;
    }

    /**
     * Set whether the player is in a protected zone or not
     *
     * @param enabled
     */
    public void setProtectedZone(boolean enabled) {
        inProtectedZone = enabled;
    }

    /**
     * Update the player's elapsed time
     */
    public void update() {
        if (disabled) {
            return;
        }
        
        long currentTime = (System.currentTimeMillis() / 1000);
        int inc = (int) (currentTime - lastUpdated);
        
        timeElapsed += inc;
        lastUpdated = (System.currentTimeMillis() / 1000);
    }

    /**
     * Reset the player
     */
    public void reset() {
        timeElapsed = 0;
        disabled = false;
        inProtectedZone = false;
        confirm = false;
        
        setEnabled(true);
        setUpdateTime();
    }

    /**
     * Set confirmation as available
     */
    public void setConfirmed() {
        confirm = true;
    }

    /**
     * Check if the player is ready to confirm termination
     *
     * @return
     */
    public boolean confirmed() {
        return confirm;
    }
}