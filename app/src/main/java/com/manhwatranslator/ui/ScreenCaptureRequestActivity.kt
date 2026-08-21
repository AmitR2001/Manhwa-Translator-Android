package com.manhwatranslator.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.manhwatranslator.service.TranslationService

/**
 * Invisible trampoline: requests the MediaProjection (screen capture) consent dialog on
 * behalf of the floating overlay button, starts TranslationService with the result, then
 * finishes immediately - returning focus to whatever app the user was in.
 */
class ScreenCaptureRequestActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, TranslationService::class.java).apply {
                putExtra(TranslationService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(TranslationService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(intent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
