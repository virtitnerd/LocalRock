package com.kodraliu.localrock.ui

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun LandscapeWhile(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active, context) {
        var c = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val activity = c as? Activity
        val before = activity?.requestedOrientation
        if (active) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (active && before != null) activity?.requestedOrientation = before }
    }
}
