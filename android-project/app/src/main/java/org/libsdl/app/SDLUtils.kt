package org.libsdl.app

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.Sensor
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.PointerIcon
import android.view.Surface
import android.view.View
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

object SDLUtils {
    private val TAG = "SDLUtils:"
    // Cursor types
    // private static final int SDL_SYSTEM_CURSOR_NONE = -1;
    private const val SDL_SYSTEM_CURSOR_ARROW = 0
    private const val SDL_SYSTEM_CURSOR_IBEAM = 1
    private const val SDL_SYSTEM_CURSOR_WAIT = 2
    private const val SDL_SYSTEM_CURSOR_CROSSHAIR = 3
    private const val SDL_SYSTEM_CURSOR_WAITARROW = 4
    private const val SDL_SYSTEM_CURSOR_SIZENWSE = 5
    private const val SDL_SYSTEM_CURSOR_SIZENESW = 6
    private const val SDL_SYSTEM_CURSOR_SIZEWE = 7
    private const val SDL_SYSTEM_CURSOR_SIZENS = 8
    private const val SDL_SYSTEM_CURSOR_SIZEALL = 9
    private const val SDL_SYSTEM_CURSOR_NO = 10
    private const val SDL_SYSTEM_CURSOR_HAND = 11
    // Messages from the SDLMain thread
    const val COMMAND_CHANGE_TITLE = 1
    const val COMMAND_CHANGE_WINDOW_STYLE = 2
    const val COMMAND_TEXTEDIT_HIDE = 3
    const val COMMAND_SET_KEEP_SCREEN_ON = 5
    const val COMMAND_USER = 0x8000

    // This is what SDL runs in. It invokes SDL_main(), eventually
    @JvmField
    var mSDLThread: Thread? = null
    
    // C functions we call
    @JvmStatic
    external fun nativeGetVersion(): String

    @JvmStatic
    external fun nativeSetupJNI(): Int

    @JvmStatic
    external fun nativeRunMain(library: String?, function: String?, arguments: Any?): Int

    @JvmStatic
    external fun nativeLowMemory()

    @JvmStatic
    external fun nativeSendQuit()

    @JvmStatic
    external fun nativeQuit()

    @JvmStatic
    external fun nativePause()

    @JvmStatic
    external fun nativeResume()

    @JvmStatic
    external fun nativeFocusChanged(hasFocus: Boolean)

    @JvmStatic
    external fun onNativeDropFile(filename: String?)

    @JvmStatic
    external fun nativeSetScreenResolution(
        surfaceWidth: Int,
        surfaceHeight: Int,
        deviceWidth: Int,
        deviceHeight: Int,
        rate: Float
    )

    @JvmStatic
    external fun onNativeResize()

    @JvmStatic
    external fun onNativeKeyDown(keycode: Int)

    @JvmStatic
    external fun onNativeKeyUp(keycode: Int)

    @JvmStatic
    external fun onNativeSoftReturnKey(): Boolean

    @JvmStatic
    external fun onNativeKeyboardFocusLost()

    @JvmStatic
    external fun onNativeMouse(button: Int, action: Int, x: Float, y: Float, relative: Boolean)

    @JvmStatic
    external fun onNativeTouch(
        touchDevId: Int, pointerFingerId: Int,
        action: Int, x: Float,
        y: Float, p: Float
    )

    @JvmStatic
    external fun onNativeAccel(x: Float, y: Float, z: Float)

    @JvmStatic
    external fun onNativeClipboardChanged()

    @JvmStatic
    external fun onNativeSurfaceCreated()

    @JvmStatic
    external fun onNativeSurfaceChanged()

    @JvmStatic
    external fun onNativeSurfaceDestroyed()

    @JvmStatic
    external fun nativeGetHint(name: String?): String?

    @JvmStatic
    external fun nativeGetHintBoolean(name: String?, default_value: Boolean): Boolean

    @JvmStatic
    external fun nativeSetenv(name: String?, value: String?)

    @JvmStatic
    external fun onNativeOrientationChanged(orientation: Int)

    @JvmStatic
    external fun nativeAddTouch(touchId: Int, name: String?)

