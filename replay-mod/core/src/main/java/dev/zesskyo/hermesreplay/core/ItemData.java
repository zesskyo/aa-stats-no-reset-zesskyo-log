package dev.zesskyo.hermesreplay.core;

import com.google.gson.JsonObject;

/** An item stack as Hermes logs it: id, count, and the item's NBT as JSON (may be null). */
public final class ItemData {
    public final String id;
    public final int count;
    public final JsonObject tag;

    public ItemData(String id, int count, JsonObject tag) {
        this.id = id;
        this.count = count;
        this.tag = tag;
    }

    @Override
    public String toString() {
        return count + "x " + id;
    }
}
