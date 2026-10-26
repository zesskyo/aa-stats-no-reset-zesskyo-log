package dev.zesskyo.hermesreplay.core;

/** One tick of a Hermes ghost: where the player was, where they looked, and what they were doing. */
public final class GhostFrame {
    public static final int FLAG_SWINGING = 0x01;
    public static final int FLAG_USING_ITEM = 0x02;
    public static final int FLAG_SNEAKING = 0x04;
    public static final int FLAG_SPRINTING = 0x08;
    public static final int FLAG_HIT_COOLDOWN = 0x10;
    public static final int FLAG_ALIVE = 0x20;
    public static final int FLAG_FALL_FLYING = 0x40;
    public static final int FLAG_SWIMMING = 0x80;

    public final long time;
    public final double x, y, z;
    public final float yaw, pitch, health;
    public final int hotbarSlot;
    public final int flags;

    public GhostFrame(long time, double x, double y, double z, float yaw, float pitch, float health, int hotbarSlot, int flags) {
        this.time = time;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = health;
        this.hotbarSlot = hotbarSlot;
        this.flags = flags;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** Eye height above the feet for the pose in this frame (1.16 values). */
    public double eyeHeight() {
        if (has(FLAG_SWIMMING) || has(FLAG_FALL_FLYING)) return 0.4;
        if (has(FLAG_SNEAKING)) return 1.27;
        return 1.62;
    }

    /** Unit look vector, using Minecraft's yaw/pitch convention. */
    public double[] look() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new double[]{-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch};
    }

    @Override
    public String toString() {
        return String.format("GhostFrame[t=%d pos=(%.2f, %.2f, %.2f) yaw=%.1f pitch=%.1f hp=%.1f slot=%d flags=%02x]",
                time, x, y, z, yaw, pitch, health, hotbarSlot, flags);
    }
}