    @JvmStatic
    external fun nativePermissionResult(requestCode: Int, result: Boolean)

    @JvmStatic
    external fun onNativeLocaleChanged()

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun setActivityTitle(title: String?): Boolean {
        // Called from SDLMain() thread and can't directly affect the view
        return SDLActivity.mSingleton!!.sendCommand(COMMAND_CHANGE_TITLE, title)
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun setWindowStyle(fullscreen: Boolean) {
        // Called from SDLMain() thread and can't directly affect the view
        SDLActivity.mSingleton!!.sendCommand(COMMAND_CHANGE_WINDOW_STYLE, if (fullscreen) 1 else 0)
    }

    /**
     * This method is called by SDL using JNI.
     * This is a static method for JNI convenience, it calls a non-static method
     * so that is can be overridden
     */
    @JvmStatic
    fun setOrientation(w: Int, h: Int, resizable: Boolean, hint: String) {
        if (SDLActivity.mSingleton != null) {
            SDLActivity.mSingleton!!.setOrientationBis(w, h, resizable, hint)
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun minimizeWindow() {
        if (SDLActivity.mSingleton == null) {
            return
        }
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        SDLActivity.mSingleton!!.startActivity(startMain)
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun shouldMinimizeOnFocusLoss(): Boolean {
        /*
    if (Build.VERSION.SDK_INT >= 24) {
        if (mSingleton == null) {
            return true;
        }

        if (mSingleton.isInMultiWindowMode()) {
            return false;
        }

        if (mSingleton.isInPictureInPictureMode()) {
            return false;
        }
    }

    return true;
*/
        return false
    }

    @JvmStatic
    val isScreenKeyboardShown: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() {
            if (SDLActivity.mTextEdit == null) {
                return false
            }
            if (!SDLActivity.mScreenKeyboardShown) {
                return false
            }
            val imm =
                SDL.getContext()!!.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
            return imm.isAcceptingText
        }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun supportsRelativeMouse(): Boolean {
        // DeX mode in Samsung Experience 9.0 and earlier doesn't support relative mice properly under
        // Android 7 APIs, and simply returns no data under Android 8 APIs.
        //
        // This is fixed in Samsung Experience 9.5, which corresponds to Android 8.1.0, and
        // thus SDK version 27.  If we are in DeX mode and not API 27 or higher, as a result,
        // we should stick to relative mode.
        //
        return if (Build.VERSION.SDK_INT < 27 && isDeXMode) {
            false
        } else motionListener!!.supportsRelativeMouse()
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun setRelativeMouseEnabled(enabled: Boolean): Boolean {
        return if (enabled && !supportsRelativeMouse()) {
            false
        } else motionListener!!.setRelativeMouseEnabled(enabled)
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun sendMessage(command: Int, param: Int): Boolean {
        return if (SDLActivity.mSingleton == null) {
            false
        } else SDLActivity.mSingleton!!.sendCommand(command, param)
    }

    @JvmStatic
    val context: Context?
        /**
         * This method is called by SDL using JNI.
         */
        get() = SDL.getContext()

    @JvmStatic
    val isAndroidTV: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() {
            val uiModeManager = context!!.getSystemService(AppCompatActivity.UI_MODE_SERVICE) as UiModeManager
            if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                return true
            }
            if (Build.MANUFACTURER == "MINIX" && Build.MODEL == "NEO-U1") {
                return true
            }
            return if (Build.MANUFACTURER == "Amlogic" && Build.MODEL == "X96-W") {
                true
            } else Build.MANUFACTURER == "Amlogic" && Build.MODEL.startsWith("TV")
        }

    @JvmStatic
    val isTablet: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() =// If our diagonal size is seven inches or greater, we consider ourselves a tablet.
            SDLActivity.diagonal >= 7.0

    @JvmStatic
    val isChromebook: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() = if (context == null) {
            false
        } else context!!.packageManager.hasSystemFeature("org.chromium.arc.device_management")

    @JvmStatic
    val isDeXMode: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() = if (Build.VERSION.SDK_INT < 24) {
            false
        } else try {
            val config = context!!.resources.configuration
            val configClass: Class<*> = config.javaClass
            (configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass)
                    == configClass.getField("semDesktopModeEnabled").getInt(config))
        } catch (ignored: Exception) {
            false
        }

    @JvmStatic
    val displayDPI: DisplayMetrics
        /**
         * This method is called by SDL using JNI.
         */
        get() = context!!.resources.displayMetrics

    @JvmStatic
    val manifestEnvironmentVariables: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() {
            try {
                if (context == null) {
                    return false
                }
                val applicationInfo = context!!.packageManager.getApplicationInfo(
                    context!!.packageName, PackageManager.GET_META_DATA
                )
                val bundle = applicationInfo.metaData ?: return false
                val prefix = "SDL_ENV."
                val trimLength = prefix.length
                for (key in bundle.keySet()) {
                    if (key.startsWith(prefix)) {
                        val name = key.substring(trimLength)
                        val value = bundle[key].toString()
                        nativeSetenv(name, value)
                    }
                }
                /* environment variables set! */return true
            } catch (e: Exception) {
                Log.v(TAG, "exception $e")
            }
            return false
        }

    @JvmStatic
    val contentView: View?
        // This method is called by SDLControllerManager's API 26 Generic Motion Handler.
        get() = SDLActivity.mLayout

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun showTextInput(x: Int, y: Int, w: Int, h: Int): Boolean {
        // Transfer the task to the main thread as a Runnable
        return SDLActivity.mSingleton!!.commandHandler.post(
            SDLActivity.ShowTextInputTask(
                x,
                y,
                w,
                h
            )
        )
    }
    @JvmStatic
    fun handleKeyEvent(v: View?, keyCode: Int, event: KeyEvent, ic: InputConnection?): Boolean {
        val deviceId = event.deviceId
        var source = event.source
        if (source == InputDevice.SOURCE_UNKNOWN) {
            val device = InputDevice.getDevice(deviceId)
            if (device != null) {
                source = device.sources
            }
        }

//        if (event.getAction() == KeyEvent.ACTION_DOWN) {
//            Log.v("SDL", "key down: " + keyCode + ", deviceId = " + deviceId + ", source = " + source);
//        } else if (event.getAction() == KeyEvent.ACTION_UP) {
//            Log.v("SDL", "key up: " + keyCode + ", deviceId = " + deviceId + ", source = " + source);
//        }

        // Dispatch the different events depending on where they come from
        // Some SOURCE_JOYSTICK, SOURCE_DPAD or SOURCE_GAMEPAD are also SOURCE_KEYBOARD
        // So, we try to process them as JOYSTICK/DPAD/GAMEPAD events first, if that fails we try them as KEYBOARD
        //
        // Furthermore, it's possible a game controller has SOURCE_KEYBOARD and
        // SOURCE_JOYSTICK, while its key events arrive from the keyboard source
        // So, retrieve the device itself and check all of its sources
        if (SDLControllerManager.isDeviceSDLJoystick(deviceId)) {
            // Note that we process events with specific key codes here
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (SDLControllerManager.onNativePadDown(deviceId, keyCode) == 0) {
                    return true
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (SDLControllerManager.onNativePadUp(deviceId, keyCode) == 0) {
                    return true
                }
            }
        }
        if (source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (SDLActivity.isTextInputEvent(event)) {
                    ic?.commitText(event.unicodeChar.toChar().toString(), 1)
                        ?: SDLInputConnection.nativeCommitText(
                            event.unicodeChar.toChar().toString(), 1
                        )
                }
                onNativeKeyDown(keyCode)
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                onNativeKeyUp(keyCode)
                return true
            }
        }
        if (source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            // on some devices key events are sent for mouse BUTTON_BACK/FORWARD presses
            // they are ignored here because sending them as mouse input to SDL is messy
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_FORWARD) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP ->                     // mark the event as handled or it will be handled by system
                        // handling KEYCODE_BACK by system will call onBackPressed()
                        return true
                }
            }
        }
        return false
    }

