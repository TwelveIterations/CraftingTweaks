package net.blay09.mods.craftingtweaks.api.impl;

import net.blay09.mods.craftingtweaks.api.CraftingGrid;
import net.blay09.mods.craftingtweaks.api.GridRotateHandler;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public class DefaultGridRotateHandler implements GridRotateHandler<AbstractContainerMenu> {
    protected boolean ignoresSlotId(int slotId) {
        return slotId == 4;
    }

    protected int rotateSlotId(int slotId, boolean counterClockwise) {
        if (!counterClockwise) {
            switch (slotId) {
                case 0:
                    return 1;
                case 1:
                    return 2;
                case 2:
                    return 5;
                case 5:
                    return 8;
                case 8:
                    return 7;
                case 7:
                    return 6;
                case 6:
                    return 3;
                case 3:
                    return 0;
            }
        } else {
            switch (slotId) {
                case 0:
                    return 3;
                case 1:
                    return 0;
                case 2:
                    return 1;
                case 3:
                    return 6;
                case 5:
                    return 2;
                case 6:
                    return 7;
                case 7:
                    return 8;
                case 8:
                    return 5;
            }
        }
        return 0;
    }

    @Override
    public void rotateGrid(CraftingGrid grid, Player player, AbstractContainerMenu menu, boolean reverse) {
        Container craftMatrix = grid.getCraftingMatrix(player, menu);
        if (craftMatrix == null) {
            return;
        }

        int start = grid.getGridStartSlot(player, menu);
        int size = grid.getGridSize(player, menu);
        Container matrixClone = new SimpleContainer(size);
        for (int i = 0; i < size; i++) {
            matrixClone.setItem(i, menu.getSlot(start + i).getItem().copy());
        }

        for (int i = 0; i < size; i++) {
            if (ignoresSlotId(i)) {
                continue;
            }

            Slot slot = menu.getSlot(start + rotateSlotId(i, reverse));
            slot.set(matrixClone.getItem(i));
        }

        menu.broadcastChanges();
    }
}
