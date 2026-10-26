package dev.zesskyo.hermesreplay.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockInferenceTest {
    /** Flat stone ground at y <= 63, air above, plus whatever the test puts in. */
    static final class FakeWorld implements BlockInference.WorldView {
        final Map<String, String> blocks = new HashMap<>();

        FakeWorld put(int x, int y, int z, String b) {
            blocks.put(x + "," + y + "," + z, b);
            return this;
        }

        @Override
        public String blockAt(int x, int y, int z) {
            String b = blocks.get(x + "," + y + "," + z);
            if (b != null) return b;
            return y <= 63 ? "minecraft:stone" : BlockInference.AIR;
        }
    }

    static GhostFrame standing(double x, double z, float yaw, float pitch) {
        return new GhostFrame(0, x, 64, z, yaw, pitch, 20, 0, GhostFrame.FLAG_ALIVE);
    }

    @Test
    void minesTheBlockInFrontOfTheEyes() {
        // Yaw 0 looks toward +z. A log at eye height two blocks ahead.
        FakeWorld w = new FakeWorld().put(0, 65, 2, "minecraft:oak_log");
        BlockInference.Change c = BlockInference.inferMined(standing(0.5, 0.5, 0, 0), "minecraft:oak_log", w);
        assertEquals(new BlockInference.Change(0, 65, 2, BlockInference.AIR), c);
    }

    @Test
    void minesTheGroundWhenLookingDown() {
        BlockInference.Change c = BlockInference.inferMined(standing(0.5, 0.5, 0, 90), "minecraft:stone", new FakeWorld());
        assertEquals(new BlockInference.Change(0, 63, 0, BlockInference.AIR), c);
    }

    @Test
    void findsANeighbourWhenTheGhostIsSlightlyOff() {
        // Looking at stone at (0,65,2) but the stat says dirt, which sits right next to it.
        FakeWorld w = new FakeWorld().put(0, 65, 2, "minecraft:stone").put(1, 65, 2, "minecraft:dirt");
        BlockInference.Change c = BlockInference.inferMined(standing(0.5, 0.5, 0, 0), "minecraft:dirt", w);
        assertEquals(new BlockInference.Change(1, 65, 2, BlockInference.AIR), c);
    }

    @Test
    void givesUpWhenNothingMatches() {
        assertNull(BlockInference.inferMined(standing(0.5, 0.5, 0, -90), "minecraft:diamond_ore", new FakeWorld()));
    }

    @Test
    void placesAgainstTheFaceItLooksAt() {
        // Looking straight down: the block goes on top of the ground, at the player's feet.
        BlockInference.Change c = BlockInference.inferPlaced(standing(0.5, 0.5, 0, 90), "minecraft:cobblestone", new FakeWorld());
        assertEquals(new BlockInference.Change(0, 64, 0, "minecraft:cobblestone"), c);
    }

    @Test
    void replacesTallGrassInsteadOfStackingOnIt() {
        FakeWorld w = new FakeWorld().put(0, 64, 2, "minecraft:grass");
        // Pitch ~23.5 degrees down hits (0,64,2) from an eye at 65.62.
        BlockInference.Change c = BlockInference.inferPlaced(standing(0.5, 0.5, 0, 33), "minecraft:dirt", w);
        assertEquals(new BlockInference.Change(0, 64, 2, "minecraft:dirt"), c);
    }

    @Test
    void cannotPlaceIntoThinAir() {
        assertNull(BlockInference.inferPlaced(standing(0.5, 0.5, 0, -90), "minecraft:dirt", new FakeWorld()));
    }
}
