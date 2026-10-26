package dev.zesskyo.hermesreplay.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
    /** Entity flag 7 is elytra flight; there's no public setter for it. */
    @Invoker("setFlag")
    void hermesreplay$setFlag(int index, boolean value);
}
