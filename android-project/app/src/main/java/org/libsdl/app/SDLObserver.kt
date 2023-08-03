package org.libsdl.app

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.cancel

/**
 * A class uses the LifecycleObserver to process some actions within the Activity`s lifecycle,
 * it allows users no need to call the actions in their onw Activity
 */
class SDLObserver: DefaultLifecycleObserver {
    private val TAG ="SDLObserver:"

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.v(TAG, "onPause()")
        if (SDLUtils.mHIDDeviceManager != null) {
            SDLUtils.mHIDDeviceManager!!.setFrozen(true)
        }
        if (!SDLUtils.mHasMultiWindow) {
            SDLUtils.pauseNativeThread()
        }
    }
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.v(TAG, "onResume()")
        if (SDLUtils.mHIDDeviceManager != null) {
            SDLUtils.mHIDDeviceManager!!.setFrozen(false)
        }
        if (!SDLUtils.mHasMultiWindow) {
            SDLUtils.resumeNativeThread()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.v(TAG, "onStop()")
        super.onStop(owner)
        if (SDLUtils.mHasMultiWindow) {
            SDLUtils.pauseNativeThread()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        Log.v(TAG, "onStart()")
        super.onStart(owner)
        if (SDLUtils.mHasMultiWindow) {
            SDLUtils.resumeNativeThread()
        }
    }
    override fun onDestroy(owner: LifecycleOwner) {
        Log.v(TAG, "onDestroy()")
        if (SDLUtils.mHIDDeviceManager != null) {
            HIDDeviceManager.release(SDLUtils.mHIDDeviceManager!!)
            SDLUtils.mHIDDeviceManager = null
        }
        if (SDLUtils.mBrokenLibraries) {
            super.onDestroy(owner)
            return
        }
            SDLUtils.nativeSendQuit()
            SDLUtils.sdlMain?.cancel()
        SDLAudioManager.sdlAudiojob?.cancel()
        SDLUtils.nativeQuit()
        super.onDestroy(owner)
    }
}