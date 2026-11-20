package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.BlurThresholdPreset
import com.example.model.GuidanceMode
import com.example.model.SensorMode
import com.example.viewmodel.SphereCaptureViewModel
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SphereCaptureViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val diagnostics = remember { viewModel.getDeviceDiagnostics() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .testTag("settings_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Guidance Mode
            item {
                SettingsSectionCard(title = "Capture Guidance Mode") {
                    GuidanceMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSettings { it.copy(guidanceMode = mode) } }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.guidanceMode == mode,
                                onClick = { viewModel.updateSettings { it.copy(guidanceMode = mode) } }
                            )
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // 2. Orientation Sensor Mode
            item {
                SettingsSectionCard(title = "Orientation Sensor Policy") {
                    SensorMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSettings { it.copy(sensorMode = mode) } }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.sensorMode == mode,
                                onClick = { viewModel.updateSettings { it.copy(sensorMode = mode) } }
                            )
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. HOLD Ring Duration
            item {
                SettingsSectionCard(title = "HOLD Auto-Capture Timing") {
                    Text(
                        text = "Hold still duration: ${settings.holdDurationMs} ms",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = settings.holdDurationMs.toFloat(),
                        onValueChange = { value ->
                            viewModel.updateSettings {
                                it.copy(holdDurationMs = (value / 50f).roundToLong() * 50L)
                            }
                        },
                        valueRange = 650f..1100f
                    )
                    Text(
                        text = "Recommended 700–1000 ms to avoid motion blur before shutter fires.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Blur Quality Gate
            item {
                SettingsSectionCard(title = "Blur Quality Gate (Laplacian Variance)") {
                    BlurThresholdPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSettings { it.copy(blurPreset = preset) } }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.blurPreset == preset,
                                onClick = { viewModel.updateSettings { it.copy(blurPreset = preset) } }
                            )
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // 5. Device Compatibility & Camera Characteristics Diagnostics
            item {
                SettingsSectionCard(title = "Device Diagnostics (Android 11+ Support)") {
                    DiagnosticRow("Android Version", "${diagnostics.androidVersion} (API ${diagnostics.apiLevel})")
                    DiagnosticRow("Back Camera", if (diagnostics.hasBackCamera) "Available" else "Unavailable")
                    DiagnosticRow("Camera FOV", diagnostics.cameraFov.lensSummary)
                    DiagnosticRow("Game Rotation Vector", if (diagnostics.hasGameRotationVector) "Supported" else "Fallback")
                    DiagnosticRow("Rotation Vector", if (diagnostics.hasRotationVector) "Supported" else "Unavailable")
                    DiagnosticRow("Gyroscope", if (diagnostics.hasGyroscope) "Supported" else "Reduced Mode")
                    DiagnosticRow("Accel + Magnetometer", if (diagnostics.hasAccelerometerAndMag) "Supported" else "Unavailable")
                    DiagnosticRow("Free Storage", "${diagnostics.freeStorageMb} MB")

                    if (settings.customHFovDeg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(customHFovDeg = null, customVFovDeg = null)
                                }
                            }
                        ) {
                            Text("Reset FOV Override to Hardware Auto")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
