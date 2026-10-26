package dev.zesskyo.hermesreplay.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayLogTest {
    static final String P = "\"player\":{\"name\":\"Zesskyo\",\"uuid\":\"616d19b7-69ef-4d04-b519-40a454298665\"}";

    static String line(long time, String type, String data, long igt) {
        return "{\"time\":" + time + ",\"type\":\"" + type + "\",\"data\":{" + data + "},\"speedrunigt\":{\"rta\":" + igt + ",\"igt\":" + igt + ",\"retime\":0}}\n";
    }

    static final String SAMPLE =
            line(1000, "initialize", "\"hermes_version\":\"0.16.0+MC1.16.1\",\"mc_version\":\"1.16.1\"", 0)
                    + line(1000, "dimension", P + ",\"old_dimension\":null,\"dimension\":\"minecraft:overworld\",\"position\":{\"x\":211.5,\"y\":63.0,\"z\":267.5}", 0)
                    + line(2000, "stat", P + ",\"stat\":\"minecraft.mined:minecraft.oak_log\",\"value\":1,\"diff\":1", 1000)
                    + line(2100, "inventory_slots", P + ",\"slots\":{\"0\":{\"id\":\"minecraft:oak_log\",\"Count\":1},\"39\":{\"id\":\"minecraft:iron_helmet\",\"Count\":1,\"tag\":{\"Damage\":0}}}", 1100)
                    + line(2200, "inventory_slots", P + ",\"slots\":{\"0\":null}", 1200)
                    + line(3000, "advancement", P + ",\"id\":\"minecraft:story/root\",\"criterion_name\":\"crafting_table\",\"completed\":true", 2000)
                    + line(3000, "advancement", P + ",\"id\":\"minecraft:recipes/misc/stick\",\"criterion_name\":\"has_planks\",\"completed\":true", 2000)
                    + "{\"time\":3500,\"type\":\"stat\",\"da" // cut off by a crash
                    + "\n" + line(4000, "seed", "\"seed\":7429120198198009883", 3000)
                    + line(4000, "command", P + ",\"command\":\"/seed\"", 3000);

    @Test
    void parsesTheEventsAReplayUses() throws IOException {
        PlayLog log = PlayLog.read(new StringReader(SAMPLE));
        assertEquals("Zesskyo", log.playerName);
        assertEquals("1.16.1", log.mcVersion);
        assertEquals(7429120198198009883L, log.seed);
        assertEquals(1, log.skippedLines);
        assertEquals(1, log.dimensions.size());
        assertEquals(211.5, log.dimensions.get(0).x);

        PlayLog.StatChange mined = log.stats.get(0);
        assertEquals("mined", mined.category);
        assertEquals("minecraft:oak_log", mined.key);

        assertEquals(2, log.inventory.size());
        assertEquals("minecraft:iron_helmet", log.inventory.get(0).slots.get(39).id);
        assertTrue(log.inventory.get(1).slots.containsKey(0));
        assertNull(log.inventory.get(1).slots.get(0));

        assertTrue(log.advancements.get(1).isRecipe());
    }

    @Test
    void seedIsNullWhenNeverTyped() throws IOException {
        PlayLog log = PlayLog.read(new StringReader(line(1, "initialize", "\"mc_version\":\"1.16.1\"", 0)));
        assertNull(log.seed);
    }
}
