package dev.zesskyo.hermesreplay.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Hermes doesn't log which block was broken or placed, only that a "mined" or "used" stat went up.
 * This works out the block by casting a ray from the ghost's eyes along its look direction, the
 * same way the player's crosshair picked it in game. It's a best guess: explosions, fluids and
 * anything placed out of view are missed.
 */
public final class BlockInference {
    /** Survival reach is 4.5 blocks; a little extra covers server/ghost lag. */
    public static final double REACH = 5.5;

    /** The world as the inference needs to see it. Block ids are like "minecraft:stone". */
    public interface WorldView {
        String blockAt(int x, int y, int z);
    }

    /** A guessed change: set {@code (x, y, z)} to {@code block} ("minecraft:air" for a break). */
    public static final class Change {
        public final int x, y, z;
        public final String block;

        public Change(int x, int y, int z, String block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Change)) return false;
            Change c = (Change) o;
            return x == c.x && y == c.y && z == c.z && block.equals(c.block);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new Object[]{x, y, z, block});
        }

        @Override
        public String toString() {
            return block + " at " + x + " " + y + " " + z;
        }
    }

    public static final String AIR = "minecraft:air";

    /** Blocks the crosshair passes through. */
    private static final Set<String> PASSABLE = new HashSet<>(Arrays.asList(
            AIR, "minecraft:cave_air", "minecraft:void_air", "minecraft:water", "minecraft:lava",
            "minecraft:bubble_column", "minecraft:light"));

    /** Blocks that a placed block replaces instead of attaching to. */
    private static final Set<String> REPLACEABLE = new HashSet<>(Arrays.asList(
            "minecraft:grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
            "minecraft:dead_bush", "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:snow",
            "minecraft:vine", "minecraft:fire", "minecraft:soul_fire", "minecraft:nether_sprouts",
            "minecraft:warped_roots", "minecraft:crimson_roots", "minecraft:structure_void"));

    private BlockInference() {
    }

    public static boolean isPassable(String block) {
        return block == null || PASSABLE.contains(block);
    }

    /** Which block of type {@code block} the ghost broke, or null if none is in reach along its look. */
    public static Change inferMined(GhostFrame f, String block, WorldView world) {
        Hit first = null;
        for (Hit h : new Ray(f)) {
            String at = world.blockAt(h.x, h.y, h.z);
            if (block.equals(at)) return new Change(h.x, h.y, h.z, AIR);
            if (first == null && !isPassable(at)) first = h;
        }
        // The ghost is a tick or two off what the client saw; look right around the first block hit.
        if (first != null) {
            for (int dy : new int[]{0, -1, 1}) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (block.equals(world.blockAt(first.x + dx, first.y + dy, first.z + dz))) {
                            return new Change(first.x + dx, first.y + dy, first.z + dz, AIR);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Where the ghost placed {@code block}, or null if it wasn't looking at a surface in reach. */
    public static Change inferPlaced(GhostFrame f, String block, WorldView world) {
        Hit prev = null;
        for (Hit h : new Ray(f)) {
            String at = world.blockAt(h.x, h.y, h.z);
            if (isPassable(at)) {
                prev = h;
                continue;
            }
            if (REPLACEABLE.contains(at)) return new Change(h.x, h.y, h.z, block);
            if (prev == null) return null;
            return new Change(prev.x, prev.y, prev.z, block);
        }
        return null;
    }

    static final class Hit {
        final int x, y, z;

        Hit(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Voxels crossed by the look ray, in order (Amanatides-Woo traversal). */
    static final class Ray implements Iterable<Hit> {
        private final double ox, oy, oz, dx, dy, dz;

        Ray(GhostFrame f) {
            double[] d = f.look();
            ox = f.x;
            oy = f.y + f.eyeHeight();
            oz = f.z;
            dx = d[0];
            dy = d[1];
            dz = d[2];
        }

        @Override
        public java.util.Iterator<Hit> iterator() {
            return new java.util.Iterator<Hit>() {
                int x = floor(ox), y = floor(oy), z = floor(oz);
                final int sx = (int) Math.signum(dx), sy = (int) Math.signum(dy), sz = (int) Math.signum(dz);
                double tx = boundary(ox, dx), ty = boundary(oy, dy), tz = boundary(oz, dz);
                final double ddx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
                final double ddy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
                final double ddz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
                double t = 0;
                boolean first = true;

                @Override
                public boolean hasNext() {
                    return first || Math.min(tx, Math.min(ty, tz)) <= REACH;
                }

                @Override
                public Hit next() {
                    if (first) {
                        first = false;
                        return new Hit(x, y, z);
                    }
                    if (tx <= ty && tx <= tz) {
                        x += sx;
                        t = tx;
                        tx += ddx;
                    } else if (ty <= tz) {
                        y += sy;
                        t = ty;
                        ty += ddy;
                    } else {
                        z += sz;
                        t = tz;
                        tz += ddz;
                    }
                    return new Hit(x, y, z);
                }
            };
        }

        private static int floor(double v) {
            return (int) Math.floor(v);
        }

        private static double boundary(double o, double d) {
            if (d == 0) return Double.POSITIVE_INFINITY;
            double cell = Math.floor(o);
            return d > 0 ? (cell + 1 - o) / d : (o - cell) / -d;
        }
    }
}
