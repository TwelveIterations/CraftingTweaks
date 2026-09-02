package net.blay09.mods.craftingtweaks.client;


import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.balm.client.BalmClientRegistrars;
import net.blay09.mods.balm.client.gui.screens.BalmScreenUtils;
import net.blay09.mods.balm.client.platform.event.callback.ClientLifecycleCallback;
import net.blay09.mods.balm.client.platform.event.callback.ScreenCallback;
import net.blay09.mods.balm.mixin.AbstractContainerScreenAccessor;
import net.blay09.mods.balm.platform.event.callback.ItemCallback;
import net.blay09.mods.craftingtweaks.CraftingGuideButtonFixer;
import net.blay09.mods.craftingtweaks.CraftingTweaks;
import net.blay09.mods.craftingtweaks.CraftingTweaksProviderManager;
import net.blay09.mods.craftingtweaks.api.CraftingGrid;
import net.blay09.mods.craftingtweaks.api.CraftingTweaksClientAPI;
import net.blay09.mods.craftingtweaks.api.GridGuiHandler;
import net.blay09.mods.craftingtweaks.api.impl.DefaultGridGuiHandler;
import net.blay09.mods.craftingtweaks.api.impl.InternalClientMethodsImpl;
import net.blay09.mods.craftingtweaks.config.CraftingTweaksConfig;
import net.blay09.mods.craftingtweaks.config.CraftingTweaksMode;
import net.blay09.mods.craftingtweaks.network.CraftStackMessage;
import net.blay09.mods.craftingtweaks.network.TransferStackMessage;
import net.blay09.mods.kuma.api.Kuma;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;


public class CraftingTweaksClient {

    private static final ClientProvider clientProvider = new ClientProvider();

    private static boolean ignoreMouseUp;
    private static int rightClickCraftingSlot = -1;
    private static @Nullable AbstractWidget unpleasantButton;
    private static int fixedUnpleasantButtonX;

    public static void initialize(BalmClientRegistrars registrars) {
        CraftingTweaksClientAPI.setupAPI(new InternalClientMethodsImpl());

        //noinspection unchecked
        CraftingTweaksClientAPI.registerCraftingGridGuiHandler(AbstractContainerScreen.class, new DefaultGridGuiHandler());

        ModKeyMappings.initialize();

        ItemCallback.Craft.After.EVENT.register(CraftingTweaksClient::onItemCrafted);

        ClientLifecycleCallback.ConnectedToServer.EVENT.register(client -> CraftingTweaks.isServerSideInstalled = false);

        ScreenCallback.Init.After.EVENT.register(CraftingTweaksClient::screenInitialized);
        ScreenCallback.MousePress.Before.EVENT.register(CraftingTweaksClient::screenMouseClick);
        ScreenCallback.MouseRelease.Before.EVENT.register(CraftingTweaksClient::screenMouseRelease);
        ScreenCallback.Render.BEFORE.register(CraftingTweaksClient::screenAboutToDraw);
        ScreenCallback.Render.AFTER.register(CraftingTweaksClient::screenDrawn);

        CraftingTweaksDebugger.initialize();
    }

    public static boolean screenMouseRelease(Screen screen, double mouseX, double mouseY, int button) {
        if (ignoreMouseUp) {
            ignoreMouseUp = false;
            return true;
        }

        return false;
    }

    public static boolean screenMouseClick(Screen screen, MouseButtonEvent event) {
        // Reset right-click crafting if any click happens
        rightClickCraftingSlot = -1;

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return false;
        }

