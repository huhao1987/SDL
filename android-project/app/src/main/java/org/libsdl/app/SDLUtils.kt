package org.libsdl.app

import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.hardware.Sensor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.PointerIcon
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Hashtable
import java.util.Locale

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
    const val HEIGHT_PADDING = 15

    const val SDL_ORIENTATION_UNKNOWN = 0
    const val SDL_ORIENTATION_LANDSCAPE = 1
    const val SDL_ORIENTATION_LANDSCAPE_FLIPPED = 2
    const val SDL_ORIENTATION_PORTRAIT = 3
    const val SDL_ORIENTATION_PORTRAIT_FLIPPED = 4
    var mScreenKeyboardShown = false

    var mTextEdit: DummyEdit? = null
    var mLayout: ViewGroup? = null

    var mClipboardHandler: SDLClipboardHandler? = null

    // Handler for the messages
    var commandHandler: Handler? = null
    var mHIDDeviceManager: HIDDeviceManager? = null

    var mIsResumedCalled = false
    var mHasFocus = true
    val mHasMultiWindow = Build.VERSION.SDK_INT >= 24


    @JvmField
    var mCurrentOrientation = 0
    var mCurrentLocale: Locale? = null

    @JvmField
    var mNextNativeState = SDLUtils.NativeState.INIT
    var mCurrentNativeState = SDLUtils.NativeState.INIT

    /** If shared libraries (e.g. SDL or the native application) could not be loaded.  */
    var mBrokenLibraries = true

    // Main components


    var mCursors: Hashtable<Int, PointerIcon> = Hashtable()
    var mLastCursorID = 0
    var mMotionListener: SDLGenericMotionListener_API12? = null

    lateinit var mSurface: SDLSurface

    enum class NativeState {
        INIT,
        RESUMED,
        PAUSED
    }

    val currentOrientation: Int
        get() {
            var result = SDL_ORIENTATION_UNKNOWN
            val display = (context as AppCompatActivity).windowManager.defaultDisplay
            when (display.rotation) {
                Surface.ROTATION_0 -> result = SDL_ORIENTATION_PORTRAIT
                Surface.ROTATION_90 -> result = SDL_ORIENTATION_LANDSCAPE
                Surface.ROTATION_180 -> result = SDL_ORIENTATION_PORTRAIT_FLIPPED
                Surface.ROTATION_270 -> result = SDL_ORIENTATION_LANDSCAPE_FLIPPED
            }
            return result
        }

    val mainSharedObject: String
        /**
         * This method returns the name of the shared object with the application entry point
         * It can be overridden by derived classes.
         */
        get() {
            val library: String
            library = if (libraries.size > 0) {
                "lib" + libraries[libraries.size - 1] + ".so"
            } else {
                "libmain.so"
            }
            return context?.applicationInfo?.nativeLibraryDir + "/" + library
        }
    val mainFunction: String
        /**
         * This method returns the name of the application entry point
         * It can be overridden by derived classes.
         */
        get() = "SDL_main"

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
    fun init(contac: Context,view: ViewGroup ?= null):SDLUtils {
        this.context = contac
        context?.apply {
            initLibraries(this)
            SDLControllerManager.initialize()

            mClipboardHandler = SDLClipboardHandler(this)
            commandHandler = SDLCommandHandler(context)
            mHIDDeviceManager = HIDDeviceManager.acquire(this)

            // Set up the surface
            mSurface = SDLSurface(this)
            mLayout = RelativeLayout(this)
            mLayout?.addView(mSurface)

            // Get our current screen orientation and pass it down.
            mCurrentOrientation = currentOrientation
            // Only record current orientation
            onNativeOrientationChanged(mCurrentOrientation)
            try {
                if (Build.VERSION.SDK_INT < 24) {
                    mCurrentLocale = resources.configuration.locale
                } else {
                    mCurrentLocale = resources.configuration.locales[0]
                }
            } catch (ignored: Exception) {
            }
            nativeSetupJNI()
            SDLAudioManager.nativeSetupJNI()
            SDLControllerManager.nativeSetupJNI()
            (this as AppCompatActivity).apply {
                lifecycle.addObserver(SDLObserver())
                view?.apply {
                    view.addView(mLayout)
                }?: let {
                    setContentView(mLayout)
                }
                setWindowStyle(false)
                window.decorView.setOnSystemUiVisibilityChangeListener(object :
                    View.OnSystemUiVisibilityChangeListener {
                    override fun onSystemUiVisibilityChange(visibility: Int) {
                        if (mFullscreenModeActive && (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0 || visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0)) {
                            val handler = window.decorView.handler
                            if (handler != null) {
                                handler.removeCallbacks(rehideSystemUi) // Prevent a hide loop.
                                handler.postDelayed(rehideSystemUi, 2000)
                            }
                        }
                    }
                })
                // Get filename from "Open with" of another application
                if (intent != null && intent.data != null) {
                    val filename = intent.data!!.path
                    if (filename != null) {
                        Log.v(TAG, "Got filename: $filename")
                        onNativeDropFile(filename)
                    }
                }
            }
        }
        return this
    }

    fun setLibraries(vararg listofLibries: String):SDLUtils {
        libraries = listofLibries as Array<String>
        return this
    }

    fun setArguments(vararg listofArguments: String):SDLUtils{
        arguments = listofArguments as Array<String>
        return this
    }
    private val rehideSystemUi = Runnable {
        if (Build.VERSION.SDK_INT >= 19) {
            val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.INVISIBLE
            (context as AppCompatActivity).window.decorView.systemUiVisibility = flags
        }
    }


    // Send a message from the SDLMain thread
    fun sendCommand(command: Int, data: Any?): Boolean {
        val msg = commandHandler?.obtainMessage()
        msg?.apply {
            arg1 = command
            obj = data
            val result = commandHandler?.sendMessage(this)
            if (Build.VERSION.SDK_INT >= 19) {
                if (command == COMMAND_CHANGE_WINDOW_STYLE) {
                    // Ensure we don't return until the resize has actually happened,
                    // or 500ms have passed.
                    var bShouldWait = false
                    if (data is Int) {
                        // Let's figure out if we're already laid out fullscreen or not.
                        val display =
                            (context?.getSystemService(AppCompatActivity.WINDOW_SERVICE) as WindowManager).defaultDisplay
                        val realMetrics = DisplayMetrics()
                        display.getRealMetrics(realMetrics)
                        val bFullscreenLayout =
                            realMetrics.widthPixels == mSurface.width && realMetrics.heightPixels == mSurface.height
                        bShouldWait = if (data == 1) {
                            // If we aren't laid out fullscreen or actively in fullscreen mode already, we're going
                            // to change size and should wait for surfaceChanged() before we return, so the size
                            // is right back in native code.  If we're already laid out fullscreen, though, we're
                            // not going to change size even if we change decor modes, so we shouldn't wait for
                            // surfaceChanged() -- which may not even happen -- and should return immediately.
                            !bFullscreenLayout
                        } else {
                            // If we're laid out fullscreen (even if the status bar and nav bar are present),
                            // or are actively in fullscreen, we're going to change size and should wait for
                            // surfaceChanged before we return, so the size is right back in native code.
                            bFullscreenLayout
                        }
                    }
                    if (bShouldWait && context != null) {
                        // We'll wait for the surfaceChanged() method, which will notify us
                        // when called.  That way, we know our current size is really the
                        // size we need, instead of grabbing a size that's still got
                        // the navigation and/or status bars before they're hidden.
                        //
                        // We'll wait for up to half a second, because some devices
                        // take a surprisingly long time for the surface resize, but
                        // then we'll just give up and return.
                        //
                        synchronized(context!!) {
                            try {
                                (context as Object).wait(500)
                            } catch (ie: InterruptedException) {
                                ie.printStackTrace()
                            }
                        }
                    }
                }
            }
            return result ?: false
        }
        return false
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun setActivityTitle(title: String?): Boolean {
        // Called from SDLMain() thread and can't directly affect the view
        return sendCommand(COMMAND_CHANGE_TITLE, title)
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun setWindowStyle(fullscreen: Boolean) {
        // Called from SDLMain() thread and can't directly affect the view
        sendCommand(COMMAND_CHANGE_WINDOW_STYLE, if (fullscreen) 1 else 0)
    }

    /**
     * This can be overridden
     */
    fun setOrientationBis(w: Int, h: Int, resizable: Boolean, hint: String) {
        var orientation_landscape = -1
        var orientation_portrait = -1

        /* If set, hint "explicitly controls which UI orientations are allowed". */if (hint.contains(
                "LandscapeRight"
            ) && hint.contains("LandscapeLeft")
        ) {
            orientation_landscape = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (hint.contains("LandscapeRight")) {
            orientation_landscape = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else if (hint.contains("LandscapeLeft")) {
            orientation_landscape = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
        if (hint.contains("Portrait") && hint.contains("PortraitUpsideDown")) {
            orientation_portrait = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else if (hint.contains("Portrait")) {
            orientation_portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (hint.contains("PortraitUpsideDown")) {
            orientation_portrait = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
        val is_landscape_allowed = orientation_landscape != -1
        val is_portrait_allowed = orientation_portrait != -1
        val req: Int /* Requested orientation */

        /* No valid hint, nothing is explicitly allowed */req =
            if (!is_portrait_allowed && !is_landscape_allowed) {
                if (resizable) {
                    /* All orientations are allowed */
                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                } else {
                    /* Fixed window and nothing specified. Get orientation from w/h of created window */
                    if (w > h) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            } else {
                /* At least one orientation is allowed */
                if (resizable) {
                    if (is_portrait_allowed && is_landscape_allowed) {
                        /* hint allows both landscape and portrait, promote to full sensor */
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    } else {
                        /* Use the only one allowed "orientation" */
                        if (is_landscape_allowed) orientation_landscape else orientation_portrait
                    }
                } else {
                    /* Fixed window and both orientations are allowed. Choose one. */
                    if (is_portrait_allowed && is_landscape_allowed) {
                        if (w > h) orientation_landscape else orientation_portrait
                    } else {
                        /* Use the only one allowed "orientation" */
                        if (is_landscape_allowed) orientation_landscape else orientation_portrait
                    }
                }
            }
        Log.v(
            TAG,
            "setOrientation() requestedOrientation=$req width=$w height=$h resizable=$resizable hint=$hint"
        )
        (context as AppCompatActivity).requestedOrientation = req
    }

    /**
     * This method is called by SDL using JNI.
     * This is a static method for JNI convenience, it calls a non-static method
     * so that is can be overridden
     */
    @JvmStatic
    fun setOrientation(w: Int, h: Int, resizable: Boolean, hint: String) {
        setOrientationBis(w, h, resizable, hint)
    }

    /**
     * This method is called by SDL if SDL did not handle a message itself.
     * This happens if a received message contains an unsupported command.
     * Method can be overwritten to handle Messages in a different class.
     * @param command the command of the message.
     * @param param the parameter of the message. May be null.
     * @return if the message was handled in overridden method.
     */
    fun onUnhandledMessage(command: Int, param: Any?): Boolean {
        return false
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun minimizeWindow() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context?.startActivity(startMain)
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun shouldMinimizeOnFocusLoss(): Boolean {
        if (Build.VERSION.SDK_INT >= 24) {
            if (context == null) {
                return true;
            } else {
                if ((context as AppCompatActivity).isInMultiWindowMode()
                    || (context as AppCompatActivity).isInPictureInPictureMode()
                )
                    return false
            }
        }
        return false
    }

    @JvmStatic
    val isScreenKeyboardShown: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() {
            if (mTextEdit == null) {
                return false
            }
            if (!mScreenKeyboardShown) {
                return false
            }
            val imm =
                context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
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
    fun sendMessage(command: Int, param: Int): Boolean = sendCommand(command, param)


    @JvmStatic
    var context: Context? = null

    @JvmStatic
    val isAndroidTV: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() {
            val uiModeManager =
                context!!.getSystemService(AppCompatActivity.UI_MODE_SERVICE) as UiModeManager
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
    var mFullscreenModeActive = false

    val diagonal: Double
        get() {
            val metrics = DisplayMetrics()
            (context as AppCompatActivity).windowManager.defaultDisplay.getMetrics(metrics)
            val dWidthInches = metrics.widthPixels / metrics.xdpi.toDouble()
            val dHeightInches = metrics.heightPixels / metrics.ydpi.toDouble()
            return Math.sqrt(dWidthInches * dWidthInches + dHeightInches * dHeightInches)
        }

    @JvmStatic
    val isTablet: Boolean
        /**
         * This method is called by SDL using JNI.
         */
        get() =// If our diagonal size is seven inches or greater, we consider ourselves a tablet.
            diagonal >= 7.0

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
        get() = mLayout

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun showTextInput(x: Int, y: Int, w: Int, h: Int): Boolean {
        // Transfer the task to the main thread as a Runnable
        return commandHandler?.post(
            ShowTextInputTask(
                x,
                y,
                w,
                h
            )
        ) ?: false
    }

    internal class ShowTextInputTask(var x: Int, var y: Int, var w: Int, var h: Int) : Runnable {
        init {
            /* Minimum size of 1 pixel, so it takes focus. */if (w <= 0) {
                w = 1
            }
            if (h + HEIGHT_PADDING <= 0) {
                h = 1 - HEIGHT_PADDING
            }
        }

        override fun run() {
            val params = RelativeLayout.LayoutParams(w, h + HEIGHT_PADDING)
            params.leftMargin = x
            params.topMargin = y
            if (mTextEdit == null) {
                mTextEdit = DummyEdit(context)
                mLayout!!.addView(mTextEdit, params)
            } else {
                mTextEdit!!.layoutParams = params
            }
            mTextEdit!!.visibility = View.VISIBLE
            mTextEdit!!.requestFocus()
            val imm =
                context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(mTextEdit, 0)
            mScreenKeyboardShown = true
        }
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
                if (isTextInputEvent(event)) {
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
        get() = mSurface.nativeSurface
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
        return mClipboardHandler?.clipboardHasText() ?: false
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun clipboardGetText(): String? {
        return mClipboardHandler?.clipboardGetText()
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun clipboardSetText(string: String?) {
        mClipboardHandler?.clipboardSetText(string)
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
        ++mLastCursorID
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                mCursors!![mLastCursorID] =
                    PointerIcon.create(bitmap, hotSpotX.toFloat(), hotSpotY.toFloat())
            } catch (e: Exception) {
                return 0
            }
        } else {
            return 0
        }
        return mLastCursorID
    }

    /**
     * This method is called by SDL using JNI.
     */
    @JvmStatic
    fun destroyCustomCursor(cursorID: Int) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                mCursors!!.remove(cursorID)
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
                mSurface.pointerIcon = mCursors!![cursorID]
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
                context?.let {
                    mSurface.pointerIcon =
                        PointerIcon.getSystemIcon(it, cursor_type)
                }
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
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ){
        val result = grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
        nativePermissionResult(requestCode, result)
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
            context?.startActivity(i)
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
        try {
            val toast = Toast.makeText(context, message, duration)
            if (gravity >= 0) {
                toast.setGravity(gravity, xOffset, yOffset)
            }
            toast.show()
        } catch (ex: Exception) {
            Log.e(TAG, ex.message!!)
            return 1
        }
        return 0
    }

    // Called by JNI from SDL.
    @JvmStatic
    fun manualBackButton() {
        (context as AppCompatActivity).onBackPressed()
    }

    /* Transition to next state */
    @JvmStatic
    fun handleNativeState() {
        if (mNextNativeState == mCurrentNativeState) {
            // Already in same state, discard.
            return
        }

        // Try a transition to init state
        if (mNextNativeState == NativeState.INIT) {
            mCurrentNativeState = mNextNativeState
            return
        }

        // Try a transition to paused state
        if (mNextNativeState == NativeState.PAUSED) {
            if (mSDLThread != null) {
                nativePause()
            }
            mSurface.handlePause()
            mCurrentNativeState = mNextNativeState
            return
        }

        // Try a transition to resumed state
        if (mNextNativeState == NativeState.RESUMED) {
            if (mSurface!!.mIsSurfaceReady && mHasFocus && mIsResumedCalled) {
                if (mSDLThread == null) {
                    // This is the entry point to the C app.
                    // Start up the C app thread and enable sensor input for the first time
                    // FIXME: Why aren't we enabling sensor input at start?
                    mSDLThread = Thread(SDLMain(context as AppCompatActivity), "SDLThread")
                    mSurface.enableSensor(Sensor.TYPE_ACCELEROMETER, true)
                    mSDLThread!!.start()

                    // No nativeResume(), don't signal Android_ResumeSem
                } else {
                    nativeResume()
                }
                mSurface.handleResume()
                mCurrentNativeState = mNextNativeState
            }
        }
    }

    /**
     * This method is called by SDL before loading the native shared libraries.
     * It can be overridden to provide names of shared libraries to be loaded.
     * The default implementation returns the defaults. It never returns null.
     * An array returned by a new implementation must at least contain "SDL2".
     * Also keep in mind that the order the libraries are loaded may matter.
     * @return names of shared libraries to be loaded (e.g. "SDL2", "main").
     */
    var libraries = arrayOf(
        "SDL2",
        // "SDL2_image",
        // "SDL2_mixer",
        // "SDL2_net",
        // "SDL2_ttf",
//        "main"
    )

    @JvmStatic
    @Throws(UnsatisfiedLinkError::class, SecurityException::class, NullPointerException::class)
    fun loadLibrary(libraryName: String?) {
        if (libraryName == null) {
            throw NullPointerException("No library name provided.")
        }
        try {
            // Let's see if we have ReLinker available in the project.  This is necessary for
            // some projects that have huge numbers of local libraries bundled, and thus may
            // trip a bug in Android's native library loader which ReLinker works around.  (If
            // loadLibrary works properly, ReLinker will simply use the normal Android method
            // internally.)
            //
            // To use ReLinker, just add it as a dependency.  For more information, see
            // https://github.com/KeepSafe/ReLinker for ReLinker's repository.
            //
            val relinkClass = context?.classLoader?.loadClass("com.getkeepsafe.relinker.ReLinker")
            val relinkListenerClass =
                context?.classLoader?.loadClass("com.getkeepsafe.relinker.ReLinker\$LoadListener")
            val contextClass = context?.classLoader?.loadClass("android.content.Context")
            val stringClass = context?.classLoader?.loadClass("java.lang.String")

            // Get a 'force' instance of the ReLinker, so we can ensure libraries are reinstalled if
            // they've changed during updates.
            val forceMethod = relinkClass?.getDeclaredMethod("force")
            val relinkInstance = forceMethod?.invoke(null)
            val relinkInstanceClass: Class<*> = relinkInstance?.javaClass!!

            // Actually load the library!
            val loadMethod = relinkInstanceClass.getDeclaredMethod(
                "loadLibrary",
                contextClass,
                stringClass,
                stringClass,
                relinkListenerClass
            )
            context?.let {
                loadMethod.invoke(relinkInstance, it, libraryName, null, null)
            }
        } catch (e: Throwable) {
            // Fall back
            try {
                System.loadLibrary(libraryName)
            } catch (ule: UnsatisfiedLinkError) {
                throw ule
            } catch (se: SecurityException) {
                throw se
            }
        }
    }

    // Load the .so
    fun loadLibraries() {
        for (lib in libraries) {
            loadLibrary(lib)
        }
    }

    fun initLibraries(context: Context) {
        runCatching {
            loadLibraries()
        }
            .onFailure {
                val dlgAlert = AlertDialog.Builder(context)
                dlgAlert.setMessage(
                    "An error occurred while trying to start the application. Please try again and/or reinstall."
                            + System.getProperty("line.separator")
                            + System.getProperty("line.separator")
                            + "Error: " + it.message
                )
                dlgAlert.setTitle("SDL Error")
                dlgAlert.setPositiveButton(
                    "Exit"
                ) { dialog, id -> // if this button is clicked, close current activity
                    (context as AppCompatActivity).finish()
                }
                dlgAlert.setCancelable(false)
                dlgAlert.create().show()
            }
    }

    fun pauseNativeThread() {
        mNextNativeState = NativeState.PAUSED
        if (mBrokenLibraries) {
            return
        }
        handleNativeState()
    }

    open fun resumeNativeThread() {
        mNextNativeState = NativeState.RESUMED
        mIsResumedCalled = true
        if (mBrokenLibraries) {
            return
        }
        handleNativeState()
    }

    // Messagebox
    /** Result of current messagebox. Also used for blocking the calling thread.  */
    private val messageboxSelection = IntArray(1)

    /**
     * This method is called by SDL using JNI.
     * Shows the messagebox from UI thread and block calling thread.
     * buttonFlags, buttonIds and buttonTexts must have same length.
     * @param buttonFlags array containing flags for every button.
     * @param buttonIds array containing id for every button.
     * @param buttonTexts array containing text for every button.
     * @param colors null for default or array of length 5 containing colors.
     * @return button id or -1.
     */
    fun messageboxShowMessageBox(
        flags: Int,
        title: String?,
        message: String?,
        buttonFlags: IntArray,
        buttonIds: IntArray,
        buttonTexts: Array<String?>,
        colors: IntArray?
    ): Int {
        messageboxSelection[0] = -1

        // sanity checks
        if (buttonFlags.size != buttonIds.size && buttonIds.size != buttonTexts.size) {
            return -1 // implementation broken
        }

        // collect arguments for Dialog
        val args = Bundle()
        args.putInt("flags", flags)
        args.putString("title", title)
        args.putString("message", message)
        args.putIntArray("buttonFlags", buttonFlags)
        args.putIntArray("buttonIds", buttonIds)
        args.putStringArray("buttonTexts", buttonTexts)
        args.putIntArray("colors", colors)
        messageboxCreateAndShow(args)
        // block the calling thread
        synchronized(messageboxSelection) {
            try {
                (context as Object).wait()
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
                return -1
            }
        }

        // return selected value
        return messageboxSelection[0]
    }

    fun messageboxCreateAndShow(args: Bundle) {

        // TODO set values from "flags" to messagebox dialog

        // get colors
        val colors = args.getIntArray("colors")
        val backgroundColor: Int
        val textColor: Int
        val buttonBorderColor: Int
        val buttonBackgroundColor: Int
        val buttonSelectedColor: Int
        if (colors != null) {
            var i = -1
            backgroundColor = colors[++i]
            textColor = colors[++i]
            buttonBorderColor = colors[++i]
            buttonBackgroundColor = colors[++i]
            buttonSelectedColor = colors[++i]
        } else {
            backgroundColor = Color.TRANSPARENT
            textColor = Color.TRANSPARENT
            buttonBorderColor = Color.TRANSPARENT
            buttonBackgroundColor = Color.TRANSPARENT
            buttonSelectedColor = Color.TRANSPARENT
        }
        context?.apply {
            // create dialog with title and a listener to wake up calling thread
            val dialog = AlertDialog.Builder(this).create()
            dialog.setTitle(args.getString("title"))
            dialog.setCancelable(false)
            dialog.setOnDismissListener { synchronized(messageboxSelection) { (messageboxSelection as Object).notify() } }

            // create text
            val message = TextView(this)
            message.gravity = Gravity.CENTER
            message.text = args.getString("message")
            if (textColor != Color.TRANSPARENT) {
                message.setTextColor(textColor)
            }

            // create buttons
            val buttonFlags = args.getIntArray("buttonFlags")
            val buttonIds = args.getIntArray("buttonIds")
            val buttonTexts = args.getStringArray("buttonTexts")
            val mapping = SparseArray<Button>()
            val buttons = LinearLayout(this)
            buttons.orientation = LinearLayout.HORIZONTAL
            buttons.gravity = Gravity.CENTER
            for (i in buttonTexts!!.indices) {
                val button = Button(this)
                val id = buttonIds!![i]
                button.setOnClickListener {
                    messageboxSelection[0] = id
                    dialog.dismiss()
                }
                if (buttonFlags!![i] != 0) {
                    // see SDL_messagebox.h
                    if (buttonFlags[i] and 0x00000001 != 0) {
                        mapping.put(KeyEvent.KEYCODE_ENTER, button)
                    }
                    if (buttonFlags[i] and 0x00000002 != 0) {
                        mapping.put(KeyEvent.KEYCODE_ESCAPE, button) /* API 11 */
                    }
                }
                button.text = buttonTexts[i]
                if (textColor != Color.TRANSPARENT) {
                    button.setTextColor(textColor)
                }
                if (buttonBorderColor != Color.TRANSPARENT) {
                    // TODO set color for border of messagebox button
                }
                if (buttonBackgroundColor != Color.TRANSPARENT) {
                    val drawable = button.background
                    if (drawable == null) {
                        // setting the color this way removes the style
                        button.setBackgroundColor(buttonBackgroundColor)
                    } else {
                        // setting the color this way keeps the style (gradient, padding, etc.)
                        drawable.setColorFilter(buttonBackgroundColor, PorterDuff.Mode.MULTIPLY)
                    }
                }
                if (buttonSelectedColor != Color.TRANSPARENT) {
                    // TODO set color for selected messagebox button
                }
                buttons.addView(button)
            }

            // create content
            val content = LinearLayout(this)
            content.orientation = LinearLayout.VERTICAL
            content.addView(message)
            content.addView(buttons)
            if (backgroundColor != Color.TRANSPARENT) {
                content.setBackgroundColor(backgroundColor)
            }

            // add content to dialog and return
            dialog.setView(content)
            dialog.setOnKeyListener(DialogInterface.OnKeyListener { d, keyCode, event ->
                val button = mapping[keyCode]
                if (button != null) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        button.performClick()
                    }
                    return@OnKeyListener true // also for ignored actions
                }
                false
            })
            dialog.show()
        }
    }


    var arguments: Array<String> = arrayOf()
    fun isTextInputEvent(event: KeyEvent): Boolean {

        // Key pressed with Ctrl should be sent as SDL_KEYDOWN/SDL_KEYUP and not SDL_TEXTINPUT
        return if (event.isCtrlPressed) {
            false
        } else event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE
    }

    fun onWIndowFocusChanged(hasFocus: Boolean){
        if (mBrokenLibraries) {
            return
        }
        mHasFocus = hasFocus
        if (hasFocus) {
            mNextNativeState = NativeState.RESUMED
            motionListener!!.reclaimRelativeMouseModeIfNeeded()
            handleNativeState()
            nativeFocusChanged(true)
        } else {
            nativeFocusChanged(false)
            if (!mHasMultiWindow) {
                mNextNativeState = NativeState.PAUSED
                handleNativeState()
            }
        }
    }

    fun onLowMemory(){
        Log.v(TAG, "onLowMemory()")
        if (mBrokenLibraries) {
            return
        }
        nativeLowMemory()
    }

    fun onConfigurationChanged(configration: Configuration){
        Log.v(TAG, "onConfigurationChanged()")
        if (mBrokenLibraries) {
            return
        }
        if (mCurrentLocale == null || mCurrentLocale != configration.locale) {
            mCurrentLocale = configration.locale
            onNativeLocaleChanged()
        }
    }

    fun onBackPressed(){
        // Check if we want to block the back button in case of mouse right click.
        //
        // If we do, the normal hardware back button will no longer work and people have to use home,
        // but the mouse right click will work.
        //
        val trapBack = nativeGetHintBoolean("SDL_ANDROID_TRAP_BACK_BUTTON", false)
        if (trapBack) {
            // Exit and let the mouse handler handle this button (if appropriate)
            return
        }

        // Default system back button behavior.
        if ((context as AppCompatActivity).isFinishing) {
            (context as AppCompatActivity).onBackPressed()
        }
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean{
        if (mBrokenLibraries) {
            return false
        }
        val keyCode = event.keyCode
        return if (
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            || keyCode == KeyEvent.KEYCODE_VOLUME_UP
            || keyCode == KeyEvent.KEYCODE_CAMERA
            || keyCode == KeyEvent.KEYCODE_ZOOM_IN
            || keyCode == KeyEvent.KEYCODE_ZOOM_OUT
        ) {
            false
        } else (context as AppCompatActivity).dispatchKeyEvent(event)
    }

    @JvmStatic
    val motionListener: SDLGenericMotionListener_API12?
        get() {
            if (mMotionListener == null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    mMotionListener = SDLGenericMotionListener_API26()
                } else if (Build.VERSION.SDK_INT >= 24) {
                    mMotionListener = SDLGenericMotionListener_API24()
                } else {
                    mMotionListener = SDLGenericMotionListener_API12()
                }
            }
            return mMotionListener
        }
}