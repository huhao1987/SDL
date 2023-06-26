package org.libsdl.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipboardManager.OnPrimaryClipChangedListener
import android.content.Context

/* This is a fake invisible editor view that receives the input and defines the
 * pan&scan region
 */
internal class SDLClipboardHandler : OnPrimaryClipChangedListener {
    protected var mClipMgr: ClipboardManager

    init {
        mClipMgr = SDL.context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        mClipMgr.addPrimaryClipChangedListener(this)
    }

    fun clipboardHasText(): Boolean {
        return mClipMgr.hasPrimaryClip()
    }

    fun clipboardGetText(): String? {
        val clip = mClipMgr.primaryClip
        if (clip != null) {
            val item = clip.getItemAt(0)
            if (item != null) {
                val text = item.text
                if (text != null) {
                    return text.toString()
                }
            }
        }
        return null
    }

    fun clipboardSetText(string: String?) {
        mClipMgr.removePrimaryClipChangedListener(this)
        val clip = ClipData.newPlainText(null, string)
        mClipMgr.setPrimaryClip(clip)
        mClipMgr.addPrimaryClipChangedListener(this)
    }

    override fun onPrimaryClipChanged() {
        SDLActivity.onNativeClipboardChanged()
    }
}
