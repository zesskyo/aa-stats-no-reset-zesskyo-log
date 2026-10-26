package dev.zesskyo.hermesreplay;

import dev.zesskyo.hermesreplay.core.GhostFrame;
import dev.zesskyo.hermesreplay.core.ItemData;
import dev.zesskyo.hermesreplay.core.PlayLog;
import dev.zesskyo.hermesreplay.core.Replay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/** Timeline bar coloured by dimension, the run's IGT, speed, advancement count and the runner's hotbar. */
final class ReplayHud {
    private static final int BAR_HEIGHT = 5;
    private static final int TOTAL_ADVANCEMENTS = 80;

    private ReplayHud() {
    }

    static void render(MinecraftClient client, MatrixStack matrices, ReplaySession s) {
        if (client.options.hudHidden) return;
        TextRenderer text = client.textRenderer;
        Replay r = s.replay;
        int width = client.getWindow().getScaledWidth();
        int left = 10, right = width - 10, top = 6;
        long duration = Math.max(1, r.duration());
        long play = s.clock.position();

        DrawableHelper.fill(matrices, left - 1, top - 1, right + 1, top + BAR_HEIGHT + 1, 0x90000000);
        for (Replay.Segment seg : r.dimensionSegments()) {
            int x1 = left + (int) ((right - left) * seg.start / duration);
            int x2 = left + (int) ((right - left) * seg.end / duration);
            DrawableHelper.fill(matrices, x1, top, Math.max(x1 + 1, x2), top + BAR_HEIGHT, Dimensions.color(seg.dimension));
        }
        for (PlayLog.AdvancementProgress a : r.completions()) {
            int x = left + (int) ((right - left) * r.playTime(a.time) / duration);
            DrawableHelper.fill(matrices, x, top + BAR_HEIGHT, x + 1, top + BAR_HEIGHT + 2, 0xFFFFD040);
        }
        int head = left + (int) ((right - left) * play / duration);
        DrawableHelper.fill(matrices, head - 1, top - 2, head + 1, top + BAR_HEIGHT + 2, 0xFFFFFFFF);

        String state = s.clock.paused() ? "Paused" : "Playing";
        String speed = s.clock.speed() == Math.floor(s.clock.speed())
                ? String.valueOf((int) s.clock.speed()) : String.valueOf(s.clock.speed());
        String line = String.format("%s  IGT %s  %sx  %d/%d advancements%s%s",
                state, time(r.igtAt(play)), speed, r.advancementsDoneAt(play), TOTAL_ADVANCEMENTS,
                s.follow() ? "  following" : "", s.editsCaughtUp() ? "" : "  rebuilding terrain...");
        text.drawWithShadow(matrices, line, left, top + BAR_HEIGHT + 5, 0xFFFFFF);

        GhostFrame f = r.ghostAt(play);
        String where = String.format("%s  %.0f %.0f %.0f  %.1f hp", shortDim(r.dimensionAt(play)), f.x, f.y, f.z, f.health / 2);
        text.drawWithShadow(matrices, where, left, top + BAR_HEIGHT + 16, 0xC0C0C0);

        // The runner's hotbar, top right, with the selected slot outlined.
        ItemData[] inv = r.inventoryAt(play);
        int slotX = right - 9 * 18, slotY = top + BAR_HEIGHT + 5;
        for (int i = 0; i < 9; i++) {
            int x = slotX + i * 18;
            DrawableHelper.fill(matrices, x, slotY, x + 17, slotY + 17, i == f.hotbarSlot ? 0xC0FFFFFF : 0x80000000);
            ItemStack stack = ItemStacks.of(inv[i]);
            if (!stack.isEmpty()) {
                client.getItemRenderer().renderInGuiWithOverrides(stack, x + 1, slotY + 1);
                client.getItemRenderer().renderGuiItemOverlay(text, stack, x + 1, slotY + 1);
            }
        }
    }

    static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600
                ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    private static String shortDim(String id) {
        if ("minecraft:the_nether".equals(id)) return "Nether";
        if ("minecraft:the_end".equals(id)) return "End";
        return "Overworld";
    }
}
