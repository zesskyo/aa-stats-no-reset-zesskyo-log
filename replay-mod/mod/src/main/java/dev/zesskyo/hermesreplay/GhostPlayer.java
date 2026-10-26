package dev.zesskyo.hermesreplay;

import com.mojang.authlib.GameProfile;
import dev.zesskyo.hermesreplay.core.GhostFrame;
import dev.zesskyo.hermesreplay.core.ItemData;
import dev.zesskyo.hermesreplay.core.Replay;
import dev.zesskyo.hermesreplay.mixin.EntityAccessor;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Hand;

import java.util.UUID;

/**
 * The runner, drawn as a client-side player entity that only exists in the viewer's world.
 * It's re-created whenever the viewer's world changes (dimension switch, rejoin).
 */
final class GhostPlayer {
    /** Negative so it can never collide with an id the server hands out. */
    private static final int ENTITY_ID = -0x4E5;
    private static final int FLAG_FALL_FLYING = 7;

    private final GameProfile profile;
    private OtherClientPlayerEntity entity;
    private ClientWorld world;
    private boolean wasSwinging;

    GhostPlayer(String name) {
        // A fresh UUID: reusing the runner's would clash with the viewer when they are the same person.
        this.profile = new GameProfile(UUID.randomUUID(), name == null ? "Runner" : name);
    }

    OtherClientPlayerEntity entity() {
        return entity;
    }

    /** Shows the ghost in {@code world} at {@code f}, or hides it when {@code visible} is false. */
    void update(ClientWorld world, boolean visible, GhostFrame f, ItemData[] inventory, boolean jumped) {
        if (!visible || world == null) {
            remove();
            return;
        }
        if (entity == null || this.world != world) {
            remove();
            this.world = world;
            entity = new OtherClientPlayerEntity(world, profile);
            entity.refreshPositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch);
            world.addEntity(ENTITY_ID, entity);
            jumped = true;
        }
        if (jumped) {
            entity.refreshPositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch);
            entity.updateTrackedPositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch, 1, true);
            entity.setHeadYaw(f.yaw);
        } else {
            entity.updateTrackedPositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch, 2, true);
        }
        entity.updateTrackedHeadRotation(f.yaw, 2);
        entity.setHealth(Math.max(1, f.health));
        entity.setSneaking(f.has(GhostFrame.FLAG_SNEAKING));
        entity.setSprinting(f.has(GhostFrame.FLAG_SPRINTING));
        entity.setSwimming(f.has(GhostFrame.FLAG_SWIMMING));
        ((EntityAccessor) entity).hermesreplay$setFlag(FLAG_FALL_FLYING, f.has(GhostFrame.FLAG_FALL_FLYING));

        boolean swinging = f.has(GhostFrame.FLAG_SWINGING);
        if (swinging && (!wasSwinging || !entity.handSwinging)) entity.swingHand(Hand.MAIN_HAND);
        wasSwinging = swinging;

        entity.inventory.selectedSlot = f.hotbarSlot;
        entity.equipStack(EquipmentSlot.MAINHAND, ItemStacks.of(inventory[f.hotbarSlot]));
        entity.equipStack(EquipmentSlot.OFFHAND, ItemStacks.of(inventory[Replay.SLOT_OFFHAND]));
        entity.equipStack(EquipmentSlot.FEET, ItemStacks.of(inventory[Replay.SLOT_BOOTS]));
        entity.equipStack(EquipmentSlot.LEGS, ItemStacks.of(inventory[Replay.SLOT_LEGS]));
        entity.equipStack(EquipmentSlot.CHEST, ItemStacks.of(inventory[Replay.SLOT_CHEST]));
        entity.equipStack(EquipmentSlot.HEAD, ItemStacks.of(inventory[Replay.SLOT_HELMET]));
    }

    void remove() {
        if (entity != null && world != null) world.removeEntity(ENTITY_ID);
        entity = null;
        world = null;
    }
}
