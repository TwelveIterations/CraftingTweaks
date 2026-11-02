package net.blay09.mods.craftingtweaks.api.impl;

import net.blay09.mods.craftingtweaks.api.CraftingGrid;
import net.blay09.mods.craftingtweaks.api.GridTransferHandler;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class DefaultGridTransferHandler implements GridTransferHandler<AbstractContainerMenu> {

    @Override
    public ItemStack putIntoGrid(CraftingGrid grid, Player player, AbstractContainerMenu menu, int slotId, ItemStack itemStack) {
        Container craftMatrix = grid.getCraftingMatrix(player, menu);
        if (craftMatrix == null) {
            return itemStack;
        }

        int start = grid.getGridStartSlot(player, menu);
        int size = grid.getGridSize(player, menu);
        for (int i = start; i < start + size; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container == craftMatrix && slot.getContainerSlot() == slotId) {
                return slot.safeInsert(itemStack);
            }
        }
        return itemStack;
    }

    @Override
    public boolean transferIntoGrid(CraftingGrid grid, Player player, AbstractContainerMenu menu, Slot fromSlot) {
        if (grid.getCraftingMatrix(player, menu) == null) {
            return false;
        }

        int start = grid.getGridStartSlot(player, menu);
        int size = grid.getGridSize(player, menu);
        ItemStack itemStack = fromSlot.getItem();
        if (itemStack.isEmpty()) {
            return false;
        }

        int originalCount = itemStack.getCount();
        Slot firstEmptySlot = null;
        for (int i = start; i < start + size; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.hasItem()) {
                itemStack = slot.safeInsert(itemStack);
                if (itemStack.isEmpty()) {
                    return true;
                }
            } else if (firstEmptySlot == null && slot.mayPlace(itemStack) && slot.getMaxStackSize(itemStack) > 0) {
                firstEmptySlot = slot;
            }
        }

        if (!itemStack.isEmpty() && firstEmptySlot != null) {
            itemStack = firstEmptySlot.safeInsert(itemStack);
        }
        return itemStack.getCount() < originalCount;
    }

    @Override
    public boolean canTransferFrom(Player player, AbstractContainerMenu menu, Slot slot, CraftingGrid toGrid) {
        return slot.container == player.getInventory();
    }
}
