package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.webrtc.CodecOrder
import com.kodraliu.localrock.shared.webrtc.VideoDirection
import com.kodraliu.localrock.shared.webrtc.VideoExperiments

/** Debug panel opened from the live view popup (Beta): each switch applies the next time the stream starts. */
@Composable
fun VideoExperimentsDialog(
    value: VideoExperiments,
    onChange: (VideoExperiments) -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val bitrates = listOf("None" to 0, "2000" to 2000, "4000" to 4000, "8000" to 8000)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Video experiments") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Debug only. Applied on the next stream start; read the results with adb logcat -s RtcStats SdpDump.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Choice(
                    "Video direction",
                    listOf("recvonly (current)" to VideoDirection.RECV_ONLY, "sendrecv (official)" to VideoDirection.SEND_RECV),
                    value.direction,
                ) { onChange(value.copy(direction = it)) }
                Choice(
                    "Codec order",
                    listOf(
                        "Current" to CodecOrder.CURRENT,
                        "Official H264 list" to CodecOrder.OFFICIAL_H264,
                        "VP8 first" to CodecOrder.VP8_FIRST,
                        "H264 42e01f only" to CodecOrder.BASELINE_ONLY,
                    ),
                    value.codecOrder,
                ) { onChange(value.copy(codecOrder = it)) }
                Choice("Keep VP8 and VP9 (VP8 first implies it)", listOf("Strip (current)" to false, "Keep" to true), value.keepVp8Vp9) {
                    onChange(value.copy(keepVp8Vp9 = it))
                }
                Choice("b=AS on video (kbps)", bitrates, value.bandwidthKbps) { onChange(value.copy(bandwidthKbps = it)) }
                Choice("x-google start/min bitrate (kbps)", bitrates, value.googleBitrateKbps) { onChange(value.copy(googleBitrateKbps = it)) }
                Choice("imageattr 1280x720 and 1920x1080", listOf("Off" to false, "On" to true), value.imageAttr) {
                    onChange(value.copy(imageAttr = it))
                }
                Choice("Log SDP offer and answer (SdpDump)", listOf("Off" to false, "On" to true), value.dumpSdp) {
                    onChange(value.copy(dumpSdp = it))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRestart(); onClose() }) { Text("Restart stream") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onChange(VideoExperiments()) }) { Text("Reset") }
                TextButton(onClick = onClose) { Text("Done") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (name, option) ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(name) })
            }
        }
    }
}
