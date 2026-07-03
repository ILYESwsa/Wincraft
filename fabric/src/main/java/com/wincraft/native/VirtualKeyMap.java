package com.wincraft.native;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates GLFW key codes (what Minecraft/LWJGL give us) into Windows
 * virtual-key codes (what PostMessage/SendInput expect).
 *
 * This is the direct equivalent of what xkbcommon handles on the Linux
 * side of waylandcraft — except Windows already resolves layout-specific
 * behavior for us downstream, so this table only needs to cover the
 * physical/virtual key identity, not layout translation.
 */
public final class VirtualKeyMap {

    private static final Map<Integer, Integer> GLFW_TO_VK = new HashMap<>();

    // Standard VK_ constants (winuser.h)
    private static final int VK_BACK = 0x08;
    private static final int VK_TAB = 0x09;
    private static final int VK_RETURN = 0x0D;
    private static final int VK_SHIFT = 0x10;
    private static final int VK_CONTROL = 0x11;
    private static final int VK_MENU = 0x12; // Alt
    private static final int VK_ESCAPE = 0x1B;
    private static final int VK_SPACE = 0x20;
    private static final int VK_PRIOR = 0x21; // Page Up
    private static final int VK_NEXT = 0x22;  // Page Down
    private static final int VK_END = 0x23;
    private static final int VK_HOME = 0x24;
    private static final int VK_LEFT = 0x25;
    private static final int VK_UP = 0x26;
    private static final int VK_RIGHT = 0x27;
    private static final int VK_DOWN = 0x28;
    private static final int VK_DELETE = 0x2E;
    private static final int VK_LWIN = 0x5B;
    private static final int VK_CAPITAL = 0x14; // Caps Lock

    static {
        // Letters A-Z map 1:1 between GLFW and VK (both use ASCII 'A'-'Z')
        for (int c = 'A'; c <= 'Z'; c++) {
            GLFW_TO_VK.put(c, c);
        }
        // Digits 0-9 map 1:1 as well
        for (int c = '0'; c <= '9'; c++) {
            GLFW_TO_VK.put(c, c);
        }

        GLFW_TO_VK.put(GLFW.GLFW_KEY_SPACE, VK_SPACE);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_ENTER, VK_RETURN);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_KP_ENTER, VK_RETURN);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_TAB, VK_TAB);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_BACKSPACE, VK_BACK);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_ESCAPE, VK_ESCAPE);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_DELETE, VK_DELETE);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_HOME, VK_HOME);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_END, VK_END);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_PAGE_UP, VK_PRIOR);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_PAGE_DOWN, VK_NEXT);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_LEFT, VK_LEFT);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_RIGHT, VK_RIGHT);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_UP, VK_UP);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_DOWN, VK_DOWN);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_CAPS_LOCK, VK_CAPITAL);

        GLFW_TO_VK.put(GLFW.GLFW_KEY_LEFT_SHIFT, VK_SHIFT);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_RIGHT_SHIFT, VK_SHIFT);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_LEFT_CONTROL, VK_CONTROL);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_RIGHT_CONTROL, VK_CONTROL);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_LEFT_ALT, VK_MENU);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_RIGHT_ALT, VK_MENU);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_LEFT_SUPER, VK_LWIN);
        GLFW_TO_VK.put(GLFW.GLFW_KEY_RIGHT_SUPER, VK_LWIN);

        // Function keys F1-F25 -> VK_F1 (0x70) sequential
        for (int i = 0; i <= 24; i++) {
            int glfwKey = GLFW.GLFW_KEY_F1 + i;
            GLFW_TO_VK.put(glfwKey, 0x70 + i);
        }
    }

    private VirtualKeyMap() {}

    /** Returns the Windows VK_ code for a GLFW key code, or -1 if unmapped. */
    public static int toVirtualKey(int glfwKey) {
        return GLFW_TO_VK.getOrDefault(glfwKey, -1);
    }
}
