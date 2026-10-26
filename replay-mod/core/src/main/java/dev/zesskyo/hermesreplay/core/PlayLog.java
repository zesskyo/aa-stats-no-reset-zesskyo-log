package dev.zesskyo.hermesreplay.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of a deciphered Hermes play log (one JSON object per line) that a replay needs.
 * Events keep Hermes' wall-clock {@code time}, which lines up with ghost timestamps.
 */
public final class PlayLog {
    public static class Event {
        public final long time;
        /** SpeedrunIGT's real-time and in-game timers at this event, in ms (0 when unknown). */
        public final long rta, igt;

        Event(long time, long rta, long igt) {
            this.time = time;
            this.rta = rta;
            this.igt = igt;
        }
    }

    public static final class DimensionChange extends Event {
        public final String dimension;
        public final double x, y, z;

        DimensionChange(long time, long rta, long igt, String dimension, double x, double y, double z) {
            super(time, rta, igt);
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class InventoryChange extends Event {
        /** Slot number to new contents; null means the slot was emptied. 0-8 hotbar, 9-35 main, 36-39 boots..helmet, 40 offhand. */
        public final Map<Integer, ItemData> slots;

        InventoryChange(long time, long rta, long igt, Map<Integer, ItemData> slots) {
            super(time, rta, igt);
            this.slots = slots;
        }
    }

    public static final class StatChange extends Event {
        /** e.g. "mined", "used", "crafted", "picked_up", "dropped", "killed", "custom". */
        public final String category;
        /** e.g. "minecraft:stone". */
        public final String key;
        public final int value, diff;

        StatChange(long time, long rta, long igt, String category, String key, int value, int diff) {
            super(time, rta, igt);
            this.category = category;
            this.key = key;
            this.value = value;
            this.diff = diff;
        }
    }

    public static final class AdvancementProgress extends Event {
        public final String id, criterion;
        /** Whether the whole advancement is done after this criterion. */
        public final boolean completed;

        AdvancementProgress(long time, long rta, long igt, String id, String criterion, boolean completed) {
            super(time, rta, igt);
            this.id = id;
            this.criterion = criterion;
            this.completed = completed;
        }

        /** Recipe unlocks are advancements too, but not ones a runner cares about. */
        public boolean isRecipe() {
            return id.startsWith("minecraft:recipes/");
        }
    }

    public static final class EffectsChange extends Event {
        public final Map<String, Integer> effects;

        EffectsChange(long time, long rta, long igt, Map<String, Integer> effects) {
            super(time, rta, igt);
            this.effects = effects;
        }
    }

    public String playerName;
    public String playerUuid;
    public String mcVersion;
    public String hermesVersion;
    /** The world seed, or null if the runner never typed /seed (Hermes only logs it then). */
    public Long seed;

    public final List<Event> timerPoints = new ArrayList<>();
    public final List<DimensionChange> dimensions = new ArrayList<>();
    public final List<InventoryChange> inventory = new ArrayList<>();
    public final List<StatChange> stats = new ArrayList<>();
    public final List<AdvancementProgress> advancements = new ArrayList<>();
    public final List<EffectsChange> effects = new ArrayList<>();
    public final List<Event> deaths = new ArrayList<>();
    public int skippedLines;

    public static PlayLog read(Path file) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(r);
        }
    }

    public static PlayLog read(Reader reader) throws IOException {
        PlayLog log = new PlayLog();
        BufferedReader r = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        JsonParser parser = new JsonParser();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) continue;
            try {
                JsonElement el = parser.parse(line);
                if (el.isJsonObject()) log.accept(el.getAsJsonObject());
            } catch (RuntimeException e) {
                // A line cut off by a crash; everything else is still usable.
                log.skippedLines++;
            }
        }
        // Hermes writes in order, but sort anyway so lookups can binary search.
        Collections.sort(log.timerPoints, (a, b) -> Long.compare(a.time, b.time));
        return log;
    }

    private void accept(JsonObject o) {
        String type = str(o, "type");
        long time = o.has("time") ? o.get("time").getAsLong() : 0;
        long rta = 0, igt = 0;
        if (o.has("speedrunigt") && o.get("speedrunigt").isJsonObject()) {
            JsonObject t = o.getAsJsonObject("speedrunigt");
            rta = t.has("rta") ? t.get("rta").getAsLong() : 0;
            igt = t.has("igt") ? t.get("igt").getAsLong() : 0;
        }
        JsonObject d = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : new JsonObject();
        if (playerName == null && d.has("player") && d.get("player").isJsonObject()) {
            playerName = str(d.getAsJsonObject("player"), "name");
            playerUuid = str(d.getAsJsonObject("player"), "uuid");
        }
        if (igt > 0) timerPoints.add(new Event(time, rta, igt));
        if (type == null) return;
        switch (type) {
            case "initialize":
                if (mcVersion == null) mcVersion = str(d, "mc_version");
                if (hermesVersion == null) hermesVersion = str(d, "hermes_version");
                break;
            case "seed":
                if (d.has("seed")) seed = d.get("seed").getAsLong();
                break;
            case "dimension": {
                JsonObject p = d.has("position") && d.get("position").isJsonObject() ? d.getAsJsonObject("position") : new JsonObject();
                dimensions.add(new DimensionChange(time, rta, igt, str(d, "dimension"),
                        num(p, "x"), num(p, "y"), num(p, "z")));
                break;
            }
            case "inventory_slots": {
                Map<Integer, ItemData> slots = new LinkedHashMap<>();
                if (d.has("slots") && d.get("slots").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("slots").entrySet()) {
                        slots.put(Integer.parseInt(e.getKey()), item(e.getValue()));
                    }
                }
                inventory.add(new InventoryChange(time, rta, igt, slots));
                break;
            }
            case "stat": {
                // "minecraft.mined:minecraft.stone" -> category "mined", key "minecraft:stone"
                String stat = str(d, "stat");
                if (stat == null) break;
                int colon = stat.indexOf(':');
                if (colon < 0) break;
                String cat = stat.substring(0, colon);
                int dot = cat.lastIndexOf('.');
                cat = dot >= 0 ? cat.substring(dot + 1) : cat;
                String key = stat.substring(colon + 1).replaceFirst("^([a-z0-9_\\-]+)\\.", "$1:");
                stats.add(new StatChange(time, rta, igt, cat, key,
                        d.has("value") ? d.get("value").getAsInt() : 0, d.has("diff") ? d.get("diff").getAsInt() : 0));
                break;
            }
            case "advancement":
                advancements.add(new AdvancementProgress(time, rta, igt, str(d, "id"), str(d, "criterion_name"),
                        d.has("completed") && d.get("completed").getAsBoolean()));
                break;
            case "status_effects": {
                Map<String, Integer> fx = new LinkedHashMap<>();
                if (d.has("effects") && d.get("effects").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("effects").entrySet()) {
                        fx.put(e.getKey(), e.getValue().getAsInt());
                    }
                }
                effects.add(new EffectsChange(time, rta, igt, fx));
                break;
            }
            case "respawn":
                deaths.add(new Event(time, rta, igt));
                break;
            default:
                break;
        }
    }

    private static ItemData item(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        String id = str(o, "id");
        if (id == null) return null;
        int count = o.has("Count") ? o.get("Count").getAsInt() : 1;
        JsonObject tag = o.has("tag") && o.get("tag").isJsonObject() ? o.getAsJsonObject("tag") : null;
        return new ItemData(id, count, tag);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static double num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }
}
