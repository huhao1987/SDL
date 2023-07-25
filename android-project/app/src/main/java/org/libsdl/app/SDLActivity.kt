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
import android.os.Message
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.PointerIcon
import android.view.Surface
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
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
import org.libsdl.app.HIDDeviceManager.Companion.acquire
import org.libsdl.app.HIDDeviceManager.Companion.release
import org.libsdl.app.SDL.getContext
import org.libsdl.app.SDL.loadLibrary
import org.libsdl.app.SDL.setContext
import org.libsdl.app.SDL.setupJNI
import org.libsdl.app.SDLControllerManager.isDeviceSDLJoystick
import org.libsdl.app.SDLControllerManager.onNativePadDown
import org.libsdl.app.SDLControllerManager.onNativePadUp
import org.libsdl.app.SDLInputConnection.Companion.nativeCommitText
import org.libsdl.app.SDLUtils.handleNativeState
import org.libsdl.app.SDLUtils.motionListener
import org.libsdl.app.SDLUtils.nativeFocusChanged
import org.libsdl.app.SDLUtils.nativeGetHintBoolean
import org.libsdl.app.SDLUtils.nativeGetVersion
import org.libsdl.app.SDLUtils.nativeLowMemory
import org.libsdl.app.SDLUtils.nativePause
import org.libsdl.app.SDLUtils.nativePermissionResult
import org.libsdl.app.SDLUtils.nativeQuit
import org.libsdl.app.SDLUtils.nativeResume
import org.libsdl.app.SDLUtils.nativeSendQuit
import org.libsdl.app.SDLUtils.onNativeDropFile
import org.libsdl.app.SDLUtils.onNativeLocaleChanged
import org.libsdl.app.SDLUtils.onNativeOrientationChanged
import org.libsdl.app.SDLUtils.setWindowStyle
import java.util.Hashtable
import java.util.Locale
import java.util.Objects

/**
 * SDL Activity
 */
open class SDLActivity : AppCompatActivity(), OnSystemUiVisibilityChangeListener {
    // Handle the state of the native layer
    enum class NativeState {
        INIT,
        RESUMED,
        PAUSED
    }

    val mainSharedObject: String
        /**
         * This method returns the name of the shared object with the application entry point
         * It can be overridden by derived classes.
         */
        get() {
            val library: String
            val libraries = mSingleton!!.libraries
            library = if (libraries.size > 0) {
                "lib" + libraries[libraries.size - 1] + ".so"
            } else {
                "libmain.so"
            }
            return context!!.applicationInfo.nativeLibraryDir + "/" + library
        }
    val mainFunction: String
        /**
         * This method returns the name of the application entry point
         * It can be overridden by derived classes.
         */
        get() = "SDL_main"
    protected val libraries: Array<String>
        /**
         * This method is called by SDL before loading the native shared libraries.
         * It can be overridden to provide names of shared libraries to be loaded.
         * The default implementation returns the defaults. It never returns null.
         * An array returned by a new implementation must at least contain "SDL2".
         * Also keep in mind that the order the libraries are loaded may matter.
         * @return names of shared libraries to be loaded (e.g. "SDL2", "main").
         */
        protected get() = arrayOf(
            "SDL2",  // "SDL2_image",
            // "SDL2_mixer",
            // "SDL2_net",
            // "SDL2_ttf",
            "main"
        )

    // Load the .so
    fun loadLibraries() {
        for (lib in libraries) {
            loadLibrary(lib)
        }
    }

    open fun getArguments(): Array<String> {
        return arrayOf()
    }

    protected fun createSDLSurface(context: Context): SDLSurface {
        return SDLSurface(context)
    }

