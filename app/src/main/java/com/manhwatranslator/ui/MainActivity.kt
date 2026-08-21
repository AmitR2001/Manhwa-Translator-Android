package com.manhwatranslator.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.manhwatranslator.R
import com.manhwatranslator.overlay.FloatingButtonService
import com.manhwatranslator.pipeline.ModelManager
import com.manhwatranslator.service.TranslationService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TranslationService.StateListener {

    private lateinit var toggleButton: android.widget.Button
    private lateinit var downloadButton: android.widget.Button
    private lateinit var modelStatus: android.widget.TextView

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toggleButton = findViewById(R.id.btnToggle)
        downloadButton = findViewById(R.id.btnDownloadModel)
        modelStatus = findViewById(R.id.modelStatus)

        toggleButton.setOnClickListener {
            if (!TranslationService.isRunning) {
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    return@setOnClickListener
                }
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            } else {
                startService(Intent(this, TranslationService::class.java).setAction(TranslationService.ACTION_STOP))
            }
        }

        downloadButton.setOnClickListener {
            downloadButton.isEnabled = false
            modelStatus.text = getString(R.string.downloading_model)
            lifecycleScope.launch {
                val result = ModelManager.downloadKoreanModels()
                modelStatus.text = when {
                    result.ocrReady && result.translateReady -> getString(R.string.model_ready)
                    else -> {
                        val parts = mutableListOf<String>()
                        if (!result.ocrReady) parts.add("OCR failed")
                        if (!result.translateReady) parts.add("Translate failed")
                        parts.joinToString(", ") + (result.error?.let { " ($it)" } ?: "")
                    }
                }
                downloadButton.isEnabled = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TranslationService.addListener(this)
        maybeStartFloatingButton()
    }

    override fun onPause() {
        TranslationService.removeListener(this)
        super.onPause()
    }

    private fun maybeStartFloatingButton() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingButtonService::class.java))
        }
    }

    override fun onStateChanged(running: Boolean) {
        toggleButton.text = if (running) getString(R.string.stop_translation) else getString(R.string.start_translation)
    }
}
