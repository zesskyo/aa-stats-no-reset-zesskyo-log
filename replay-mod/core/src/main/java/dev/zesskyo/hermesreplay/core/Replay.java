package dev.zesskyo.hermesreplay.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A ghost track and its play log, with lookups by play time (the replay's clock, pauses removed). */
public final class Replay {
    public static final int INVENTORY_SLOTS = 41;
    public static final int SLOT_BOOTS = 36, SLOT_LEGS = 37, SLOT_CHEST = 38, SLOT_HELMET = 39, SLOT_OFFHAND = 40;
    private static final int INVENTORY_KEYFRAME_EVERY = 256;

    public final GhostTrack ghost;
    public final PlayLog log;

    private final List<ItemData[]> inventoryKeyframes = new ArrayList<>();
    private final List<PlayLog.StatChange> worldEdits = new ArrayList<>();
    private final List<PlayLog.AdvancementProgress> completions = new ArrayList<>();

    public Replay(GhostTrack ghost, PlayLog log) {
        if (ghost.isEmpty()) throw new IllegalArgumentException("ghost file has no ticks");
        this.ghost = ghost;
        this.log = log;
        ItemData[] state = new ItemData[INVENTORY_SLOTS];
        for (int i = 0; i < log.inventory.size(); i++) {
            if (i % INVENTORY_KEYFRAME_EVERY == 0) inventoryKeyframes.add(state.clone());
            apply(state, log.inventory.get(i));
        }
        for (PlayLog.StatChange s : log.stats) {
            if (s.diff > 0 && ("mined".equals(s.category) || "used".equals(s.category))) worldEdits.add(s);
        }
        for (PlayLog.AdvancementProgress a : log.advancements) {
            if (a.completed && !a.isRecipe()) completions.add(a);
        }
    }

    public static Replay load(Path ghostFile, Path logFile) throws IOException {
        return new Replay(GhostTrack.read(ghostFile), PlayLog.read(logFile));
    }

    private static void apply(ItemData[] state, PlayLog.InventoryChange change) {
        for (java.util.Map.Entry<Integer, ItemData> e : change.slots.entrySet()) {
            int slot = e.getKey();
            if (slot >= 0 && slot < state.length) state[slot] = e.getValue();
        }
    }

    public long duration() {
        return ghost.playDuration();
    }

    public long wallTime(long play) {
        return ghost.wallTimeAt(play);
    }

    public long playTime(long wall) {
        return ghost.playTimeAtWall(wall);
    }

    public GhostFrame ghostAt(long play) {
        return ghost.sampleAtPlayTime(play);
    }

    /** Dimension id at {@code play}, e.g. "minecraft:the_nether". Defaults to the overworld. */
    public String dimensionAt(long play) {
        long wall = wallTime(play);
        String dim = "minecraft:overworld";
        for (PlayLog.DimensionChange d : log.dimensions) {
            if (d.time > wall) break;
            if (d.dimension != null) dim = d.dimension;
        }
        return dim;
    }

    /** All 41 slots at {@code play}; null entries are empty. */
    public ItemData[] inventoryAt(long play) {
        long wall = wallTime(play);
        int count = upperBound(log.inventory, wall);
        if (count == 0) return new ItemData[INVENTORY_SLOTS];
        int key = (count - 1) / INVENTORY_KEYFRAME_EVERY;
        ItemData[] state = inventoryKeyframes.get(key).clone();
        for (int i = key * INVENTORY_KEYFRAME_EVERY; i < count; i++) apply(state, log.inventory.get(i));
        return state;
    }

    /** SpeedrunIGT's in-game time at {@code play}, in ms, extrapolated from the nearest earlier event. */
    public long igtAt(long play) {
        long wall = wallTime(play);
        int n = upperBound(log.timerPoints, wall);
        if (n == 0) return 0;
        PlayLog.Event e = log.timerPoints.get(n - 1);
        long since = play - playTime(e.time);
        return e.igt + Math.max(0, since);
    }

    /** Play time at which SpeedrunIGT showed {@code igt} ms, for seeking by the run's timer. */
    public long playTimeAtIgt(long igt) {
        PlayLog.Event best = null;
        for (PlayLog.Event e : log.timerPoints) {
            if (e.igt > igt) break;
            best = e;
        }
        if (best == null) return 0;
        return Math.min(duration(), playTime(best.time) + (igt - best.igt));
    }

    /** Advancements (not recipes) completed by {@code play}. */
    public int advancementsDoneAt(long play) {
        return upperBound(completions, wallTime(play));
    }

    public List<PlayLog.AdvancementProgress> completions() {
        return Collections.unmodifiableList(completions);
    }

    /** Every "mined" or "used" stat increment, in order; the mod turns these into block changes. */
    public List<PlayLog.StatChange> worldEdits() {
        return Collections.unmodifiableList(worldEdits);
    }

    /** Number of world edits at or before {@code play}. */
    public int worldEditsUpTo(long play) {
        return upperBound(worldEdits, wallTime(play));
    }

    /** Stretches of the timeline spent in each dimension, for drawing a coloured bar. */
    public List<Segment> dimensionSegments() {
        List<Segment> out = new ArrayList<>();
        String dim = "minecraft:overworld";
        long start = 0;
        for (PlayLog.DimensionChange d : log.dimensions) {
            if (d.dimension == null) continue;
            long at = Math.min(duration(), playTime(d.time));
            if (!d.dimension.equals(dim)) {
                if (at > start) out.add(new Segment(start, at, dim));
                dim = d.dimension;
                start = at;
            }
        }
        if (duration() > start) out.add(new Segment(start, duration(), dim));
        return out;
    }

    public static final class Segment {
        public final long start, end;
        public final String dimension;

        Segment(long start, long end, String dimension) {
            this.start = start;
            this.end = end;
            this.dimension = dimension;
        }
    }

    /** Number of events with time at or before {@code wall}. */
    static int upperBound(List<? extends PlayLog.Event> events, long wall) {
        int lo = 0, hi = events.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (events.get(mid).time <= wall) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
