package com.wincraft.client.gui;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Bound to the "open manager" key (default B). Lets the player select an
 * already-open captured window (whichever one they're looking closest at)
 * and move / resize / close it, mirroring waylandcraft's window-manager
 * screen.
 *
 * This screen stays open (doesn't close on selection) so distance/scale
 * can be tuned interactively with immediate visual feedback, same as
 * dragging a window in the Linux mod. Closing the screen (Escape or the
 * Done button) leaves the window wherever it was last placed.
 */
public class WindowManagerScreen extends Screen {

    private static final double MAX_LOOK_DISTANCE = 12.0D;
    private static final double MOVE_STEP = 0.25D;
    private static final float RESIZE_STEP = 0.15F;

    private UUID selected;
    private double distance = 3.0D;

    public WindowManagerScreen() {
        super(Component.translatable("gui.wincraft.manager.title"));
    }

    @Override
    protected void init() {
        int y = this.height - 32;
        int spacing = 84;
        int x = (this.width - spacing * 4) / 2;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wincraft.manager.closer"),
                button -> adjustDistance(-MOVE_STEP)
        ).bounds(x, y, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wincraft.manager.farther"),
                button -> adjustDistance(MOVE_STEP)
        ).bounds(x + spacing, y, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wincraft.manager.smaller"),
                button -> adjustSize(-RESIZE_STEP)
        ).bounds(x + spacing * 2, y, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wincraft.manager.larger"),
                button -> adjustSize(RESIZE_STEP)
        ).bounds(x + spacing * 3, y, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wincraft.manager.close_window"),
                button -> closeSelected()
        ).bounds((this.width - 100) / 2, y - 26, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                button -> Minecraft.getInstance().setScreen(null)
        ).bounds((this.width - 100) / 2, y + 26, 100, 20).build());

        refreshSelection();
    }

    /** Re-picks whichever window the player is currently looking at. */
    private void refreshSelection() {
        CapturedWindow window = WindowManager.get().findLookedAt(MAX_LOOK_DISTANCE);
        if (window != null) {
            selected = window.id;
        }
    }

    private CapturedWindow selectedWindow() {
        return selected == null ? null : WindowManager.get().get(selected);
    }

    private void adjustDistance(double delta) {
        refreshSelection();
        CapturedWindow window = selectedWindow();
        if (window == null) return;
        distance = Math.max(0.5D, Math.min(10.0D, distance + delta));
        WindowManager.get().moveInFrontOfPlayer(window, distance);
    }

    private void adjustSize(float delta) {
        refreshSelection();
        CapturedWindow window = selectedWindow();
        if (window == null) return;
        window.setWidthBlocks(window.getWidthBlocks() + delta);
    }

    private void closeSelected() {
        refreshSelection();
        if (selected != null) {
            WindowManager.get().close(selected);
            selected = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        refreshSelection();
        String titleStr = this.title.getString();
        int titleWidth = this.font.width(titleStr);
        graphics.text(this.font, titleStr, (this.width - titleWidth) / 2, 20, 0xFFFFFFFF, true);

        CapturedWindow window = selectedWindow();
        String status = window == null
                ? Component.translatable("gui.wincraft.manager.none_selected").getString()
                : window.title;
        int statusWidth = this.font.width(status);
        graphics.text(this.font, status, (this.width - statusWidth) / 2, 36, window == null ? 0xFFAAAAAA : 0xFFFFFFFF, true);
    }
}
