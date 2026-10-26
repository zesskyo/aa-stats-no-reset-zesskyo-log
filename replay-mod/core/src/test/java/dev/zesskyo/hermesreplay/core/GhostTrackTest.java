package dev.zesskyo.hermesreplay.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostTrackTest {
    static byte[] record(long t, double x, double y, double z, float yaw, float pitch, float hp, int slot, int flags) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        out.writeLong(t);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
        out.writeFloat(hp);
        out.writeByte(0xF0 | slot); // high bits are unused and must be ignored
        out.writeByte(flags);
        return b.toByteArray();
    }

    static GhostTrack track(byte[]... records) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] r : records) b.write(r);
        return GhostTrack.read(new ByteArrayInputStream(b.toByteArray()), records.length);
    }

    @Test
    void readsFields() throws IOException {
        GhostTrack t = track(record(1000, 211.5, 63, 267.5, -90, 30, 20, 4, 0x2C));
        GhostFrame f = t.frame(0);
        assertEquals(1000, f.time);
        assertEquals(211.5, f.x);
        assertEquals(-90f, f.yaw);
        assertEquals(4, f.hotbarSlot);
        assertTrue(f.has(GhostFrame.FLAG_ALIVE));
        assertTrue(f.has(GhostFrame.FLAG_SNEAKING));
        assertTrue(f.has(GhostFrame.FLAG_SPRINTING));
        assertFalse(f.has(GhostFrame.FLAG_SWINGING));
    }

    @Test
    void dropsTruncatedLastRecord() throws IOException {
        byte[] whole = record(0, 0, 0, 0, 0, 0, 20, 0, 0x20);
        byte[] cut = new byte[20];
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(whole);
        b.write(cut);
        GhostTrack t = GhostTrack.read(new ByteArrayInputStream(b.toByteArray()), 2);
        assertEquals(1, t.size());
    }

    @Test
    void squeezesPausesOutOfPlayTime() throws IOException {
        GhostTrack t = track(
                record(0, 0, 64, 0, 0, 0, 20, 0, 0x20),
                record(50, 1, 64, 0, 0, 0, 20, 0, 0x20),
                record(60_050, 2, 64, 0, 0, 0, 20, 0, 0x20), // a minute paused
                record(60_100, 3, 64, 0, 0, 0, 20, 0, 0x20));
        assertEquals(150, t.playDuration());
        assertEquals(2, t.indexAtPlayTime(100));
        assertEquals(60_050, t.wallTimeAt(100));
        assertEquals(100, t.playTimeAtWall(60_050));
        // Wall times inside the pause stay at the pause.
        assertEquals(100, t.playTimeAtWall(30_000));
    }

    @Test
    void interpolatesButNotAcrossTeleports() throws IOException {
        GhostTrack t = track(
                record(0, 0, 64, 0, 350, 0, 20, 0, 0x20),
                record(50, 1, 64, 0, 10, 0, 20, 0, 0x20),
                record(100, 500, 64, 0, 10, 0, 20, 0, 0x20));
        GhostFrame mid = t.sampleAtPlayTime(25);
        assertEquals(0.5, mid.x, 1e-9);
        // 350 -> 10 is 20 degrees forward, not 340 back.
        assertEquals(360, mid.yaw, 1e-3);
        GhostFrame jump = t.sampleAtPlayTime(70);
        assertEquals(1, jump.x, 1e-9);
    }
}
