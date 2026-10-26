package dev.zesskyo.hermesreplay.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads run 30 from this repository's logs/ folder, when it's there. */
class RealRunTest {
    static Replay load(String run) throws IOException {
        Path dir = Paths.get(System.getProperty("hermesreplay.logs", "../../logs"));
        Path ghost = dir.resolve(run + ".ghost"), log = dir.resolve(run + ".log");
        Assumptions.assumeTrue(Files.exists(ghost) && Files.exists(log), "run " + run + " not present");
        return Replay.load(ghost, log);
    }

    @Test
    void run30() throws IOException {
        Replay r = load("30");
        assertEquals(251_674, r.ghost.size());
        assertEquals(7429120198198009883L, r.log.seed);
        assertEquals("Zesskyo", r.log.playerName);
        assertEquals(0, r.log.skippedLines);

        // Spawn tick from the log's first dimension event.
        GhostFrame start = r.ghostAt(0);
        assertEquals(211.5, start.x);
        assertEquals(267.5, start.z);

        // With pauses removed the replay is close to the final IGT (Hermes and SpeedrunIGT count ticks slightly differently).
        long finalIgt = r.log.timerPoints.get(r.log.timerPoints.size() - 1).igt;
        System.out.printf("run 30: play duration %s, final IGT %s, wall span %s%n",
                fmt(r.duration()), fmt(finalIgt), fmt(r.ghost.endTime() - r.ghost.startTime()));
        assertTrue(Math.abs(r.duration() - finalIgt) < finalIgt * 0.05, "duration should be within 5% of IGT");

        List<Replay.Segment> segs = r.dimensionSegments();
        assertEquals("minecraft:overworld", segs.get(0).dimension);
        assertTrue(segs.stream().anyMatch(s -> s.dimension.equals("minecraft:the_end")));
        System.out.println("run 30: " + segs.size() + " dimension segments, " + r.worldEdits().size()
                + " world edits, " + r.completions().size() + " advancements");
        assertEquals(80, r.completions().stream().map(a -> a.id).distinct().count(), "all 80 advancements");

        // At the end of the run the ghost wears what the log says it wore.
        ItemData[] inv = r.inventoryAt(r.duration());
        assertNotNull(inv[Replay.SLOT_CHEST]);
        System.out.println("run 30 final armor: " + inv[Replay.SLOT_HELMET] + ", " + inv[Replay.SLOT_CHEST]
                + ", " + inv[Replay.SLOT_LEGS] + ", " + inv[Replay.SLOT_BOOTS]);

        // The ghost's position on entering the nether matches the dimension event.
        PlayLog.DimensionChange nether = r.log.dimensions.get(1);
        assertEquals("minecraft:the_nether", nether.dimension);
        long at = r.playTime(nether.time);
        GhostFrame g = r.ghostAt(at + 100);
        assertTrue(Math.abs(g.x - nether.x) < 3 && Math.abs(g.z - nether.z) < 3,
                "ghost " + g + " near portal exit " + nether.x + "," + nether.z);
        assertEquals("minecraft:the_nether", r.dimensionAt(at + 100));

        // Seeking by IGT lands where the timer shows that IGT.
        long target = 60 * 60 * 1000;
        long p = r.playTimeAtIgt(target);
        assertTrue(Math.abs(r.igtAt(p) - target) < 1000, "igt at seek point " + r.igtAt(p));
    }

    static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }
}
