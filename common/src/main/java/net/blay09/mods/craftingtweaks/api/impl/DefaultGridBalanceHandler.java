package net.blay09.mods.craftingtweaks.api.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import net.blay09.mods.craftingtweaks.api.CraftingGrid;
import net.blay09.mods.craftingtweaks.api.GridBalanceHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public class DefaultGridBalanceHandler implements GridBalanceHandler<AbstractContainerMenu> {
    @Override
    public void balanceGrid(CraftingGrid grid, Player player, AbstractContainerMenu menu) {
        Container craftMatrix = grid.getCraftingMatrix(player, menu);
        if (craftMatrix == null) {
            return;
        }

        ArrayListMultimap<String, Slot> itemMap = ArrayListMultimap.create();
        Multiset<String> itemCount = HashMultiset.create();
        int start = grid.getGridStartSlot(player, menu);
        int size = grid.getGridSize(player, menu);
        for (int i = start; i < start + size; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack itemStack = slot.getItem();
            if (!itemStack.isEmpty() && itemStack.getMaxStackSize() > 1) {
                Identifier registryName = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
                String key = Objects.toString(registryName);

                final var componentsPatch = itemStack.getComponentsPatch();
                if (!componentsPatch.isEmpty()) {
                    key = key + "@" + componentsPatch;
                }
                itemMap.put(key, slot);
                itemCount.add(key, itemStack.getCount());
            }
        }

        for (String key : itemMap.keySet()) {
            List<Slot> balanceList = itemMap.get(key);
            int totalCount = itemCount.count(key);
            int countPerStack = totalCount / balanceList.size();
            int restCount = totalCount % balanceList.size();
            for (Slot slot : balanceList) {
                slot.set(slot.getItem().copyWithCount(countPerStack));
            }

            int idx = 0;
            while (restCount > 0) {
                Slot slot = balanceList.get(idx);
                ItemStack itemStack = slot.getItem();
                if (itemStack.getCount() < itemStack.getMaxStackSize()) {
                    slot.set(itemStack.copyWithCount(itemStack.getCount() + 1));
                    restCount--;
                }
                idx++;
                if (idx >= balanceList.size()) {
                    idx = 0;
                }
            }
        }

        menu.broadcastChanges();
    }

    @Override
    public void spreadGrid(CraftingGrid grid, Player player, AbstractContainerMenu menu) {
        Container craftMatrix = grid.getCraftingMatrix(player, menu);
        if (craftMatrix == null) {
            return;
        }

        while (true) {
            Slot biggestSlot = null;
            ItemStack biggestSlotStack = null;
            int biggestSlotSize = 1;
            int start = grid.getGridStartSlot(player, menu);
            int size = grid.getGridSize(player, menu);
            for (int i = start; i < start + size; i++) {
                ItemStack itemStack = menu.getSlot(i).getItem();
                if (!itemStack.isEmpty() && itemStack.getCount() > biggestSlotSize) {
                    biggestSlot = menu.getSlot(i);
                    biggestSlotStack = itemStack;
                    biggestSlotSize = itemStack.getCount();
                }
            }

            if (biggestSlotStack == null) {
                return;
            }

            boolean emptyBiggestSlot = false;
            for (int i = start; i < start + size; i++) {
                ItemStack itemStack = menu.getSlot(i).getItem();
                if (itemStack.isEmpty()) {
                    if (biggestSlotStack.getCount() > 1) {
                        Slot slot = menu.getSlot(i);
                        if (slot.mayPlace(biggestSlotStack) && slot.getMaxStackSize(biggestSlotStack) > 0) {
                            ItemStack remaining = biggestSlotStack.copyWithCount(biggestSlotStack.getCount() - 1);
                            slot.set(biggestSlotStack.copyWithCount(1));
                            biggestSlot.set(remaining);
                            biggestSlotStack = remaining;
                        }
                    } else {
                        emptyBiggestSlot = true;
                    }
                }
            }

            if (!emptyBiggestSlot) {
                break;
            }
        }

        balanceGrid(grid, player, menu);
    }
}
