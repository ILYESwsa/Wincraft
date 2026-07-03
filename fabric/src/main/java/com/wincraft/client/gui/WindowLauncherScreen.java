package com.wincraft.client.gui;

import com.wincraft.native.WindowHandle;
import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Bound to the "open launcher" key (default B, mirroring waylandcraft's
 * window-manager screen). Lists currently open Windows applications;
 * clicking one starts capturing it and spawns it as a grabbable window
 * in front of the player.
 */
public class WindowLauncherScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 260;

    public WindowLauncherScreen() {
        super(Text.translatable("gui.wincraft.launcher.title"));
    }

    @Override
    protected void init() {
        List<WindowHandle> handles = WindowManager.get().listAvailableWindows();

        int startY = this.height / 4;
        int x = (this.width - LIST_WIDTH) / 2;

        if (handles.isEmpty()) {
            // No-op: renderBackground draws a hint text in render(), see below.
            return;
        }

        int y = startY;
        for (WindowHandle handle : handles) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(truncate(handle.title, 40)),
                    button -> openWindow(handle)
            ).dimensions(x, y, LIST_WIDTH, 20).build());
            y += ROW_HEIGHT;
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.wincraft.launcher.refresh"),
                button -> this.clearAndInit()
        ).dimensions(x, y + 10, LIST_WIDTH, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.cancel"),
                button -> this.close()
        ).dimensions(x, y + 36, LIST_WIDTH, 20).build());
    }

    private void openWindow(WindowHandle handle) {
        CapturedWindow window = WindowManager.get().open(handle);
        if (window != null) {
            this.close();
        }
        // If null, capture failed (e.g. protected content, or the
        // window closed between enumeration and click) — leave the
        // screen open so the user can pick another.
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        if (WindowManager.get().listAvailableWindows().isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("gui.wincraft.launcher.empty"),
                    this.width / 2,
                    this.height / 4,
                    0xAAAAAA
            );
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
