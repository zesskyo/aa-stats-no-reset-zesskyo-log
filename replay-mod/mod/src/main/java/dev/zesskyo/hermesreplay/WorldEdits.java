package dev.zesskyo.hermesreplay;

import dev.zesskyo.hermesreplay.core.BlockInference;
import dev.zesskyo.hermesreplay.core.EditCursor;
import dev.zesskyo.hermesreplay.core.GhostFrame;
import dev.zesskyo.hermesreplay.core.PlayLog;
import dev.zesskyo.hermesreplay.core.Replay;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

/**
 * Rebuilds the runner's block changes in the replay world from "mined" and "used" stats.
 * Runs on the integrated server thread only. Every change keeps the block it replaced, so
 * seeking backwards puts the terrain back.
 */
final class WorldEdits {
    /** No neighbour updates: placed sand doesn't fall and placed water doesn't flow. */
    private static final int SET_FLAGS = 2 | 16;
    /** Ticks to look back from a stat for the frame where the player clicked. */
    private static final long[] LOOKBACK_MS = {0, 50, 100, 150, 250};

    private static final class Undo {
        final ServerWorld world;
        final BlockPos pos;
        final BlockState previous;

        Undo(ServerWorld world, BlockPos pos, BlockState previous) {
            this.world = world;
            this.pos = pos;
            this.previous = previous;
        }
    }

    private final Replay replay;
    private final EditCursor<Undo> cursor;
    private int guessed, missed;

    WorldEdits(Replay replay) {
        this.replay = replay;
        this.cursor = new EditCursor<>(replay);
    }

    /** Brings the world to {@code play}; returns true once caught up. Server thread only. */
    boolean moveTo(MinecraftServer server, long play, int budget) {
        return cursor.moveTo(play, budget, edit -> apply(server, edit), WorldEdits::undo);
    }

    /** Puts every changed block back, e.g. when the replay is closed. Server thread only. */
    void undoAll() {
        cursor.undoAll(WorldEdits::undo);
    }

    private static void undo(Undo u) {
        u.world.setBlockState(u.pos, u.previous, SET_FLAGS);
    }

    String stats() {
        return guessed + " block changes placed, " + missed + " couldn't be placed";
    }

    private Undo apply(MinecraftServer server, PlayLog.StatChange edit) {
        long play = replay.playTime(edit.time);
        ServerWorld world = server.getWorld(Dimensions.key(replay.dimensionAt(play)));
        if (world == null) return null;
        Block target = null;
        if ("used".equals(edit.category)) {
            Identifier id = Identifier.tryParse(edit.key);
            Item item = id == null ? null : Registry.ITEM.get(id);
            target = item == null ? Blocks.AIR : Block.getBlockFromItem(item);
            // Food, tools, pearls and so on are "used" too, but don't place anything.
            if (target == Blocks.AIR) return null;
        }
        BlockInference.WorldView view = (x, y, z) -> Registry.BLOCK.getId(world.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
        for (long back : LOOKBACK_MS) {
            GhostFrame f = replay.ghostAt(Math.max(0, play - back));
            BlockInference.Change c = target == null
                    ? BlockInference.inferMined(f, edit.key, view)
                    : BlockInference.inferPlaced(f, Registry.BLOCK.getId(target).toString(), view);
            if (c == null) continue;
            BlockPos pos = new BlockPos(c.x, c.y, c.z);
            BlockState previous = world.getBlockState(pos);
            BlockState next = target == null ? Blocks.AIR.getDefaultState() : target.getDefaultState();
            world.setBlockState(pos, next, SET_FLAGS);
            guessed++;
            return new Undo(world, pos, previous);
        }
        missed++;
        return null;
    }
}
