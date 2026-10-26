package dev.zesskyo.hermesreplay;

import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

final class Dimensions {
    private Dimensions() {
    }

    static RegistryKey<World> key(String id) {
        if ("minecraft:the_nether".equals(id)) return World.NETHER;
        if ("minecraft:the_end".equals(id)) return World.END;
        return World.OVERWORLD;
    }

    /** Timeline colours: light blue, red and purple, like MCSR Ranked's replay bar. */
    static int color(String id) {
        if ("minecraft:the_nether".equals(id)) return 0xFFD04040;
        if ("minecraft:the_end".equals(id)) return 0xFFA060E0;
        return 0xFF60B0F0;
    }
}
