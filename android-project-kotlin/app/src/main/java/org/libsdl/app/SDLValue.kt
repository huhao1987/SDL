package org.libsdl.app

import android.os.Build
import java.util.Locale

class SDLValue {
    companion object{

        var mIsResumedCalled = false
        var mHasFocus:kotlin.Boolean = false
        val mHasMultiWindow = Build.VERSION.SDK_INT >= 24

        // Cursor types
        //  static final int SDL_SYSTEM_CURSOR_NONE = -1;
         const val SDL_SYSTEM_CURSOR_ARROW = 0
         const val SDL_SYSTEM_CURSOR_IBEAM = 1
         const val SDL_SYSTEM_CURSOR_WAIT = 2
         const val SDL_SYSTEM_CURSOR_CROSSHAIR = 3
         const val SDL_SYSTEM_CURSOR_WAITARROW = 4
         const val SDL_SYSTEM_CURSOR_SIZENWSE = 5
         const val SDL_SYSTEM_CURSOR_SIZENESW = 6
         const val SDL_SYSTEM_CURSOR_SIZEWE = 7
         const val SDL_SYSTEM_CURSOR_SIZENS = 8
         const val SDL_SYSTEM_CURSOR_SIZEALL = 9
         const val SDL_SYSTEM_CURSOR_NO = 10
         const val SDL_SYSTEM_CURSOR_HAND = 11

         const val SDL_ORIENTATION_UNKNOWN = 0
         const val SDL_ORIENTATION_LANDSCAPE = 1
         const val SDL_ORIENTATION_LANDSCAPE_FLIPPED = 2
         const val SDL_ORIENTATION_PORTRAIT = 3
         const val SDL_ORIENTATION_PORTRAIT_FLIPPED = 4

         var mCurrentOrientation = 0
         var mCurrentLocale: Locale? = null
        var mNextNativeState: NativeState? = null
        var mCurrentNativeState: NativeState? = null

    }
}


enum class NativeState {
    INIT,
    RESUMED,
    PAUSED
}
