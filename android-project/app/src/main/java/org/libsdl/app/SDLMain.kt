package org.libsdl.app

import android.hardware.Sensor
import android.os.Process
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.libsdl.app.SDLUtils.arguments
import org.libsdl.app.SDLUtils.mainFunction
import org.libsdl.app.SDLUtils.mainSharedObject
import org.libsdl.app.SDLUtils.nativeRunMain
import org.libsdl.app.SDLUtils.toActivity

/**
 * The class is used to
 */
class SDLMain  {
    private var sdlMainjob : Job? = null
    private val TAG = "SDLMain:"
    fun run() {
        if(sdlMainjob!=null || sdlMainjob?.isActive == true)
            SDLUtils.nativeResume()
        sdlMainjob?: let{
            sdlMainjob =
                CoroutineScope(Dispatchers.IO).launch {
                    Log.v("SDL", "Running main function $mainFunction from library $arguments")
                    nativeRunMain(mainSharedObject, mainFunction, arguments)
                    Log.v(TAG, "Finished main function")
                    if(SDLUtils.context.toActivity()?.isFinishing == true){
                        sdlMainjob?.cancel()
                        SDLUtils.context.toActivity()?.finish()
                    }
                }
            SDLUtils.mSurface.enableSensor(Sensor.TYPE_ACCELEROMETER, true)
            sdlMainjob?.start()
        }
    }
    fun pause(pause:()->Unit){
        pause()
    }
    fun cancel(){
        sdlMainjob?.cancel()
    }
}