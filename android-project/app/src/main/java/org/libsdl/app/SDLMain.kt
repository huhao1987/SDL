package org.libsdl.app

import android.os.Process
import android.util.Log
import org.libsdl.app.SDLUtils.nativeRunMain

/**
 * Simple runnable to start the SDL application
 */
internal class SDLMain : Runnable {
    override fun run() {
        // Runs SDL_main()
        val library = SDLActivity.mSingleton!!.mainSharedObject
        val function = SDLActivity.mSingleton!!.mainFunction
        val arguments = SDLActivity.mSingleton!!.getArguments()
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        } catch (e: Exception) {
            Log.v("SDL", "modify thread properties failed $e")
        }
        Log.v("SDL", "Running main function $function from library $library")
        nativeRunMain(library, function, arguments)
        Log.v("SDL", "Finished main function")
        if (SDLActivity.mSingleton != null && !SDLActivity.mSingleton!!.isFinishing) {
            // Let's finish the Activity
            SDLActivity.mSDLThread = null
            SDLActivity.mSingleton!!.finish()
        } // else: Activity is already being destroyed
    }
}