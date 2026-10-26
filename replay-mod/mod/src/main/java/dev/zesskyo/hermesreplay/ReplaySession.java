package dev.zesskyo.hermesreplay;

import dev.zesskyo.hermesreplay.core.GhostFrame;
import dev.zesskyo.hermesreplay.core.ItemData;
import dev.zesskyo.hermesreplay.core.Replay;
import dev.zesskyo.hermesreplay.core.ReplayClock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One loaded replay. The clock, ghost and camera live on the client thread; block edits and
 * teleporting the viewer run on the integrated server thread.
 */
final class ReplaySession {
    /** Block edits applied per server tick while catching up after a seek. */
    private static final int EDIT_BUDGET_PER_TICK = 400;
    /** How far the viewer may drift from the runner before being pulled along to keep chunks loaded. */
    private static final double FOLLOW_SLACK = 24;

    final String name;
    final Replay replay;
    final ReplayClock clock;
    private final GhostPlayer ghost;
    private final WorldEdits edits;
    private final AtomicBoolean serverBusy = new AtomicBoolean();
    private volatile boolean editsCaughtUp = true;
    private boolean follow;
    private long lastShown = -1;

    ReplaySession(String name, Replay replay) {
        this.name = name;
        this.replay = replay;
        this.clock = new ReplayClock(replay.duration());
        this.ghost = new GhostPlayer(replay.log.playerName);
        this.edits = new WorldEdits(replay);
        clock.setPaused(true);
    }

    boolean follow() {
        return follow;
    }

    void setFollow(boolean follow) {
        this.follow = follow;
        if (!follow) MinecraftClient.getInstance().setCameraEntity(MinecraftClient.getInstance().player);
    }

    boolean editsCaughtUp() {
        return editsCaughtUp;
    }

    String editStats() {
        return edits.stats();
    }

    void seekBy(long ms) {
        clock.seek(clock.position() + ms);
    }

    /** Called every client tick (50 ms). */
    void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        clock.advance(50);
        long play = clock.position();
        GhostFrame f = replay.ghostAt(play);
        String dim = replay.dimensionAt(play);
        boolean sameWorld = client.world.getRegistryKey() == Dimensions.key(dim);
        boolean jumped = lastShown < 0 || Math.abs(play - lastShown) > 1000;
        ItemData[] inventory = replay.inventoryAt(play);
        ghost.update(client.world, sameWorld && f.has(GhostFrame.FLAG_ALIVE), f, inventory, jumped);
        lastShown = play;

        if (follow && ghost.entity() != null) {
            client.setCameraEntity(ghost.entity());
        } else if (client.getCameraEntity() != client.player) {
            client.setCameraEntity(client.player);
        }

        MinecraftServer server = client.getServer();
        if (server == null) return;
        boolean pull = follow && (!sameWorld || client.player.squaredDistanceTo(f.x, f.y, f.z) > FOLLOW_SLACK * FOLLOW_SLACK);
        // One task in flight at a time so a slow seek doesn't pile up work on the server.
        if (!serverBusy.compareAndSet(false, true)) return;
        java.util.UUID viewer = client.player.getUuid();
        server.execute(() -> {
            try {
                editsCaughtUp = edits.moveTo(server, play, EDIT_BUDGET_PER_TICK);
                if (pull) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(viewer);
                    ServerWorld w = server.getWorld(Dimensions.key(dim));
                    if (p != null && w != null) p.teleport(w, f.x, f.y + 1, f.z, f.yaw, f.pitch);
                }
            } finally {
                serverBusy.set(false);
            }
        });
    }

    /** Removes the ghost and restores the terrain. */
    void close(MinecraftClient client) {
        ghost.remove();
        if (client.player != null) client.setCameraEntity(client.player);
        MinecraftServer server = client.getServer();
        if (server != null) server.execute(edits::undoAll);
    }
}
