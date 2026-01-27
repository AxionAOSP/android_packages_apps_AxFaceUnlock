package co.aospa.sense.util

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

object Util {
    private const val TAG = "Sense:Utils"

    const val IS_DEBUG_LOGGING = true
    private const val PROPERTY_FACEUNLOCK_AVAILABLE = "property_faceunlock_available"

    fun isFaceUnlockAvailable(context: Context?): Boolean {
        val preferenceHelper = PreferenceHelper(context!!)
        return preferenceHelper.getIntValueByKey(PROPERTY_FACEUNLOCK_AVAILABLE) == 1
    }

    fun setFaceUnlockAvailable(context: Context?) {
        val sharedPrefUtil = PreferenceHelper(context!!)
        sharedPrefUtil.saveIntValue(
            PROPERTY_FACEUNLOCK_AVAILABLE,
            if (isFaceUnlockEnrolled(context)) 1 else 0
        )
    }

    fun isFaceUnlockEnrolled(context: Context?): Boolean {
        val preferenceHelper = PreferenceHelper(context!!)
        return preferenceHelper.getIntValueByKey(Constants.SHARED_KEY_FACE_ID) > 0 && preferenceHelper.getByteArrayValueByKey(
            Constants.SHARED_KEY_ENROLL_TOKEN
        ) != null
    }

    fun isFaceUnlockDisabledByDPM(context: Context): Boolean {
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            if (devicePolicyManager.getPasswordQuality(null) > DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC) {
                return true
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "isFaceUnlockDisabledByDPM error:", e)
        }
        return devicePolicyManager.getKeyguardDisabledFeatures(null) and DevicePolicyManager.KEYGUARD_DISABLE_FACE != 0
    }

    fun getUserId(context: Context?): Int {
        return context?.userId ?: 0
    }
}