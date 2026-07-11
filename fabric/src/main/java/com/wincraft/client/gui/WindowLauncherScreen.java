package com.wincraft.client.gui;

import com.wincraft.item.ModComponents;
import com.wincraft.item.ModItems;
import com.wincraft.item.WindowItemData;
import com.wincraft.natives.WindowHandle;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Bound to the "open launcher" key (default V). Lists currently open
 * Windows applications; clicking one gives the player a window-spawner
 * item for it instead of placing the window immediately. Right-clicking
 * that item is what actually starts the capture (see WindowSpawnerItem) —
 * this screen only picks *which* window the item refers to.
 *
 * Built against Fabric's confirmed 26.1.2 Screen API: extractRenderState
 * (not render), GuiGraphicsExtractor (not GuiGraphics), Button.builder()
 * .bounds() (not .dimensions()), addRenderableWidget (not addDrawableChild).
 * See: https://docs.fabricmc.net/develop/rendering/gui/custom-screens
 */
public class WindowLauncherScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 260;

    public WindowLauncherScreen() {
        super(Component.translatable("gui.wincraft.launcher.title"));
    }

    @Override
    protected void init() {
        List<WindowHandle> handles = WindowManager.get().listAvailableWindows();

        int y = this.height / 4;
        int x = (this.width - LIST_WIDTH) / 2;

        for (WindowHandle handle : handles) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(truncate(handle.title, 40)),
                    button -> openWindow(handle)
            ).bounds(x, y, LIST_WIDTH, 20).build());
            y += ROW_HEIGHT;
        }

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wincraft.launcher.refresh"),
                button -> Minecraft.getInstance().setScreen(new WindowLauncherScreen())
        ).bounds(x, y + 10, LIST_WIDTH, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                button -> Minecraft.getInstance().setScreen(null)
        ).bounds(x, y + 36, LIST_WIDTH, 20).build());
    }

    private void openWindow(WindowHandle handle) {
        WindowItemData data = new WindowItemData(handle.hwnd, handle.title, handle.className, Optional.empty());
        ItemStack stack = new ItemStack(ModItems.WINDOW_SPAWNER);
        stack.set(ModComponents.WINDOW_DATA, data);
        // Show the actual app name in inventory/tooltip instead of the
        // generic "Captured Window" item name, so multiple picked-up
        // windows are distinguishable at a glance.
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(handle.title));

        var player = Minecraft.getInstance().player;
        if (player != null && !player.getInventory().add(stack)) {
            // Inventory full — drop it at the player's feet like a normal pickup miss.
            player.drop(stack, false);
        }
        Minecraft.getInstance().setScreen(null);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String titleStr = this.title.getString();
        int titleWidth = this.font.width(titleStr);
        graphics.text(this.font, titleStr, (this.width - titleWidth) / 2, 20, 0xFFFFFFFF, true);

        if (WindowManager.get().listAvailableWindows().isEmpty()) {
            String msg = Component.translatable("gui.wincraft.launcher.empty").getString();
            int msgWidth = this.font.width(msg);
            graphics.text(this.font, msg, (this.width - msgWidth) / 2, this.height / 4, 0xFFAAAAAA, true);
        }
    }
}