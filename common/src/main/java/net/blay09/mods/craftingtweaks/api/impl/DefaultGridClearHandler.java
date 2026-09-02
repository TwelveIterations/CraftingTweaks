package net.blay09.mods.craftingtweaks.api.impl;

import net.blay09.mods.craftingtweaks.api.CraftingGrid;
import net.blay09.mods.craftingtweaks.api.GridClearHandler;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class DefaultGridClearHandler implements GridClearHandler<AbstractContainerMenu> {

    private boolean phantomItems;

    @Override
    public void clearGrid(CraftingGrid grid, Player player, AbstractContainerMenu menu, boolean forced) {
        Container craftMatrix = grid.getCraftingMatrix(player, menu);
        if (craftMatrix == null) {
            return;
        }

        int start = grid.getGridStartSlot(player, menu);
        int size = grid.getGridSize(player, menu);
        for (int i = start; i < start + size; i++) {
            int slotIndex = menu.getSlot(i).getContainerSlot();
            if (phantomItems) {
                craftMatrix.setItem(slotIndex, ItemStack.EMPTY);
            } else {
                ItemStack itemStack = craftMatrix.getItem(slotIndex);
                if (!itemStack.isEmpty()) {
                    ItemStack returnStack = itemStack.copy();
                    player.getInventory().add(returnStack);
                    craftMatrix.setItem(slotIndex, returnStack.getCount() == 0 ? ItemStack.EMPTY : returnStack);
                    if (returnStack.getCount() > 0 && forced) {
                        player.drop(returnStack, false, Prediction.SERVER_ONLY);
                        craftMatrix.setItem(slotIndex, ItemStack.EMPTY);
                    }
                }
            }
        }

        menu.broadcastChanges();
    }

    public void setPhantomItems(boolean phantomItems) {
        this.phantomItems = phantomItems;
    }
}
