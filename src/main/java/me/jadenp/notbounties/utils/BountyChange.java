package me.jadenp.notbounties.utils;

import me.jadenp.notbounties.data.Bounty;

/**
 * A change in a bounty that occurred while a database was not connected
 */
public record BountyChange(me.jadenp.notbounties.utils.BountyChange.ChangeType changeType, Bounty change) {
    public enum ChangeType {
        ADD_BOUNTY, // change is a bounty
        DELETE_BOUNTY, // change is a bounty, if the setters are empty, then the whole bounty should be deleted.
        REPLACE_BOUNTY, // change is the new bounty
        NOTIFY // change is the bounty that has been notified
    }
}