    @JvmStatic
    val nativeSurface: Surface?
        /**
         * This method is called by SDL using JNI.
         */
        get() = if (SDLActivity.mSurface == null) {
            null
        } else SDLActivity.mSurface!!.nativeSurface
    // Input
    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun initTouch() {
        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val device = InputDevice.getDevice(id)
            /* Allow SOURCE_TOUCHSCREEN and also Virtual InputDevices because they can send TOUCHSCREEN events */if (device != null && (device.sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN
                        || device.isVirtual)
            ) {
                var touchDevId = device.id
                /*
             * Prevent id to be -1, since it's used in SDL internal for synthetic events
             * Appears when using Android emulator, eg:
             *  adb shell input mouse tap 100 100
             *  adb shell input touchscreen tap 100 100
             */if (touchDevId < 0) {
                    touchDevId -= 1
                }
                nativeAddTouch(touchDevId, device.name)
            }
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun clipboardHasText(): Boolean {
        return SDLActivity.mClipboardHandler!!.clipboardHasText()
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun clipboardGetText(): String? {
        return SDLActivity.mClipboardHandler!!.clipboardGetText()
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun clipboardSetText(string: String?) {
        SDLActivity.mClipboardHandler!!.clipboardSetText(string)
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun createCustomCursor(
        colors: IntArray?,
        width: Int,
        height: Int,
        hotSpotX: Int,
        hotSpotY: Int
    ): Int {
        val bitmap = Bitmap.createBitmap(colors!!, width, height, Bitmap.Config.ARGB_8888)
        ++SDLActivity.mLastCursorID
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                SDLActivity.mCursors!![SDLActivity.mLastCursorID] =
                    PointerIcon.create(bitmap, hotSpotX.toFloat(), hotSpotY.toFloat())
            } catch (e: Exception) {
                return 0
            }
        } else {
            return 0
        }
        return SDLActivity.mLastCursorID
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun destroyCustomCursor(cursorID: Int) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                SDLActivity.mCursors!!.remove(cursorID)
            } catch (e: Exception) {
            }
        }
        return
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun setCustomCursor(cursorID: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                SDLActivity.mSurface!!.pointerIcon = SDLActivity.mCursors!![cursorID]
            } catch (e: Exception) {
                return false
            }
        } else {
            return false
        }
        return true
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun setSystemCursor(cursorID: Int): Boolean {
        var cursor_type = 0 //PointerIcon.TYPE_NULL;
        when (cursorID) {
            SDL_SYSTEM_CURSOR_ARROW -> cursor_type = 1000 //PointerIcon.TYPE_ARROW;
            SDL_SYSTEM_CURSOR_IBEAM -> cursor_type = 1008 //PointerIcon.TYPE_TEXT;
            SDL_SYSTEM_CURSOR_WAIT -> cursor_type = 1004 //PointerIcon.TYPE_WAIT;
            SDL_SYSTEM_CURSOR_CROSSHAIR -> cursor_type = 1007 //PointerIcon.TYPE_CROSSHAIR;
            SDL_SYSTEM_CURSOR_WAITARROW -> cursor_type = 1004 //PointerIcon.TYPE_WAIT;
            SDL_SYSTEM_CURSOR_SIZENWSE -> cursor_type =
                1017 //PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW;
            SDL_SYSTEM_CURSOR_SIZENESW -> cursor_type =
                1016 //PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW;
            SDL_SYSTEM_CURSOR_SIZEWE -> cursor_type =
                1014 //PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW;
            SDL_SYSTEM_CURSOR_SIZENS -> cursor_type =
                1015 //PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW;
            SDL_SYSTEM_CURSOR_SIZEALL -> cursor_type = 1020 //PointerIcon.TYPE_GRAB;
            SDL_SYSTEM_CURSOR_NO -> cursor_type = 1012 //PointerIcon.TYPE_NO_DROP;
            SDL_SYSTEM_CURSOR_HAND -> cursor_type = 1002 //PointerIcon.TYPE_HAND;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                SDLActivity.mSurface!!.pointerIcon = PointerIcon.getSystemIcon(SDL.getContext()!!, cursor_type)
            } catch (e: Exception) {
                return false
            }
        }
        return true
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun requestPermission(permission: String, requestCode: Int) {
        if (Build.VERSION.SDK_INT < 23) {
            nativePermissionResult(requestCode, true)
            return
        }
        val activity = context as Activity?
        if (activity!!.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(permission), requestCode)
        } else {
            nativePermissionResult(requestCode, true)
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun openURL(url: String?): Int {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(url))
            var flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            flags = if (Build.VERSION.SDK_INT >= 21) {
                flags or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            } else {
                flags or Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
            }
            i.addFlags(flags)
            SDLActivity.mSingleton!!.startActivity(i)
        } catch (ex: Exception) {
            return -1
        }
        return 0
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun showToast(
        message: String,
        duration: Int,
        gravity: Int,
        xOffset: Int,
        yOffset: Int
    ): Int {
        if (null == SDLActivity.mSingleton) {
            return -1
        }
        try {
            class OneShotTask(
                var mMessage: String,
                var mDuration: Int,
                var mGravity: Int,
                var mXOffset: Int,
                var mYOffset: Int
            ) : Runnable {
                override fun run() {
                    try {
                        val toast = Toast.makeText(SDLActivity.mSingleton, mMessage, mDuration)
                        if (mGravity >= 0) {
                            toast.setGravity(mGravity, mXOffset, mYOffset)
                        }
                        toast.show()
                    } catch (ex: Exception) {
                        Log.e(TAG, ex.message!!)
                    }
                }
            }
            SDLActivity.mSingleton!!.runOnUiThread(
                OneShotTask(
                    message,
                    duration,
                    gravity,
                    xOffset,
                    yOffset
                )
            )
        } catch (ex: Exception) {
            return -1
        }
        return 0
    }
    // Called by JNI from SDL.
    @JvmStatic
    fun manualBackButton() {
        SDLActivity.mSingleton!!.pressBackButton()
    }

