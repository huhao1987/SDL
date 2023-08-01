package org.libsdl.app

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

/**
 * SDL Activity
 */
open class SDLActivity : AppCompatActivity() {
    init {
        SDLUtils.arguments
        SDLUtils.libraries = arrayListOf(
            "SDL2",
            // "SDL2_image",
            // "SDL2_mixer",
            // "SDL2_net",
            // "SDL2_ttf",
            // "SDL2_image",
            // "SDL2_mixer",
            // "SDL2_net",
            // "SDL2_ttf",
            "main",
        )
    }

    private val TAG = "SDL"

    // Setup
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "Device: " + Build.DEVICE)
        Log.v(TAG, "Model: " + Build.MODEL)
        Log.v(TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        SDLUtils.init(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        SDLUtils.onWIndowFocusChanged(hasFocus)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        SDLUtils.onLowMemory()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        SDLUtils.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        SDLUtils.onBackPressed()
    }


    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        SDLUtils.dispatchKeyEvent(event)


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        SDLUtils.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
