/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.util

import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

object GpuDelegateHelper {
    fun createGpuDelegate(): Delegate? {
        return try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                GpuDelegate(compatList.bestOptionsForThisDevice)
            } else {
                GpuDelegate()
            }
        } catch (e: Exception) {
            Log.e("GpuDelegateHelper", "Failed to create GPU delegate", e)
            null
        }
    }
}
