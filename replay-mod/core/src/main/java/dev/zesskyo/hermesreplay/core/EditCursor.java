package dev.zesskyo.hermesreplay.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the replay's world edits as the clock moves. Going forward it asks the caller to apply
 * each edit and keeps whatever undo record the caller returns; going back it hands those records
 * back, newest first, so the world can be restored. {@code U} is the caller's undo record.
 */
public final class EditCursor<U> {
    public interface Applier<U> {
        /** Applies the edit; returns how to undo it, or null if nothing changed. */
        U apply(PlayLog.StatChange edit);
    }

    public interface Undoer<U> {
        void undo(U record);
    }

    private final Replay replay;
    private final List<U> undo = new ArrayList<>();

    public EditCursor(Replay replay) {
        this.replay = replay;
    }

    /** Number of edits applied so far. */
    public int applied() {
        return undo.size();
    }

    /**
     * Brings the world to {@code play}, applying at most {@code budget} edits so a long seek can be
     * spread over several ticks. Returns true once the world has caught up.
     */
    public boolean moveTo(long play, int budget, Applier<U> applier, Undoer<U> undoer) {
        int target = replay.worldEditsUpTo(play);
        List<PlayLog.StatChange> edits = replay.worldEdits();
        while (undo.size() > target && budget-- > 0) {
            U record = undo.remove(undo.size() - 1);
            if (record != null) undoer.undo(record);
        }
        while (undo.size() < target && budget-- > 0) {
            undo.add(applier.apply(edits.get(undo.size())));
        }
        return undo.size() == target;
    }

    /** Undoes every applied edit, newest first. */
    public void undoAll(Undoer<U> undoer) {
        while (!undo.isEmpty()) {
            U record = undo.remove(undo.size() - 1);
            if (record != null) undoer.undo(record);
        }
    }
}
