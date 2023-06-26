package org.libsdl.app.GenericMotion

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager

class SDLGenericMotionListener_API26 : SDLGenericMotionListener_API24() {
    // Generic Motion (mouse hover, joystick...) events go here
    private var mRelativeModeEnabled = false
    override fun onGenericMotion(v: View, event: MotionEvent): Boolean {
        val x: Float
        val y: Float
        val action: Int
        when (event.source) {
            InputDevice.SOURCE_JOYSTICK -> return SDLControllerManager.handleJoystickMotionEvent(
                event
            )

            InputDevice.SOURCE_MOUSE, InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHSCREEN -> {
                action = event.actionMasked
                when (action) {
                    MotionEvent.ACTION_SCROLL -> {
                        x = event.getAxisValue(MotionEvent.AXIS_HSCROLL, 0)
                        y = event.getAxisValue(MotionEvent.AXIS_VSCROLL, 0)
                        SDLActivity.onNativeMouse(0, action, x, y, false)
                        return true
                    }

                    MotionEvent.ACTION_HOVER_MOVE -> {
                        x = event.getX(0)
                        y = event.getY(0)
                        SDLActivity.onNativeMouse(0, action, x, y, false)
                        return true
                    }

                    else -> {}
                }
            }

            InputDevice.SOURCE_MOUSE_RELATIVE -> {
                action = event.actionMasked
                when (action) {
                    MotionEvent.ACTION_SCROLL -> {
                        x = event.getAxisValue(MotionEvent.AXIS_HSCROLL, 0)
                        y = event.getAxisValue(MotionEvent.AXIS_VSCROLL, 0)
                        SDLActivity.onNativeMouse(0, action, x, y, false)
                        return true
                    }

                    MotionEvent.ACTION_HOVER_MOVE -> {
                        x = event.getX(0)
                        y = event.getY(0)
                        SDLActivity.onNativeMouse(0, action, x, y, true)
                        return true
                    }

                    else -> {}
                }
            }

            else -> {}
        }

        // Event was not managed
        return false
    }

    override fun supportsRelativeMouse(): Boolean {
        return !SDLActivity.isDeXMode() || Build.VERSION.SDK_INT >= 27
    }

    override fun inRelativeMode(): Boolean {
        return mRelativeModeEnabled
    }

    override fun setRelativeMouseEnabled(enabled: Boolean): Boolean {
        return if (!SDLActivity.isDeXMode() || Build.VERSION.SDK_INT >= 27) {
            if (enabled) {
                SDLActivity.getContentView().requestPointerCapture()
            } else {
                SDLActivity.getContentView().releasePointerCapture()
            }
            mRelativeModeEnabled = enabled
            true
        } else {
            false
        }
    }

    override fun reclaimRelativeMouseModeIfNeeded() {
        if (mRelativeModeEnabled && !SDLActivity.isDeXMode()) {
            SDLActivity.getContentView().requestPointerCapture()
        }
    }

    override fun getEventX(event: MotionEvent): Float {
        // Relative mouse in capture mode will only have relative for X/Y
        return event.getX(0)
    }

    override fun getEventY(event: MotionEvent): Float {
        // Relative mouse in capture mode will only have relative for X/Y
        return event.getY(0)
    }
}
