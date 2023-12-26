package org.libsdl.app

import android.os.Process
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.libsdl.app.SDLUtils.arguments
import org.libsdl.app.SDLUtils.mSDLThread
import org.libsdl.app.SDLUtils.mainFunction
import org.libsdl.app.SDLUtils.mainSharedObject
import org.libsdl.app.SDLUtils.nativeRunMain


internal class SDLMain(var activity: AppCompatActivity) : Runnable {
    override fun run() {
        // Runs SDL_main()
        val library = mainSharedObject
        val function = mainFunction
        val arguments = arguments
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        } catch (e: Exception) {
            Log.v("SDL", "modify thread properties failed $e")
        }
        Log.v("SDL", "Running main function $function from library $library")
        nativeRunMain(library, function, arguments)
        Log.v("SDL", "Finished main function")
        if(activity.isFinishing){
            mSDLThread = null
            activity.finish()
        }
    }
}