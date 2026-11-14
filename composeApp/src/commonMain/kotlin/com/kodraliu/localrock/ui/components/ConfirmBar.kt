package com.kodraliu.localrock.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The official edit bar: X on the left, the tool title centred, a check on the right that stays off until there is something to save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBar(
    title: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
        },
        actions = {
            IconButton(onClick = onConfirm, enabled = confirmEnabled) { Icon(Icons.Default.Check, contentDescription = "Save") }
        },
    )
}
