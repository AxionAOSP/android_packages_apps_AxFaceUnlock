package co.aospa.sense.util

import android.util.Log

internal const val LOG_TAG = "FaceUnlock"

internal interface Loggable {
    val logTag: String
        get() = this::class.java.simpleName
}

internal fun Loggable.logD(msg: String) {
    Log.d(LOG_TAG, "ML ($logTag) : $msg")
}