    // Setup
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "Device: " + Build.DEVICE)
        Log.v(TAG, "Model: " + Build.MODEL)
        Log.v(TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        try {
            Thread.currentThread().name = "SDLActivity"
        } catch (e: Exception) {
            Log.v(TAG, "modify thread properties failed $e")
        }

        // Load shared libraries
        var errorMsgBrokenLib = ""
        try {
            loadLibraries()
            mBrokenLibraries = false /* success */
        } catch (e: UnsatisfiedLinkError) {
            System.err.println(e.message)
            mBrokenLibraries = true
            errorMsgBrokenLib = e.message ?: ""
        } catch (e: Exception) {
            System.err.println(e.message)
            mBrokenLibraries = true
            errorMsgBrokenLib = e.message ?: ""
        }
        if (!mBrokenLibraries) {
            val expected_version =
                SDL_MAJOR_VERSION.toString() + "." + SDL_MINOR_VERSION.toString() + "." + SDL_MICRO_VERSION.toString()
            val version = nativeGetVersion()
            if (version != expected_version) {
                mBrokenLibraries = true
                errorMsgBrokenLib =
                    "SDL C/Java version mismatch (expected $expected_version, got $version)"
            }
        }
        if (mBrokenLibraries) {
            mSingleton = this
            val dlgAlert = AlertDialog.Builder(this)
            dlgAlert.setMessage(
                "An error occurred while trying to start the application. Please try again and/or reinstall."
                        + System.getProperty("line.separator")
                        + System.getProperty("line.separator")
                        + "Error: " + errorMsgBrokenLib
            )
            dlgAlert.setTitle("SDL Error")
            dlgAlert.setPositiveButton(
                "Exit"
            ) { dialog, id -> // if this button is clicked, close current activity
                mSingleton!!.finish()
            }
            dlgAlert.setCancelable(false)
            dlgAlert.create().show()
            return
        }

        // Set up JNI
        setupJNI()

        // Initialize state
        SDL.initialize()

        // So we can call stuff from static callbacks
        mSingleton = this
        setContext(this)
        mClipboardHandler = SDLClipboardHandler()
        mHIDDeviceManager = acquire(this)

        // Set up the surface
        mSurface = createSDLSurface(application)
        mLayout = RelativeLayout(this)
        mLayout?.addView(mSurface)

        // Get our current screen orientation and pass it down.
        mCurrentOrientation = currentOrientation
        // Only record current orientation
        onNativeOrientationChanged(mCurrentOrientation)
        try {
            if (Build.VERSION.SDK_INT < 24) {
                mCurrentLocale = context!!.resources.configuration.locale
            } else {
                mCurrentLocale = context!!.resources.configuration.locales[0]
            }
        } catch (ignored: Exception) {
        }
        setContentView(mLayout)
        setWindowStyle(false)
        window.decorView.setOnSystemUiVisibilityChangeListener(this)

        // Get filename from "Open with" of another application
        val intent = intent
        if (intent != null && intent.data != null) {
            val filename = intent.data!!.path
            if (filename != null) {
                Log.v(TAG, "Got filename: $filename")
                onNativeDropFile(filename)
            }
        }
    }

    protected fun pauseNativeThread() {
        mNextNativeState = NativeState.PAUSED
        mIsResumedCalled = false
        if (mBrokenLibraries) {
            return
        }
        handleNativeState()
    }

    protected open fun resumeNativeThread() {
        mNextNativeState = NativeState.RESUMED
        mIsResumedCalled = true
        if (mBrokenLibraries) {
            return
        }
        handleNativeState()
    }

    // Events
    override fun onPause() {
        Log.v(TAG, "onPause()")
        super.onPause()
        if (mHIDDeviceManager != null) {
            mHIDDeviceManager!!.setFrozen(true)
        }
        if (!mHasMultiWindow) {
            pauseNativeThread()
        }
    }

    override fun onResume() {
        Log.v(TAG, "onResume()")
        super.onResume()
        if (mHIDDeviceManager != null) {
            mHIDDeviceManager!!.setFrozen(false)
        }
        if (!mHasMultiWindow) {
            resumeNativeThread()
        }
    }

    override fun onStop() {
        Log.v(TAG, "onStop()")
        super.onStop()
        if (mHasMultiWindow) {
            pauseNativeThread()
        }
    }

    override fun onStart() {
        Log.v(TAG, "onStart()")
        super.onStart()
        if (mHasMultiWindow) {
            resumeNativeThread()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.v(TAG, "onWindowFocusChanged(): $hasFocus")
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

    override fun onLowMemory() {
        Log.v(TAG, "onLowMemory()")
        super.onLowMemory()
        if (mBrokenLibraries) {
            return
        }
        nativeLowMemory()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.v(TAG, "onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
        if (mBrokenLibraries) {
            return
        }
        if (mCurrentLocale == null || mCurrentLocale != newConfig.locale) {
            mCurrentLocale = newConfig.locale
            onNativeLocaleChanged()
        }
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy()")
        if (mHIDDeviceManager != null) {
            release(mHIDDeviceManager!!)
            mHIDDeviceManager = null
        }
        if (mBrokenLibraries) {
            super.onDestroy()
            return
        }
        if (mSDLThread != null) {

            // Send Quit event to "SDLThread" thread
            nativeSendQuit()

            // Wait for "SDLThread" thread to end
            try {
                mSDLThread!!.join()
            } catch (e: Exception) {
                Log.v(TAG, "Problem stopping SDLThread: $e")
            }
        }
        nativeQuit()
        super.onDestroy()
    }

    override fun onBackPressed() {
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
        if (!isFinishing) {
            super.onBackPressed()
        }
    }

    // Used to get us onto the activity's main thread
    fun pressBackButton() {
        runOnUiThread {
            if (!this@SDLActivity.isFinishing) {
                superOnBackPressed()
            }
        }
    }

    // Used to access the system back behavior.
    fun superOnBackPressed() {
        super.onBackPressed()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mBrokenLibraries) {
            return false
        }
        val keyCode = event.keyCode
        // Ignore certain special keys so they're handled by Android
        return if (/* API 11 */keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_ZOOM_IN || keyCode == KeyEvent.KEYCODE_ZOOM_OUT
        ) {
            false
        } else super.dispatchKeyEvent(event)
    }

    /**
     * This method is called by SDL if SDL did not handle a message itself.
     * This happens if a received message contains an unsupported command.
     * Method can be overwritten to handle Messages in a different class.
     * @param command the command of the message.
     * @param param the parameter of the message. May be null.
     * @return if the message was handled in overridden method.
     */
    protected fun onUnhandledMessage(command: Int, param: Any?): Boolean {
        return false
    }

    /**
     * A Handler class for Messages from native SDL applications.
     * It uses current Activities as target (e.g. for the title).
     * static to prevent implicit references to enclosing object.
     */
    protected class SDLCommandHandler : Handler() {
        override fun handleMessage(msg: Message) {
            val context = getContext()
            if (context == null) {
                Log.e(TAG, "error handling message, getContext() returned null")
                return
            }
            when (msg.arg1) {
                COMMAND_CHANGE_TITLE -> if (context is Activity) {
                    context.title = msg.obj as String
                } else {
                    Log.e(TAG, "error handling message, getContext() returned no Activity")
                }

                COMMAND_CHANGE_WINDOW_STYLE -> if (Build.VERSION.SDK_INT >= 19) {
                    if (context is Activity) {
                        val window = context.window
                        if (window != null) {
                            if (msg.obj is Int && msg.obj as Int != 0) {
                                val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or
                                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.INVISIBLE
                                window.decorView.systemUiVisibility = flags
                                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                                window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                                mFullscreenModeActive = true
                            } else {
                                val flags =
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_VISIBLE
                                window.decorView.systemUiVisibility = flags
                                window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                                mFullscreenModeActive = false
                            }
                        }
                    } else {
                        Log.e(TAG, "error handling message, getContext() returned no Activity")
                    }
                }

                COMMAND_TEXTEDIT_HIDE -> if (mTextEdit != null) {
                    // Note: On some devices setting view to GONE creates a flicker in landscape.
                    // Setting the View's sizes to 0 is similar to GONE but without the flicker.
                    // The sizes will be set to useful values when the keyboard is shown again.
                    mTextEdit!!.layoutParams = RelativeLayout.LayoutParams(0, 0)
                    val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(mTextEdit!!.windowToken, 0)
                    mScreenKeyboardShown = false
                    mSurface!!.requestFocus()
                }

                COMMAND_SET_KEEP_SCREEN_ON -> {
                    if (context is Activity) {
                        val window = context.window
                        if (window != null) {
                            if (msg.obj is Int && msg.obj as Int != 0) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                    }
                }

                else -> if (context is SDLActivity && !context.onUnhandledMessage(
                        msg.arg1,
                        msg.obj
                    )
                ) {
                    Log.e(TAG, "error handling message, command is " + msg.arg1)
                }
            }
        }
    }

    // Handler for the messages
    var commandHandler: Handler = SDLCommandHandler()

    // Send a message from the SDLMain thread
    fun sendCommand(command: Int, data: Any?): Boolean {
        val msg = commandHandler.obtainMessage()
        msg.arg1 = command
        msg.obj = data
        val result = commandHandler.sendMessage(msg)
        if (Build.VERSION.SDK_INT >= 19) {
            if (command == COMMAND_CHANGE_WINDOW_STYLE) {
                // Ensure we don't return until the resize has actually happened,
                // or 500ms have passed.
                var bShouldWait = false
                if (data is Int) {
                    // Let's figure out if we're already laid out fullscreen or not.
                    val display = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
                    val realMetrics = DisplayMetrics()
                    display.getRealMetrics(realMetrics)
                    val bFullscreenLayout =
                        realMetrics.widthPixels == mSurface!!.width && realMetrics.heightPixels == mSurface!!.height
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
        return result
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
        mSingleton!!.requestedOrientation = req
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
                mTextEdit = DummyEdit(getContext())
                mLayout!!.addView(mTextEdit, params)
            } else {
                mTextEdit!!.layoutParams = params
            }
            mTextEdit!!.visibility = View.VISIBLE
            mTextEdit!!.requestFocus()
            val imm = getContext()!!.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(mTextEdit, 0)
            mScreenKeyboardShown = true
        }

        companion object {
            /*
         * This is used to regulate the pan&scan method to have some offset from
         * the bottom edge of the input region and the top edge of an input
         * method (soft keyboard)
         */
            const val HEIGHT_PADDING = 15
        }
    }
    // Messagebox
    /** Result of current messagebox. Also used for blocking the calling thread.  */
    protected val messageboxSelection = IntArray(1)

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

        // trigger Dialog creation on UI thread
        runOnUiThread { messageboxCreateAndShow(args) }

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

    protected fun messageboxCreateAndShow(args: Bundle) {

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

    private val rehideSystemUi = Runnable {
        if (Build.VERSION.SDK_INT >= 19) {
            val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.INVISIBLE
            this@SDLActivity.window.decorView.systemUiVisibility = flags
        }
    }

    override fun onSystemUiVisibilityChange(visibility: Int) {
        if (mFullscreenModeActive && (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0 || visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0)) {
            val handler = window.decorView.handler
            if (handler != null) {
                handler.removeCallbacks(rehideSystemUi) // Prevent a hide loop.
                handler.postDelayed(rehideSystemUi, 2000)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val result = grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
        nativePermissionResult(requestCode, result)
    }

    companion object {
        private const val TAG = "SDL"
        private const val SDL_MAJOR_VERSION = 2
        private const val SDL_MINOR_VERSION = 26
        private const val SDL_MICRO_VERSION = 5
        @JvmStatic
        val context: Context?
            /**
             * This method is called by SDL using JNI.
             */
            get() = SDL.getContext()
        /*
    // Display InputType.SOURCE/CLASS of events and devices
    //
    // SDLActivity.debugSource(device.getSources(), "device[" + device.getName() + "]");
    // SDLActivity.debugSource(event.getSource(), "event");
    public static void debugSource(int sources, String prefix) {
        int s = sources;
        int s_copy = sources;
        String cls = "";
        String src = "";
        int tst = 0;
        int FLAG_TAINTED = 0x80000000;

        if ((s & InputDevice.SOURCE_CLASS_BUTTON) != 0)     cls += " BUTTON";
        if ((s & InputDevice.SOURCE_CLASS_JOYSTICK) != 0)   cls += " JOYSTICK";
        if ((s & InputDevice.SOURCE_CLASS_POINTER) != 0)    cls += " POINTER";
        if ((s & InputDevice.SOURCE_CLASS_POSITION) != 0)   cls += " POSITION";
        if ((s & InputDevice.SOURCE_CLASS_TRACKBALL) != 0)  cls += " TRACKBALL";


        int s2 = s_copy & ~InputDevice.SOURCE_ANY; // keep class bits
        s2 &= ~(  InputDevice.SOURCE_CLASS_BUTTON
                | InputDevice.SOURCE_CLASS_JOYSTICK
                | InputDevice.SOURCE_CLASS_POINTER
                | InputDevice.SOURCE_CLASS_POSITION
                | InputDevice.SOURCE_CLASS_TRACKBALL);

        if (s2 != 0) cls += "Some_Unkown";

        s2 = s_copy & InputDevice.SOURCE_ANY; // keep source only, no class;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tst = InputDevice.SOURCE_BLUETOOTH_STYLUS;
            if ((s & tst) == tst) src += " BLUETOOTH_STYLUS";
            s2 &= ~tst;
        }

        tst = InputDevice.SOURCE_DPAD;
        if ((s & tst) == tst) src += " DPAD";
        s2 &= ~tst;

        tst = InputDevice.SOURCE_GAMEPAD;
        if ((s & tst) == tst) src += " GAMEPAD";
        s2 &= ~tst;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tst = InputDevice.SOURCE_HDMI;
            if ((s & tst) == tst) src += " HDMI";
            s2 &= ~tst;
        }

        tst = InputDevice.SOURCE_JOYSTICK;
        if ((s & tst) == tst) src += " JOYSTICK";
        s2 &= ~tst;

        tst = InputDevice.SOURCE_KEYBOARD;
        if ((s & tst) == tst) src += " KEYBOARD";
        s2 &= ~tst;

        tst = InputDevice.SOURCE_MOUSE;
        if ((s & tst) == tst) src += " MOUSE";
        s2 &= ~tst;

        if (Build.VERSION.SDK_INT >= 26) {
            tst = InputDevice.SOURCE_MOUSE_RELATIVE;
            if ((s & tst) == tst) src += " MOUSE_RELATIVE";
            s2 &= ~tst;

            tst = InputDevice.SOURCE_ROTARY_ENCODER;
            if ((s & tst) == tst) src += " ROTARY_ENCODER";
            s2 &= ~tst;
        }
        tst = InputDevice.SOURCE_STYLUS;
        if ((s & tst) == tst) src += " STYLUS";
        s2 &= ~tst;

        tst = InputDevice.SOURCE_TOUCHPAD;
        if ((s & tst) == tst) src += " TOUCHPAD";
        s2 &= ~tst;

        tst = InputDevice.SOURCE_TOUCHSCREEN;
        if ((s & tst) == tst) src += " TOUCHSCREEN";
        s2 &= ~tst;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            tst = InputDevice.SOURCE_TOUCH_NAVIGATION;
            if ((s & tst) == tst) src += " TOUCH_NAVIGATION";
            s2 &= ~tst;
        }

        tst = InputDevice.SOURCE_TRACKBALL;
        if ((s & tst) == tst) src += " TRACKBALL";
        s2 &= ~tst;

        tst = InputDevice.SOURCE_ANY;
        if ((s & tst) == tst) src += " ANY";
        s2 &= ~tst;

        if (s == FLAG_TAINTED) src += " FLAG_TAINTED";
        s2 &= ~FLAG_TAINTED;

        if (s2 != 0) src += " Some_Unkown";

        Log.v(TAG, prefix + "int=" + s_copy + " CLASS={" + cls + " } source(s):" + src);
    }
*/
        var mIsResumedCalled = false
        var mHasFocus = false
        val mHasMultiWindow = Build.VERSION.SDK_INT >= 24


        protected const val SDL_ORIENTATION_UNKNOWN = 0
        const val SDL_ORIENTATION_LANDSCAPE = 1
        const val SDL_ORIENTATION_LANDSCAPE_FLIPPED = 2
        const val SDL_ORIENTATION_PORTRAIT = 3
        const val SDL_ORIENTATION_PORTRAIT_FLIPPED = 4

        @JvmField
        var mCurrentOrientation = 0
        protected var mCurrentLocale: Locale? = null

        @JvmField
        var mNextNativeState: NativeState? = null
        var mCurrentNativeState: NativeState? = null

        /** If shared libraries (e.g. SDL or the native application) could not be loaded.  */
        var mBrokenLibraries = true

        // Main components
        @JvmField
        var mSingleton: SDLActivity? = null
        var mSurface: SDLSurface? = null
        var mTextEdit: DummyEdit? = null
        var mScreenKeyboardShown = false
        var mLayout: ViewGroup? = null
        var mClipboardHandler: SDLClipboardHandler? = null
        var mCursors: Hashtable<Int, PointerIcon>? = null
        var mLastCursorID = 0
        var mMotionListener: SDLGenericMotionListener_API12? = null
        var mHIDDeviceManager: HIDDeviceManager? = null

        // This is what SDL runs in. It invokes SDL_main(), eventually
        @JvmField
        var mSDLThread: Thread? = null



        fun initialize() {
            // The static nature of the singleton and Android quirkyness force us to initialize everything here
            // Otherwise, when exiting the app and returning to it, these variables *keep* their pre exit values
            mSingleton = null
            mSurface = null
            mTextEdit = null
            mLayout = null
            mClipboardHandler = null
            mCursors = Hashtable()
            mLastCursorID = 0
            mSDLThread = null
            mIsResumedCalled = false
            mHasFocus = true
            mNextNativeState = NativeState.INIT
            mCurrentNativeState = NativeState.INIT
        }

        val currentOrientation: Int
            get() {
                var result = SDL_ORIENTATION_UNKNOWN
                val activity = context as Activity? ?: return result
                val display = activity.windowManager.defaultDisplay
                when (display.rotation) {
                    Surface.ROTATION_0 -> result = SDL_ORIENTATION_PORTRAIT
                    Surface.ROTATION_90 -> result = SDL_ORIENTATION_LANDSCAPE
                    Surface.ROTATION_180 -> result = SDL_ORIENTATION_PORTRAIT_FLIPPED
                    Surface.ROTATION_270 -> result = SDL_ORIENTATION_LANDSCAPE_FLIPPED
                }
                return result
            }



        // Messages from the SDLMain thread
        const val COMMAND_CHANGE_TITLE = 1
        const val COMMAND_CHANGE_WINDOW_STYLE = 2
        const val COMMAND_TEXTEDIT_HIDE = 3
        const val COMMAND_SET_KEEP_SCREEN_ON = 5
        protected const val COMMAND_USER = 0x8000
        var mFullscreenModeActive = false


        val diagonal: Double
            get() {
                val metrics = DisplayMetrics()
                val activity = context as Activity? ?: return 0.0
                activity.windowManager.defaultDisplay.getMetrics(metrics)
                val dWidthInches = metrics.widthPixels / metrics.xdpi.toDouble()
                val dHeightInches = metrics.heightPixels / metrics.ydpi.toDouble()
                return Math.sqrt(dWidthInches * dWidthInches + dHeightInches * dHeightInches)
            }



        fun isTextInputEvent(event: KeyEvent): Boolean {

            // Key pressed with Ctrl should be sent as SDL_KEYDOWN/SDL_KEYUP and not SDL_TEXTINPUT
            return if (event.isCtrlPressed) {
                false
            } else event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE
        }


    }
}
