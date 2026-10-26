package dev.zesskyo.hermesreplay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

/**
 * /replay commands. Fabric API for 1.16.1 has no client-side commands, so these are registered on
 * the integrated server and hand the work to the client thread.
 */
final class ReplayCommands {
    private static final SimpleCommandExceptionType BAD_TIME =
            new SimpleCommandExceptionType(new LiteralText("Use a time like 12:34 or 1:02:03"));

    private ReplayCommands() {
    }

    static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("replay")
                .then(CommandManager.literal("list").executes(c -> client(c, () -> HermesReplayClient.list())))
                .then(CommandManager.literal("load")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(c -> client(c, () -> HermesReplayClient.load(StringArgumentType.getString(c, "name"))))))
                .then(CommandManager.literal("play").executes(c -> client(c, () -> HermesReplayClient.setPaused(false))))
                .then(CommandManager.literal("pause").executes(c -> client(c, () -> HermesReplayClient.setPaused(true))))
                .then(CommandManager.literal("speed")
                        .then(CommandManager.argument("x", DoubleArgumentType.doubleArg(0.25, 64))
                                .executes(c -> client(c, () -> HermesReplayClient.setSpeed(DoubleArgumentType.getDouble(c, "x"))))))
                .then(CommandManager.literal("seek")
                        .then(CommandManager.argument("time", StringArgumentType.word())
                                .executes(c -> {
                                    long ms = parseTime(StringArgumentType.getString(c, "time"));
                                    return client(c, () -> HermesReplayClient.seekIgt(ms));
                                })))
                .then(CommandManager.literal("follow").executes(c -> client(c, HermesReplayClient::toggleFollow)))
                .then(CommandManager.literal("stop").executes(c -> client(c, HermesReplayClient::stop))));
    }

    private static int client(CommandContext<ServerCommandSource> c, Runnable action) {
        MinecraftClient.getInstance().execute(action);
        return 1;
    }

    /** "45", "12:34" or "1:02:03" (hours:minutes:seconds) to milliseconds. */
    static long parseTime(String s) throws CommandSyntaxException {
        String[] parts = s.split(":");
        if (parts.length > 3) throw BAD_TIME.create();
        long total = 0;
        try {
            for (String p : parts) total = total * 60 + Long.parseLong(p);
        } catch (NumberFormatException e) {
            throw BAD_TIME.create();
        }
        return total * 1000;
    }
}
