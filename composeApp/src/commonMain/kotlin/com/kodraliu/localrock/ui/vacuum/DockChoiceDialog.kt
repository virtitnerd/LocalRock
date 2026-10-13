package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.DockChoice

/**
 * Single-choice picker keyed by the robot's code. The current value is selected by code, so it is
 * selected whatever its position; a value none of [choices] names is called out above the list
 * instead of leaving every option unselected with no explanation.
 */
@Composable
internal fun DockChoiceDialog(
    title: String,
    choices: List<DockChoice>,
    selectedCode: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (choices.none { it.code == selectedCode }) {
                    Text(
                        "Currently set to $selectedCode, which this app has no name for. Pick one to change it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                choices.forEach { choice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice.code); onDismiss() }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = choice.code == selectedCode,
                            onClick = { onSelect(choice.code); onDismiss() },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
