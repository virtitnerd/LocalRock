package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.carpetModeOptions
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.components.PickerDialog
import com.kodraliu.localrock.ui.components.SettingRowClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarpetScreen(
    carpetMode: Int,
    features: List<BetaFeatureState>,
    onCarpetModeChange: (Int) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val options = carpetModeOptions(carpetMode)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carpet cleaning") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        CenteredContent {
            LazyColumn(contentPadding = padding) {
                item {
                    SettingRowClickable(
                        icon = Icons.Default.GridView,
                        iconTint = AppColors.Good,
                        title = "Carpet mode",
                        subtitle = options.getOrElse(carpetMode) { "Mode $carpetMode" },
                        onClick = { showDialog = true },
                    )
                }
                items(features, key = { it.def.id }) { f ->
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                    FeatureToggleRow(f, onToggle)
                }
            }
        }
    }
    if (showDialog) {
        PickerDialog(
            title = "Carpet mode",
            options = options,
            selected = carpetMode,
            onSelect = { onCarpetModeChange(it); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}
