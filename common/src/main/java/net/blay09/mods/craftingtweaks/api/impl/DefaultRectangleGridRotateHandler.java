package net.blay09.mods.craftingtweaks.api.impl;

import net.blay09.mods.craftingtweaks.api.CraftingGrid;
import net.blay09.mods.craftingtweaks.api.GridRotateHandler;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public class DefaultRectangleGridRotateHandler implements GridRotateHandler<AbstractContainerMenu> {

    /**
     * Calculates the offset for rotating a slot in a grid of given size, either clockwise or counterclockwise
     * @param slot index of the slot to be rotated
     * @param width width of the grid
     * @param height height of the grid
     * @param clockwise direction of rotation
     * @return index offset
     */
    private int rotateRectangularGrid(int slot, int width, int height, boolean clockwise) {
        // offset constants
        final int stay = 0;
        final int moveRight = 1;
        final int moveLeft = -1;
        final int moveUp = -width;
        final int moveDown = width;
        // cartesian coordinates
        int x = slot % width;
        int y = slot / width;

        // smallest side length
        int smallestSide = Math.min(width, height);
        int hss = smallestSide / 2; // half smallest side length
        int td = y; // top edge distance
        int bd = height - 1 - y; // bottom edge distance
        int ld = x; // left edge distance
        int rd = width - 1 - x; // right edge distance

        int ved = Math.min(td, bd); // vertical edge distance
        int hed = Math.min(rd, ld); // horizontal edge distance
        int civcd = Math.max(hss - ved, 0); // clamped inverse vertical center distance
        int cihcd = Math.max(hss - hed, 0); // clamped inverse horizontal center distance

        // is on corner diagonal and not in center
        boolean diagonal = Math.abs(civcd) - Math.abs(cihcd) == 0 && civcd != 0 && cihcd != 0;

        // on which axis quadrant
        boolean verticalQuadrant = civcd - cihcd > 0;
        boolean horizontalQuadrant = cihcd - civcd > 0;
        // is on left half of grid
        boolean leftHalf = ld < rd;
        // is on top half of grid
        boolean topHalf = td < bd;
        // quadrant masks
        boolean q1 = verticalQuadrant && topHalf;
        boolean q2 = horizontalQuadrant && !leftHalf;
        boolean q3 = verticalQuadrant && !topHalf;
        boolean q4 = horizontalQuadrant && leftHalf;
        // diagonal masks
        boolean d1 = diagonal && topHalf && leftHalf;
        boolean d2 = diagonal && topHalf && !leftHalf;
        boolean d3 = diagonal && !topHalf && !leftHalf;
        boolean d4 = diagonal && !topHalf && leftHalf;

        if (clockwise) {
            if (q1 || d1) {
                return moveRight;
            } else if (q3 || d3) {
                return moveLeft;
            } else if (q2 || d2) {
                return moveDown;
            } else if (q4 || d4) {
                return moveUp;
            } else {
                return stay;
            }
        } else {
            if (q1 || d2) {
                return moveLeft;
            } else if (q3 || d4) {
                return moveRight;
            } else if (q2 || d3) {
                return moveUp;
            } else if (q4 || d1) {
                return moveDown;
            } else {
                return stay;
            }
        }
    }

    @Override
    public void rotateGrid(CraftingGrid grid, Player player, AbstractContainerMenu menu, boolean reverse) {
        Container craftMatrix = grid.getCraftingMatrix(player, menu);
        if (craftMatrix == null) {
            return;
        }

        int start = grid.getGridStartSlot(player, menu);
        int size = grid.getGridSize(player, menu);
        // Assumption: The grid is square
        int gridWidth = (int)Math.sqrt(size);
        int gridHeight = (int)Math.sqrt(size);
        Container matrixClone = new SimpleContainer(size);
        for (int i = 0; i < size; i++) {
            matrixClone.setItem(i, menu.getSlot(start + i).getItem().copy());
        }

        for (int i = 0; i < size; i++) {
            Slot slot = menu.getSlot(start + i + rotateRectangularGrid(i, gridWidth, gridHeight, !reverse));
            slot.set(matrixClone.getItem(i));
        }

        menu.broadcastChanges();
    }
}
