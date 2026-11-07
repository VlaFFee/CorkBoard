package com.corkboard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** «Блок-доска»: ставится на стену и создаёт доску. Shift + ПКМ в воздухе — сменить размер (1x1 … 5x5). */
public class BoardItem extends Item {
    public BoardItem(Properties props) {
        super(props);
    }

    public static ItemStack withSize(int w, int h) {
        ItemStack s = new ItemStack(CorkboardMod.BOARD_ITEM.get());
        s.getOrCreateTag().putInt("W", w);
        s.getOrCreateTag().putInt("H", h);
        return s;
    }

    public static int sizeW(ItemStack s) { return s.hasTag() && s.getTag().contains("W") ? clamp(s.getTag().getInt("W")) : 2; }
    public static int sizeH(ItemStack s) { return s.hasTag() && s.getTag().contains("H") ? clamp(s.getTag().getInt("H")) : 2; }
    private static int clamp(int v) { return Math.max(1, Math.min(5, v)); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) return InteractionResult.FAIL;
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos().relative(face);
        if (player != null && !player.mayUseItemAt(pos, face, stack)) return InteractionResult.FAIL;

        Level level = ctx.getLevel();
        BoardEntity board = new BoardEntity(level, pos, face, sizeW(stack), sizeH(stack));
        if (!board.survives()) return InteractionResult.CONSUME;

        if (!level.isClientSide) {
            board.playPlacementSound();
            level.addFreshEntity(board);
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        int w = sizeW(stack), h = sizeH(stack);
        // 1x1 -> 2x2 -> 3x3 -> 4x4 -> 5x5 -> 1x1
        int n = w >= 5 ? 1 : w + 1;
        stack.getOrCreateTag().putInt("W", n);
        stack.getOrCreateTag().putInt("H", n);
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("item.corkboard.board.size", n, n), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).append(" " + sizeW(stack) + "×" + sizeH(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.corkboard.board.tip1").withStyle(net.minecraft.ChatFormatting.GRAY));
        tip.add(Component.translatable("item.corkboard.board.tip2").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