    /* Transition to next state */
    @JvmStatic
    fun handleNativeState() {
        if (SDLActivity.mNextNativeState == SDLActivity.mCurrentNativeState) {
            // Already in same state, discard.
            return
        }

        // Try a transition to init state
        if (SDLActivity.mNextNativeState == SDLActivity.NativeState.INIT) {
            SDLActivity.mCurrentNativeState = SDLActivity.mNextNativeState
            return
        }

        // Try a transition to paused state
        if (SDLActivity.mNextNativeState == SDLActivity.NativeState.PAUSED) {
            if (mSDLThread != null) {
                nativePause()
            }
            if (SDLActivity.mSurface != null) {
                SDLActivity.mSurface!!.handlePause()
            }
            SDLActivity.mCurrentNativeState = SDLActivity.mNextNativeState
            return
        }

        // Try a transition to resumed state
        if (SDLActivity.mNextNativeState == SDLActivity.NativeState.RESUMED) {
            if (SDLActivity.mSurface!!.mIsSurfaceReady && SDLActivity.mHasFocus && SDLActivity.mIsResumedCalled) {
                if (mSDLThread == null) {
                    // This is the entry point to the C app.
                    // Start up the C app thread and enable sensor input for the first time
                    // FIXME: Why aren't we enabling sensor input at start?
                    mSDLThread = Thread(SDLMain(), "SDLThread")
                    SDLActivity.mSurface!!.enableSensor(Sensor.TYPE_ACCELEROMETER, true)
                    mSDLThread!!.start()

                    // No nativeResume(), don't signal Android_ResumeSem
                } else {
                    nativeResume()
                }
                SDLActivity.mSurface!!.handleResume()
                SDLActivity.mCurrentNativeState = SDLActivity.mNextNativeState
            }
        }
    }
    @JvmStatic
    val motionListener: SDLGenericMotionListener_API12?
        get() {
            if (SDLActivity.mMotionListener == null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    SDLActivity.mMotionListener = SDLGenericMotionListener_API26()
                } else if (Build.VERSION.SDK_INT >= 24) {
                    SDLActivity.mMotionListener = SDLGenericMotionListener_API24()
                } else {
                    SDLActivity.mMotionListener = SDLGenericMotionListener_API12()
                }
            }
            return SDLActivity.mMotionListener
        }
}