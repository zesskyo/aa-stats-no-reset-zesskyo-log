package dev.zesskyo.hermesreplay;

import dev.zesskyo.hermesreplay.core.Replay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Plays back a run from its Hermes files. Put {@code <name>.ghost} and {@code <name>.log} in
 * {@code .minecraft/hermes-replays/}, make a world with the run's seed, then {@code /replay load <name>}.
 */
public final class HermesReplayClient implements ClientModInitializer {
    private static final double[] SPEEDS = {0.25, 0.5, 1, 2, 4, 8, 16, 32, 64};

    private static ReplaySession session;
    private static KeyBinding pause, slower, faster, back, forward, follow;

    @Override
    public void onInitializeClient() {
        pause = key("pause", GLFW.GLFW_KEY_P);
        slower = key("slower", GLFW.GLFW_KEY_LEFT_BRACKET);
        faster = key("faster", GLFW.GLFW_KEY_RIGHT_BRACKET);
        back = key("back", GLFW.GLFW_KEY_LEFT);
        forward = key("forward", GLFW.GLFW_KEY_RIGHT);
        follow = key("follow", GLFW.GLFW_KEY_V);

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            if (!dedicated) ReplayCommands.register(dispatcher);
        });
        ClientTickEvents.END_CLIENT_TICK.register(HermesReplayClient::tick);
        HudRenderCallback.EVENT.register((matrices, tickDelta) -> {
            if (session != null) ReplayHud.render(MinecraftClient.getInstance(), matrices, session);
        });
    }

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hermesreplay." + name, InputUtil.Type.KEYSYM, code, "category.hermesreplay"));
    }

    private static void tick(MinecraftClient client) {
        if (session == null) return;
        if (client.world == null) {
            // Left the world: nothing to restore any more.
            session = null;
            return;
        }
        while (pause.wasPressed()) setPaused(!session.clock.paused());
        while (slower.wasPressed()) stepSpeed(-1);
        while (faster.wasPressed()) stepSpeed(1);
        while (back.wasPressed()) session.seekBy(-10_000);
        while (forward.wasPressed()) session.seekBy(10_000);
        while (follow.wasPressed()) toggleFollow();
        session.tick(client);
    }

    static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("hermes-replays");
    }

    static void list() {
        List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (Stream<Path> files = Files.list(folder())) {
                files.forEach(p -> {
                    String f = p.getFileName().toString();
                    if (f.endsWith(".ghost") && Files.exists(p.resolveSibling(f.substring(0, f.length() - 6) + ".log"))) {
                        names.add(f.substring(0, f.length() - 6));
                    }
                });
            }
        } catch (IOException e) {
            say("Couldn't read " + folder() + ": " + e.getMessage());
            return;
        }
        Collections.sort(names);
        say(names.isEmpty()
                ? "No replays yet. Put <name>.ghost and <name>.log in " + folder()
                : "Replays: " + String.join(", ", names));
    }

    static void load(String name) {
        Path ghost = folder().resolve(name + ".ghost"), log = folder().resolve(name + ".log");
        if (!Files.exists(ghost) || !Files.exists(log)) {
            say("Need both " + name + ".ghost and " + name + ".log in " + folder());
            return;
        }
        say("Loading " + name + "...");
        // A 4-hour run is ~25 MB of files; read them off the render thread.
        Thread t = new Thread(() -> {
            try {
                Replay replay = Replay.load(ghost, log);
                MinecraftClient.getInstance().execute(() -> start(name, replay));
            } catch (IOException | RuntimeException e) {
                MinecraftClient.getInstance().execute(() -> say("Couldn't load " + name + ": " + e.getMessage()));
            }
        }, "Hermes Replay loader");
        t.setDaemon(true);
        t.start();
    }

    private static void start(String name, Replay replay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (session != null) session.close(client);
        session = new ReplaySession(name, replay);
        MinecraftServer server = client.getServer();
        Long seed = replay.log.seed;
        if (server != null && seed != null && server.getOverworld().getSeed() != seed) {
            say("This world's seed isn't the run's seed (" + seed + "). Terrain will be wrong; make a new world with that seed.");
        } else if (seed == null) {
            say("This run's log has no seed (type /seed at the end of a run to record it). Make sure this world uses the right one.");
        }
        if (server != null && client.player != null) {
            java.util.UUID viewer = client.player.getUuid();
            server.execute(() -> {
                server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(viewer);
                if (p != null) p.setGameMode(GameMode.SPECTATOR);
            });
        }
        say(String.format("Loaded %s: %s by %s, %s long. P to play, [ ] speed, arrows skip, V to follow.",
                name, replay.log.mcVersion, replay.log.playerName, ReplayHud.time(replay.duration())));
        session.setFollow(true);
        setPaused(false);
    }

    static void setPaused(boolean paused) {
        if (session != null) session.clock.setPaused(paused);
    }

    static void setSpeed(double speed) {
        if (session != null) session.clock.setSpeed(speed);
    }

    private static void stepSpeed(int dir) {
        double now = session.clock.speed();
        int i = 0;
        while (i < SPEEDS.length - 1 && SPEEDS[i] < now) i++;
        setSpeed(SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, i + dir))]);
    }

    static void seekIgt(long igt) {
        if (session != null) session.clock.seek(session.replay.playTimeAtIgt(igt));
    }

    static void toggleFollow() {
        if (session != null) session.setFollow(!session.follow());
    }

    static void stop() {
        if (session == null) return;
        say("Stopped " + session.name + " (" + session.editStats() + "). Terrain restored.");
        session.close(MinecraftClient.getInstance());
        session = null;
    }

    private static void say(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(new LiteralText("[Replay] " + message), false);
    }
}
