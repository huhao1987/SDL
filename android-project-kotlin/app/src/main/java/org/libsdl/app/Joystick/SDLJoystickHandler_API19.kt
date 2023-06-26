package org.libsdl.app.Joystick

import android.view.InputDevice
import android.view.InputDevice.MotionRange
import android.view.KeyEvent
import android.view.MotionEvent

class SDLJoystickHandler_API19 : SDLJoystickHandler_API16() {
    override fun getProductId(joystickDevice: InputDevice?): Int {
        return joystickDevice!!.productId
    }

    override fun getVendorId(joystickDevice: InputDevice?): Int {
        return joystickDevice!!.vendorId
    }

    override fun getAxisMask(ranges: List<MotionRange>?): Int {
        // For compatibility, keep computing the axis mask like before,
        // only really distinguishing 2, 4 and 6 axes.
        var axis_mask = 0
        if (ranges!!.size >= 2) {
            // ((1 << SDL_GAMEPAD_AXIS_LEFTX) | (1 << SDL_GAMEPAD_AXIS_LEFTY))
            axis_mask = axis_mask or 0x0003
        }
        if (ranges.size >= 4) {
            // ((1 << SDL_GAMEPAD_AXIS_RIGHTX) | (1 << SDL_GAMEPAD_AXIS_RIGHTY))
            axis_mask = axis_mask or 0x000c
        }
        if (ranges.size >= 6) {
            // ((1 << SDL_GAMEPAD_AXIS_LEFT_TRIGGER) | (1 << SDL_GAMEPAD_AXIS_RIGHT_TRIGGER))
            axis_mask = axis_mask or 0x0030
        }
        // Also add an indicator bit for whether the sorting order has changed.
        // This serves to disable outdated gamecontrollerdb.txt mappings.
        var have_z = false
        var have_past_z_before_rz = false
        for (range in ranges) {
            val axis = range.axis
            if (axis == MotionEvent.AXIS_Z) {
                have_z = true
            } else if (axis > MotionEvent.AXIS_Z && axis < MotionEvent.AXIS_RZ) {
                have_past_z_before_rz = true
            }
        }
        if (have_z && have_past_z_before_rz) {
            // If both these exist, the compare() function changed sorting order.
            // Set a bit to indicate this fact.
            axis_mask = axis_mask or 0x8000
        }
        return axis_mask
    }

    override fun getButtonMask(joystickDevice: InputDevice?): Int {
        var button_mask = 0
        val keys = intArrayOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_CENTER,  // These don't map into any SDL controller buttons directly
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_C,
            KeyEvent.KEYCODE_BUTTON_Z,
            KeyEvent.KEYCODE_BUTTON_1,
            KeyEvent.KEYCODE_BUTTON_2,
            KeyEvent.KEYCODE_BUTTON_3,
            KeyEvent.KEYCODE_BUTTON_4,
            KeyEvent.KEYCODE_BUTTON_5,
            KeyEvent.KEYCODE_BUTTON_6,
            KeyEvent.KEYCODE_BUTTON_7,
            KeyEvent.KEYCODE_BUTTON_8,
            KeyEvent.KEYCODE_BUTTON_9,
            KeyEvent.KEYCODE_BUTTON_10,
            KeyEvent.KEYCODE_BUTTON_11,
            KeyEvent.KEYCODE_BUTTON_12,
            KeyEvent.KEYCODE_BUTTON_13,
            KeyEvent.KEYCODE_BUTTON_14,
            KeyEvent.KEYCODE_BUTTON_15,
            KeyEvent.KEYCODE_BUTTON_16
        )
        val masks = intArrayOf(
            1 shl 0,  // A -> A
            1 shl 1,  // B -> B
            1 shl 2,  // X -> X
            1 shl 3,  // Y -> Y
            1 shl 4,  // BACK -> BACK
            1 shl 6,  // MENU -> START
            1 shl 5,  // MODE -> GUIDE
            1 shl 6,  // START -> START
            1 shl 7,  // THUMBL -> LEFTSTICK
            1 shl 8,  // THUMBR -> RIGHTSTICK
            1 shl 9,  // L1 -> LEFTSHOULDER
            1 shl 10,  // R1 -> RIGHTSHOULDER
            1 shl 11,  // DPAD_UP -> DPAD_UP
            1 shl 12,  // DPAD_DOWN -> DPAD_DOWN
            1 shl 13,  // DPAD_LEFT -> DPAD_LEFT
            1 shl 14,  // DPAD_RIGHT -> DPAD_RIGHT
            1 shl 4,  // SELECT -> BACK
            1 shl 0,  // DPAD_CENTER -> A
            1 shl 15,  // L2 -> ??
            1 shl 16,  // R2 -> ??
            1 shl 17,  // C -> ??
            1 shl 18,  // Z -> ??
            1 shl 20,  // 1 -> ??
            1 shl 21,  // 2 -> ??
            1 shl 22,  // 3 -> ??
            1 shl 23,  // 4 -> ??
            1 shl 24,  // 5 -> ??
            1 shl 25,  // 6 -> ??
            1 shl 26,  // 7 -> ??
            1 shl 27,  // 8 -> ??
            1 shl 28,  // 9 -> ??
            1 shl 29,  // 10 -> ??
            1 shl 30,  // 11 -> ??
            1 shl 31,  // 12 -> ??
            // We're out of room...
            -0x1,  // 13 -> ??
            -0x1,  // 14 -> ??
            -0x1,  // 15 -> ??
            -0x1
        )
        val has_keys = joystickDevice!!.hasKeys(*keys)
        for (i in keys.indices) {
            if (has_keys[i]) {
                button_mask = button_mask or masks[i]
            }
        }
        return button_mask
    }
}
