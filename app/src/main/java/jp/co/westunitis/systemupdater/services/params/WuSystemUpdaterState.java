package jp.co.westunitis.systemupdater.services.params;

import android.util.SparseArray;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controls updater state.
 */
public class WuSystemUpdaterState {
    public static final int IDLE = 0;
    public static final int ERROR = 1;
    public static final int CHECKING_UPDATE = 2;
    public static final int UPDATE_AVAILABLE = 3;
    public static final int DOWNLOADING = 4;
    public static final int CANCEL_DOWNLOADING = 5;
    public static final int APPLYING_UPDATE = 6;
    public static final int FINALIZING = 7;
    public static final int WAITING_REBOOT = 8;

    private static final SparseArray<String> STATE_MAP = new SparseArray<>();

    static {
        STATE_MAP.put(0, "IDLE");
        STATE_MAP.put(1, "ERROR");
        STATE_MAP.put(2, "CHECKING_UPDATE");
        STATE_MAP.put(3, "UPDATE_AVAILABLE");
        STATE_MAP.put(4, "DOWNLOADING");
        STATE_MAP.put(5, "CANCEL_DOWNLOADING");
        STATE_MAP.put(6, "APPLYING_UPDATE");
        STATE_MAP.put(7, "FINALIZING");
        STATE_MAP.put(8, "WAITING_REBOOT");
    }

    /**
     * Allowed state transitions. It's a map: key is a state, value is a set of states that
     * are allowed to transition to from key.
     */
    private static final ImmutableMap<Integer, ImmutableSet<Integer>> TRANSITIONS =
            ImmutableMap.<Integer, ImmutableSet<Integer>>builder()
                    .put(IDLE, ImmutableSet.of(
                            IDLE, ERROR, CHECKING_UPDATE, FINALIZING))
                    .put(ERROR, ImmutableSet.of(IDLE))
                    .put(CHECKING_UPDATE, ImmutableSet.of(IDLE, ERROR, UPDATE_AVAILABLE))
                    .put(UPDATE_AVAILABLE, ImmutableSet.of(ERROR, CHECKING_UPDATE, DOWNLOADING))
                    .put(DOWNLOADING, ImmutableSet.of(IDLE, ERROR, CANCEL_DOWNLOADING,
                            APPLYING_UPDATE))
                    .put(CANCEL_DOWNLOADING, ImmutableSet.of(ERROR, IDLE))
                    .put(APPLYING_UPDATE, ImmutableSet.of(ERROR, IDLE, FINALIZING))
                    .put(FINALIZING, ImmutableSet.of(ERROR, IDLE, WAITING_REBOOT))
                    .put(WAITING_REBOOT, ImmutableSet.of(ERROR))
                    .build();

    private AtomicInteger mState;

    public WuSystemUpdaterState(int state) {
        this.mState = new AtomicInteger(state);
    }

    /**
     * Returns updater state.
     */
    public int get() {
        return mState.get();
    }

    /**
     * Sets the updater state.
     *
     * @throws WuSystemUpdaterState.InvalidTransitionException if transition is not allowed.
     */
    public void set(int newState) throws WuSystemUpdaterState.InvalidTransitionException {
        int oldState = mState.get();
        if (!TRANSITIONS.get(oldState).contains(newState)) {
            throw new WuSystemUpdaterState.InvalidTransitionException(
                    "Can't transition from " + oldState + " to " + newState);
        }
        mState.set(newState);
    }

    /**
     * Converts status code to status name.
     */
    public static String getStateText(int state) {
        return STATE_MAP.get(state);
    }

    /**
     * Defines invalid state transition exception.
     */
    public static class InvalidTransitionException extends Exception {
        public InvalidTransitionException(String msg) {
            super(msg);
        }
    }
}
