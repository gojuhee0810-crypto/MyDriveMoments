package com.mydrivemoments.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mydrivemoments.app.ui.AppScreen

private const val UI_SCALE = 1.35f

class MainActivity : ComponentActivity() {
    private lateinit var controller: DriveController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = DriveController(applicationContext)
        controller.start()
        setContent {
            // Pleos Connect runs at mdpi on a 2560x1440 panel; scale up so text is readable at arm's length.
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density * UI_SCALE, d.fontScale)) {
                AppScreen(controller)
            }
        }
    }

    override fun onDestroy() {
        controller.stop()
        super.onDestroy()
    }
}
