package org.libsdl.app

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import org.libsdl.app.SDLUtils.COMMAND_CHANGE_TITLE
import org.libsdl.app.SDLUtils.COMMAND_CHANGE_WINDOW_STYLE
import org.libsdl.app.SDLUtils.COMMAND_SET_KEEP_SCREEN_ON
import org.libsdl.app.SDLUtils.COMMAND_TEXTEDIT_HIDE
import org.libsdl.app.SDLUtils.mFullscreenModeActive

class SDLCommandHandler : Handler() {
     private val TAG = "SDLCommandHandler:"
    override fun handleMessage(msg: Message) {
        val context = SDL.getContext()
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

            COMMAND_TEXTEDIT_HIDE -> if (SDLActivity.mTextEdit != null) {
                // Note: On some devices setting view to GONE creates a flicker in landscape.
                // Setting the View's sizes to 0 is similar to GONE but without the flicker.
                // The sizes will be set to useful values when the keyboard is shown again.
                SDLActivity.mTextEdit!!.layoutParams = RelativeLayout.LayoutParams(0, 0)
                val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(SDLActivity.mTextEdit!!.windowToken, 0)
                SDLActivity.mScreenKeyboardShown = false
                SDLUtils.mSurface.requestFocus()
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

            else -> if (context is AppCompatActivity && !onUnhandledMessage(
                    msg.arg1,
                    msg.obj
                )
            ) {
                Log.e(TAG, "error handling message, command is " + msg.arg1)
            }
        }
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
}
