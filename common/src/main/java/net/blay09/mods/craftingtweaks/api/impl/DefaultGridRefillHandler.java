package net.blay09.mods.craftingtweaks.api.impl;

import net.blay09.mods.craftingtweaks.CraftingTweaks;
import net.blay09.mods.craftingtweaks.api.CraftingGrid;
import net.blay09.mods.craftingtweaks.api.CraftingTweaksAPI;
import net.blay09.mods.craftingtweaks.api.GridRefillHandler;
import net.blay09.mods.craftingtweaks.crafting.ContainerIngredientProvider;
import net.blay09.mods.craftingtweaks.crafting.CraftingContext;
import net.blay09.mods.craftingtweaks.crafting.IngredientToken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.List;

public class DefaultGridRefillHandler implements GridRefillHandler<AbstractContainerMenu> {
    @Override
    @SuppressWarnings("unchecked")
    public void refillRecipe(CraftingGrid grid, Player player, AbstractContainerMenu menu, RecipeHolder<?> recipeHolder, boolean stack) {
        grid.clearHandler().clearGrid(grid, player, menu, true);

        final var craftMatrix = grid.getCraftingMatrix(player, menu);
        if (craftMatrix == null || craftMatrix.getContainerSize() == 0) {
            return;
        }

        final var gridSize = grid.getGridSize(player, menu);
        final var gridWidth = grid.getGridWidth(player, menu);
        final var gridSlots = new HashMap<Integer, Slot>();
        final var start = grid.getGridStartSlot(player, menu);
        for (int i = start; i < start + gridSize; i++) {
            final var slot = menu.getSlot(i);
            if (slot.container == craftMatrix) {
                gridSlots.put(slot.getContainerSlot(), slot);
            }
        }

        final var recipe = recipeHolder.value();
        final var context = new CraftingContext(List.of(new ContainerIngredientProvider(player.getInventory())));
        final var operation = context.createOperation((RecipeHolder<Recipe<?>>) recipeHolder).prepare();
        if (!operation.canCraft()) {
            return;
        }

        final var matrixMapper = CraftingTweaksAPI.getRecipeMapper(recipe.getClass());
        if (matrixMapper.getIngredients(recipe).size() > gridSize) {
            return;
        }

        int operations = 0;
        outer:
        do {
            final var ingredientTokens = operation.getIngredientTokens();

            final var matrixDiff = new HashMap<Slot, IngredientToken>();
            for (int i = 0; i < ingredientTokens.size(); i++) {
                final var ingredientToken = ingredientTokens.get(i);
                var matrixSlot = matrixMapper.mapToMatrixSlot(recipe, gridWidth, i);
                // Abort early if the mapped slot doesn't exist in the matrix
                if (matrixSlot >= craftMatrix.getContainerSize()) {
                    return;
                }
                if (matrixSlot != -1) {
                    final var itemStack = ingredientToken.peek();
                    final var slot = gridSlots.get(matrixSlot);
                    if (slot == null || !slot.mayPlace(itemStack)) {
                        return;
                    }
                    final var slotStack = slot.getItem();
                    if (slotStack.getCount() + itemStack.getCount() > slot.getMaxStackSize(itemStack)) {
                        break outer;
                    }
                    if (!slotStack.isEmpty()) {
                        if (slotStack.getCount() >= slotStack.getMaxStackSize()) {
                            break outer;
                        } else if (!slotStack.isStackable()) {
                            break outer;
                        } else if (!ItemStack.isSameItemSameComponents(slotStack, itemStack)) {
                            break outer;
                        }
                    }

                    matrixDiff.put(slot, ingredientToken);
                }
            }
            matrixDiff.forEach((slot, ingredientToken) -> {
                final var slotStack = slot.getItem();
                final var itemStack = ingredientToken.consume();
                slot.set(itemStack.copyWithCount(itemStack.getCount() + slotStack.getCount()));
            });
            operations++;
            if (operations > 64) {
                CraftingTweaks.logger.warn("Something went wrong trying to refill recipe. Too many iterations. Recipe: {}", recipeHolder.id().identifier());
                break;
            }
            if (!stack || matrixDiff.isEmpty()) {
                break;
            }
        } while (operation.prepare().canCraft());

        menu.broadcastChanges();
    }
}
