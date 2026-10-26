package dev.zesskyo.hermesreplay;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.zesskyo.hermesreplay.core.ItemData;
import net.minecraft.datafixer.NbtOps;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/** Turns Hermes' logged items back into stacks so the ghost holds and wears the right things. */
final class ItemStacks {
    private ItemStacks() {
    }

    static ItemStack of(ItemData data) {
        if (data == null) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(data.id);
        Item item = id == null ? Items.AIR : Registry.ITEM.get(id);
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, data.count));
        if (data.tag != null) {
            // Hermes writes NBT as JSON; numbers come back as doubles, which is fine for
            // enchantment glint, damage bars and dyed leather.
            Tag tag = Dynamic.convert(JsonOps.INSTANCE, NbtOps.INSTANCE, data.tag);
            if (tag instanceof CompoundTag) stack.setTag((CompoundTag) tag);
        }
        return stack;
    }
}
