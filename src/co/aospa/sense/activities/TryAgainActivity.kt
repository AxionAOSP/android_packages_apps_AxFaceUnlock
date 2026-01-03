/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import co.aospa.sense.ui.screens.TryAgainScreen
import co.aospa.sense.util.Constants

class TryAgainActivity : ComponentActivity() {

    private var token: ByteArray? = null
    private var userId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        token = intent.getByteArrayExtra(Constants.EXTRA_KEY_CHALLENGE_TOKEN)
        userId = intent.getIntExtra(Intent.EXTRA_USER_ID, 0)

        if (savedInstanceState != null && token == null) {
            token = savedInstanceState.getByteArray(Constants.EXTRA_KEY_CHALLENGE_TOKEN)
            userId = savedInstanceState.getInt(Intent.EXTRA_USER_ID)
        }

        setContent {
            TryAgainScreen(
                onTryAgain = {
                    startActivity(Intent(this, EnrollActivity::class.java).apply {
                        putExtra(Constants.EXTRA_KEY_CHALLENGE_TOKEN, token)
                        putExtra(Intent.EXTRA_USER_ID, userId)
                    })
                    finish()
                },
                showButton = token != null
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putByteArray(Constants.EXTRA_KEY_CHALLENGE_TOKEN, token)
        outState.putInt(Intent.EXTRA_USER_ID, userId)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}
