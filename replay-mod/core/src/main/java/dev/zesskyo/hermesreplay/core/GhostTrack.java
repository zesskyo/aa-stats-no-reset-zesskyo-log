package dev.zesskyo.hermesreplay.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * A whole Hermes ghost file in memory. Each tick is 46 big-endian bytes:
 * long time, double x/y/z, float yaw/pitch/health, a byte whose low 4 bits are the hotbar slot,
 * and a flags byte (see {@link GhostFrame}).
 *
 * <p>Ghost time is wall-clock milliseconds, which jumps whenever the game was paused or closed.
 * "Play time" squeezes those gaps out so a replay timeline runs at game speed.
 */
public final class GhostTrack {
    public static final int RECORD_BYTES = 46;
    /** Any gap between ticks longer than this counts as a pause and is shortened to one tick. */
    public static final long MAX_TICK_GAP_MS = 1000;
    private static final long TICK_MS = 50;
    /** Ticks further apart than this are a teleport (portal, death, pearl) and are not smoothed. */
    private static final double TELEPORT_DISTANCE_SQ = 10 * 10;

    private final int size;
    private final long[] time;
    private final long[] playTime;
    private final double[] x, y, z;
    private final float[] yaw, pitch, health;
    private final byte[] slot, flags;

    private GhostTrack(int capacity) {
        time = new long[capacity];
        playTime = new long[capacity];
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        yaw = new float[capacity];
        pitch = new float[capacity];
        health = new float[capacity];
        slot = new byte[capacity];
        flags = new byte[capacity];
        size = capacity;
    }

    public static GhostTrack read(Path file) throws IOException {
        long bytes = Files.size(file);
        try (InputStream in = Files.newInputStream(file)) {
            return read(in, (int) (bytes / RECORD_BYTES));
        }
    }

    /** Reads {@code frames} records; a truncated last record (game crashed mid-write) is dropped. */
    public static GhostTrack read(InputStream raw, int frames) throws IOException {
        GhostTrack t = new GhostTrack(frames);
        DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16));
        int n = 0;
        try {
            for (; n < frames; n++) {
                t.time[n] = in.readLong();
                t.x[n] = in.readDouble();
                t.y[n] = in.readDouble();
                t.z[n] = in.readDouble();
                t.yaw[n] = in.readFloat();
                t.pitch[n] = in.readFloat();
                t.health[n] = in.readFloat();
                t.slot[n] = (byte) (in.readUnsignedByte() & 0x0F);
                t.flags[n] = in.readByte();
            }
        } catch (EOFException e) {
            return t.truncate(n).computePlayTime();
        }
        return t.computePlayTime();
    }

    private GhostTrack truncate(int n) {
        GhostTrack t = new GhostTrack(n);
        System.arraycopy(time, 0, t.time, 0, n);
        System.arraycopy(x, 0, t.x, 0, n);
        System.arraycopy(y, 0, t.y, 0, n);
        System.arraycopy(z, 0, t.z, 0, n);
        System.arraycopy(yaw, 0, t.yaw, 0, n);
        System.arraycopy(pitch, 0, t.pitch, 0, n);
        System.arraycopy(health, 0, t.health, 0, n);
        System.arraycopy(slot, 0, t.slot, 0, n);
        System.arraycopy(flags, 0, t.flags, 0, n);
        return t;
    }

    private GhostTrack computePlayTime() {
        for (int i = 1; i < size; i++) {
            long dt = time[i] - time[i - 1];
            if (dt < 0) dt = 0;
            if (dt > MAX_TICK_GAP_MS) dt = TICK_MS;
            playTime[i] = playTime[i - 1] + dt;
        }
        return this;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public GhostFrame frame(int i) {
        return new GhostFrame(time[i], x[i], y[i], z[i], yaw[i], pitch[i], health[i], slot[i], flags[i] & 0xFF);
    }

    public long startTime() {
        return time[0];
    }

    public long endTime() {
        return time[size - 1];
    }

    /** Length of the replay with pauses removed, in milliseconds. */
    public long playDuration() {
        return size == 0 ? 0 : playTime[size - 1];
    }

    /** Index of the last frame at or before {@code wallTime}, clamped to the track. */
    public int indexAtTime(long wallTime) {
        return floorIndex(time, wallTime);
    }

    /** Index of the last frame at or before {@code play}, clamped to the track. */
    public int indexAtPlayTime(long play) {
        return floorIndex(playTime, play);
    }

    private int floorIndex(long[] keys, long key) {
        int i = Arrays.binarySearch(keys, 0, size, key);
        if (i < 0) i = -i - 2;
        // Equal keys are possible after a pause; take the last of them.
        while (i + 1 < size && keys[i + 1] == key) i++;
        return Math.max(0, Math.min(size - 1, i));
    }

    public long playTimeOf(int index) {
        return playTime[index];
    }

    public long timeOf(int index) {
        return time[index];
    }

    /** Converts play time to wall time, interpolating inside a tick. */
    public long wallTimeAt(long play) {
        int i = indexAtPlayTime(play);
        if (i + 1 >= size) return time[i];
        long span = playTime[i + 1] - playTime[i];
        if (span <= 0) return time[i];
        long into = Math.max(0, play - playTime[i]);
        long wallSpan = Math.min(time[i + 1] - time[i], span);
        return time[i] + into * wallSpan / span;
    }

    /** Converts wall time to play time. Times inside a pause land on the pause's start. */
    public long playTimeAtWall(long wall) {
        int i = indexAtTime(wall);
        long into = Math.max(0, Math.min(wall - time[i], i + 1 < size ? playTime[i + 1] - playTime[i] : 0));
        return playTime[i] + into;
    }

    /** The ghost at {@code play}, smoothly interpolated between ticks unless it teleported. */
    public GhostFrame sampleAtPlayTime(long play) {
        int i = indexAtPlayTime(play);
        if (i + 1 >= size) return frame(i);
        long span = playTime[i + 1] - playTime[i];
        if (span <= 0) return frame(i + 1);
        double a = Math.max(0, Math.min(1, (play - playTime[i]) / (double) span));
        double dx = x[i + 1] - x[i], dy = y[i + 1] - y[i], dz = z[i + 1] - z[i];
        if (dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQ) return frame(a < 0.5 ? i : i + 1);
        int j = a < 0.5 ? i : i + 1;
        return new GhostFrame(
                time[i] + Math.round(a * (time[i + 1] - time[i])),
                x[i] + a * dx, y[i] + a * dy, z[i] + a * dz,
                lerpAngle(yaw[i], yaw[i + 1], a), (float) (pitch[i] + a * (pitch[i + 1] - pitch[i])),
                health[j], slot[j], flags[j] & 0xFF);
    }

    static float lerpAngle(float from, float to, double a) {
        double d = ((to - from) % 360 + 540) % 360 - 180;
        return (float) (from + a * d);
    }
}
