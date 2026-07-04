package com.prototype.airdrop.acousticpoc

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: DetectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DetectorScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DetectorScreen(viewModel: DetectorViewModel) {
    val targetHz by viewModel.targetFrequencyHz.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SpaceDrop Acoustic POC",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tier 0 Phase 1 — Goertzel tone detector",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Target: ${targetHz.toInt()} Hz",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = targetHz,
                onValueChange = { viewModel.setTargetFrequency(it) },
                valueRange = DetectorViewModel.MIN_FREQ_HZ..DetectorViewModel.MAX_FREQ_HZ,
                steps = 39,
                modifier = Modifier.fillMaxWidth()
            )

            val snr = metrics?.snrDb ?: 0.0
            val normalized = (snr / 30.0).coerceIn(0.0, 1.0).toFloat()
            Text(text = "SNR: ${"%.1f".format(snr)} dB")
            LinearProgressIndicator(
                progress = { normalized },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            )

            metrics?.let { m ->
                Text(
                    text = "Magnitude: ${"%.2e".format(m.magnitude)}  |  Noise floor: ${"%.2e".format(m.noiseFloor)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "fs=${m.sampleRate}  source=${m.audioSourceName}  overruns=${m.bufferOverruns}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        if (metrics?.detected == true) Color(0xFF2E7D32) else Color(0xFF424242)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (metrics?.detected == true) "DETECTED" else "LISTENING",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    if (isListening) {
                        viewModel.stopListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isListening) "Stop listening" else "Start listening")
            }

            Text(
                text = "Logcat: adb logcat -s SpaceDropAcousticPoc",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