        Slot mouseSlot = screen instanceof AbstractContainerScreen<?> ? ((AbstractContainerScreenAccessor) screen).getHoveredSlot() : null;
        CraftingGrid grid = CraftingTweaksProviderManager.getDefaultCraftingGrid(menu).orElse(null);
        if (grid != null) {
            if (ModKeyMappings.keyTransferStack.isActiveAndDown()) {
                if (mouseSlot != null && mouseSlot.hasItem()) {
                    List<Slot> transferSlots = Lists.newArrayList();
                    transferSlots.add(mouseSlot);
                    if (Kuma.hasShiftDown()) {
                        ItemStack mouseSlotStack = mouseSlot.getItem();
                        for (Slot slot : menu.slots) {
                            if (!slot.hasItem() || mouseSlot == slot) {
                                continue;
                            }
                            ItemStack slotStack = slot.getItem();
                            if (ItemStack.isSameItemSameComponents(slotStack, mouseSlotStack)) {
                                transferSlots.add(slot);
                            }
                        }
                    }

                    if (CraftingTweaks.isServerSideInstalled) {
                        for (Slot slot : transferSlots) {
                            Balm.networking().sendToServer(new TransferStackMessage(grid.getId(), slot.index));
                        }
                    } else {
                        for (Slot slot : transferSlots) {
                            clientProvider.transferIntoGrid(player, menu, grid, slot);
                        }
                        ignoreMouseUp = true;
                    }

                    return true;
                }
            } else if (CraftingTweaksConfig.getActive().client.rightClickCraftsStack && event.button() == InputConstants.MOUSE_BUTTON_RIGHT && mouseSlot instanceof ResultSlot) {
                if (CraftingTweaks.isServerSideInstalled) {
                    Balm.networking().sendToServer(new CraftStackMessage(mouseSlot.index));
                } else {
                    rightClickCraftingSlot = mouseSlot.index;
                }
                ignoreMouseUp = true;
                return true;
            }
        }

        return false;
    }

    public static void screenInitialized(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            GridGuiHandler guiHandler = CraftingTweaksClientProviderManager.getGridGuiHandler(containerScreen);
            unpleasantButton = CraftingGuideButtonFixer.fixMistakes(containerScreen, guiHandler);
            if (unpleasantButton != null) {
                fixedUnpleasantButtonX = unpleasantButton.getX();
            }

            List<CraftingGrid> grids = CraftingTweaksProviderManager.getCraftingGrids(containerScreen.getMenu());
            for (CraftingGrid grid : grids) {
                String modId = grid.getId().getNamespace();
                CraftingTweaksMode config = CraftingTweaksConfig.getActive().getCraftingTweaksMode(modId);
                if ((config == CraftingTweaksMode.DEFAULT || config == CraftingTweaksMode.BUTTONS)) {
                    guiHandler.createButtons(containerScreen, grid, widget -> BalmScreenUtils.addRenderableWidget(screen, widget));
                }
            }
        }
    }

    private static void handleRightClickCrafting() {
        if (rightClickCraftingSlot == -1) {
            return;
        }

        int craftingSlot = rightClickCraftingSlot;
        rightClickCraftingSlot = -1;

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        MultiPlayerGameMode playerController = Minecraft.getInstance().gameMode;
        if (playerController == null) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return;
        }

        if (!menu.isValidSlotIndex(craftingSlot)) {
            return;
        }

        Slot mouseSlot = menu.getSlot(craftingSlot);
        if (!mouseSlot.hasItem()) {
            rightClickCraftingSlot = mouseSlot.index;
            return;
        }

        ItemStack mouseStack = menu.getCarried();
        if (mouseStack.isEmpty() || mouseStack.getCount() + mouseSlot.getItem().getCount() <= mouseStack.getMaxStackSize()) {
            playerController.handleContainerInput(menu.containerId, mouseSlot.index, 0, ContainerInput.PICKUP, player);
            rightClickCraftingSlot = mouseSlot.index;
        }
    }

    public static void screenAboutToDraw(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // Detect changes on the button that shall not be named to fix its positioning
        if (screen instanceof AbstractContainerScreen<?> containerScreen && unpleasantButton != null) {
            int unpleasantX = unpleasantButton.getX();
            if (unpleasantX != fixedUnpleasantButtonX) {
                GridGuiHandler guiHandler = CraftingTweaksClientProviderManager.getGridGuiHandler(containerScreen);
                unpleasantButton = CraftingGuideButtonFixer.fixMistakes(containerScreen, guiHandler);
                if (unpleasantButton != null) {
                    fixedUnpleasantButtonX = unpleasantButton.getX();
                }
            }
        }
    }

    public static void screenDrawn(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        handleRightClickCrafting();
    }

    private static void onItemCrafted(Player player, ItemStack itemStack, Container craftMatrix) {
        clientProvider.onItemCrafted(craftMatrix);
    }

    public static ClientProvider getClientProvider() {
        return clientProvider;
    }
}
