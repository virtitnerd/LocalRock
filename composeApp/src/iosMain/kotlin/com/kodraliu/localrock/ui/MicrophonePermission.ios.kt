package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable

// The iOS live view has no microphone path yet, so the call button reports "not allowed".
@Composable
actual fun rememberMicrophonePermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = { onResult(false) }
