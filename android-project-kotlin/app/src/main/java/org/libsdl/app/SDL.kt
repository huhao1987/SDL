package org.libsdl.app

import android.content.Context

/**
 * SDL library initialization
 */
object SDL {
    // This function should be called first and sets up the native code
    // so it can call into the Java classes
    @JvmStatic
    fun setupJNI() {
        SDLActivity.nativeSetupJNI()
        SDLAudioManager.nativeSetupJNI()
        SDLControllerManager.nativeSetupJNI()
    }

    // This function should be called each time the activity is started
    @JvmStatic
    fun initialize() {
        context = null
        SDLActivity.initialize()
        SDLAudioManager.initialize()
        SDLControllerManager.initialize()
    }

    var context: Context?
        get() = mContext
        // This function stores the current activity (SDL or not)
        set(context) {
            SDLAudioManager.setContext(context)
            mContext = context
        }

    @JvmStatic
    @Throws(UnsatisfiedLinkError::class, SecurityException::class, NullPointerException::class)
    fun loadLibrary(libraryName: String?) {
        if (libraryName == null) {
            throw NullPointerException("No library name provided.")
        }
        try {
            // Let's see if we have ReLinker available in the project.  This is necessary for
            // some projects that have huge numbers of local libraries bundled, and thus may
            // trip a bug in Android's native library loader which ReLinker works around.  (If
            // loadLibrary works properly, ReLinker will simply use the normal Android method
            // internally.)
            //
            // To use ReLinker, just add it as a dependency.  For more information, see
            // https://github.com/KeepSafe/ReLinker for ReLinker's repository.
            //
            val relinkClass = mContext!!.classLoader.loadClass("com.getkeepsafe.relinker.ReLinker")
            val relinkListenerClass =
                mContext!!.classLoader.loadClass("com.getkeepsafe.relinker.ReLinker\$LoadListener")
            val contextClass = mContext!!.classLoader.loadClass("android.content.Context")
            val stringClass = mContext!!.classLoader.loadClass("java.lang.String")

            // Get a 'force' instance of the ReLinker, so we can ensure libraries are reinstalled if
            // they've changed during updates.
            val forceMethod = relinkClass.getDeclaredMethod("force")
            val relinkInstance = forceMethod.invoke(null)
            val relinkInstanceClass: Class<*> = relinkInstance.javaClass

            // Actually load the library!
            val loadMethod = relinkInstanceClass.getDeclaredMethod(
                "loadLibrary",
                contextClass,
                stringClass,
                stringClass,
                relinkListenerClass
            )
            loadMethod.invoke(relinkInstance, mContext, libraryName, null, null)
        } catch (e: Throwable) {
            // Fall back
            try {
                System.loadLibrary(libraryName)
            } catch (ule: UnsatisfiedLinkError) {
                throw ule
            } catch (se: SecurityException) {
                throw se
            }
        }
    }

    internal var mContext: Context? = null
}
