package com.kodraliu.localrock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.http.ServerHeader
import com.kodraliu.localrock.shared.http.ServerHeaders
import com.kodraliu.localrock.shared.settings.AppSettings

@Composable
fun CustomHeadersDialog(settings: AppSettings, onDismiss: () -> Unit) {
    val rows = remember { mutableStateListOf<ServerHeader>().apply { addAll(settings.customHeaders.value) } }
    var revealed by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    val tooMany = rows.size > ServerHeaders.MAX_HEADERS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom headers") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "For a server behind Cloudflare Access, add CF-Access-Client-Id and " +
                        "CF-Access-Client-Secret. Sent only to your server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEachIndexed { index, row ->
                    val error = if (showErrors) ServerHeaders.validateOne(row) else null
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = row.name,
                                onValueChange = { rows[index] = row.copy(name = it.trim()) },
                                label = { Text("Name") },
                                singleLine = true,
                                isError = error != null,
                                supportingText = error?.let { { Text(it) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = row.value,
                                onValueChange = { rows[index] = row.copy(value = it) },
                                label = { Text("Value") },
                                singleLine = true,
                                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconButton(onClick = { rows.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove header")
                        }
                    }
                }
                if (tooMany) {
                    Text(
                        "At most ${ServerHeaders.MAX_HEADERS} headers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { rows.add(ServerHeader("", "")) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add header")
                    }
                    TextButton(onClick = { revealed = !revealed }) {
                        Icon(
                            if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                        Text(if (revealed) "Hide values" else "Show values")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (settings.setCustomHeaders(rows.toList()) == null) onDismiss() else showErrors = true
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
