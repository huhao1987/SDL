package org.libsdl.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.Gravity
import android.view.KeyEvent
import android.view.PointerIcon
import android.view.Surface
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.libsdl.app.HIDDeviceManager.Companion.acquire
import org.libsdl.app.HIDDeviceManager.Companion.release
import org.libsdl.app.SDL.getContext
import org.libsdl.app.SDL.loadLibrary
import org.libsdl.app.SDL.setContext
import org.libsdl.app.SDL.setupJNI
import org.libsdl.app.SDLUtils.COMMAND_CHANGE_WINDOW_STYLE
import org.libsdl.app.SDLUtils.currentOrientation
import org.libsdl.app.SDLUtils.handleNativeState
import org.libsdl.app.SDLUtils.initLibraries
import org.libsdl.app.SDLUtils.libraries
import org.libsdl.app.SDLUtils.loadLibraries
import org.libsdl.app.SDLUtils.mFullscreenModeActive
import org.libsdl.app.SDLUtils.mSDLThread
import org.libsdl.app.SDLUtils.motionListener
import org.libsdl.app.SDLUtils.nativeFocusChanged
import org.libsdl.app.SDLUtils.nativeGetHintBoolean
import org.libsdl.app.SDLUtils.nativeGetVersion
import org.libsdl.app.SDLUtils.nativeLowMemory
import org.libsdl.app.SDLUtils.nativePermissionResult
import org.libsdl.app.SDLUtils.nativeQuit
import org.libsdl.app.SDLUtils.nativeSendQuit
import org.libsdl.app.SDLUtils.onNativeDropFile
import org.libsdl.app.SDLUtils.onNativeLocaleChanged
import org.libsdl.app.SDLUtils.onNativeOrientationChanged
import org.libsdl.app.SDLUtils.pauseNativeThread
import org.libsdl.app.SDLUtils.resumeNativeThread
import org.libsdl.app.SDLUtils.setWindowStyle
import java.util.Hashtable
import java.util.Locale

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

    init {
        SDLUtils.arguments
    }
    protected fun createSDLSurface(context: Context): SDLSurface {
        return SDLSurface(this)
    }

    // Setup
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "Device: " + Build.DEVICE)
        Log.v(TAG, "Model: " + Build.MODEL)
        Log.v(TAG, "onCreate()")
        super.onCreate(savedInstanceState)

        initLibraries(this)

        // Set up JNI
        setupJNI()

        // Initialize state
        SDL.initialize()

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


        @JvmField
        var mCurrentOrientation = 0
        protected var mCurrentLocale: Locale? = null

        @JvmField
        var mNextNativeState: NativeState? = null
        var mCurrentNativeState: NativeState? = null

        /** If shared libraries (e.g. SDL or the native application) could not be loaded.  */
        var mBrokenLibraries = true

        // Main components
        var mSurface: SDLSurface? = null
        var mTextEdit: DummyEdit? = null
        var mScreenKeyboardShown = false
        var mLayout: ViewGroup? = null
        var mClipboardHandler: SDLClipboardHandler? = null
        var mCursors: Hashtable<Int, PointerIcon>? = null
        var mLastCursorID = 0
        var mMotionListener: SDLGenericMotionListener_API12? = null
        var mHIDDeviceManager: HIDDeviceManager? = null


        fun initialize() {
            // The static nature of the singleton and Android quirkyness force us to initialize everything here
            // Otherwise, when exiting the app and returning to it, these variables *keep* their pre exit values
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
    }
}
