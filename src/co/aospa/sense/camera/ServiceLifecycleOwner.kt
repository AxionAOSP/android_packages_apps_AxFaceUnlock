/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.camera

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class ServiceLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        } else {
            mainHandler.post {
                lifecycleRegistry.currentState = Lifecycle.State.STARTED
            }
        }
    }

    fun stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        } else {
            mainHandler.post {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}
