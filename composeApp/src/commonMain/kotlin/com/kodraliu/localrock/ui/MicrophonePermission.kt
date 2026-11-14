package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable

/** Returns an action that asks for the microphone and reports the answer (true at once if already allowed). */
@Composable
expect fun rememberMicrophonePermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
