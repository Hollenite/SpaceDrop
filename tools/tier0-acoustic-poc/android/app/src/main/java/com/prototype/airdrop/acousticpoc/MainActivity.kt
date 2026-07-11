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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val rangeTrial by viewModel.rangeTrial.collectAsStateWithLifecycle()
    val burstAutoTest by viewModel.burstAutoTest.collectAsStateWithLifecycle()
    val silentTest by viewModel.silentTest.collectAsStateWithLifecycle()
    val peakSnr by viewModel.peakSnr.collectAsStateWithLifecycle()
    val fskDeviceId by viewModel.fskDeviceId.collectAsStateWithLifecycle()
    val fskEpoch by viewModel.fskEpoch.collectAsStateWithLifecycle()
    val lastFskDecode by viewModel.lastFskDecode.collectAsStateWithLifecycle()
    val fskDecodeCount by viewModel.fskDecodeCount.collectAsStateWithLifecycle()
    val fskDecoderState by viewModel.fskDecoderState.collectAsStateWithLifecycle()
    val fskCrcRejectCount by viewModel.fskCrcRejectCount.collectAsStateWithLifecycle()
    val fskLastRejectedTokenHex by viewModel.fskLastRejectedTokenHex.collectAsStateWithLifecycle()
    val fskLastLockSnrDb by viewModel.fskLastLockSnrDb.collectAsStateWithLifecycle()
    val expectedTokenHex by viewModel.expectedTokenHex.collectAsStateWithLifecycle()

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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SpaceDrop Acoustic POC",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tier 0 Phase 1 — measurement gate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(text = "Target: ${targetHz.toInt()} Hz", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = targetHz,
                onValueChange = { viewModel.setTargetFrequency(it) },
                valueRange = DetectorViewModel.MIN_FREQ_HZ..DetectorViewModel.MAX_FREQ_HZ,
                steps = 39,
                modifier = Modifier.fillMaxWidth()
            )

            val snr = metrics?.snrDb ?: 0.0
            val normalized = (snr / 60.0).coerceIn(0.0, 1.0).toFloat()
            Text(text = "SNR: ${"%.1f".format(snr)} dB")
            LinearProgressIndicator(
                progress = { normalized },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            )

            metrics?.let { m ->
                Text(
                    text = "Magnitude: ${"%.2e".format(m.magnitude)}  |  Noise: ${"%.2e".format(m.noiseFloor)}",
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
                    .height(64.dp)
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
                    if (isListening) viewModel.stopListening()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isListening) "Stop listening" else "Start listening")
            }

            HorizontalDivider()

            SectionTitle("Sweep helper (Step A)")
            if (peakSnr.hasPeak) {
                Text(
                    text = "Peak SNR: ${"%.1f".format(peakSnr.peakSnrDb)} dB @ ${peakSnr.peakFreqHz.toInt()} Hz",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Peak SNR: — (listen during PC sweep)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { viewModel.resetPeakSnr() }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset peak")
            }

            HorizontalDivider()

            SectionTitle("Range test (Step B)")
            Text(
                text = "After each PC burst ON window: tap Hit or Miss",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${rangeTrial.hits} hits / ${rangeTrial.total} trials " +
                    "(${"%.0f".format(rangeTrial.ratePercent)}%)",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.markBurstHit() },
                    modifier = Modifier.weight(1f),
                    enabled = isListening
                ) {
                    Text("Hit")
                }
                Button(
                    onClick = { viewModel.markBurstMiss() },
                    modifier = Modifier.weight(1f),
                    enabled = isListening
                ) {
                    Text("Miss")
                }
            }
            OutlinedButton(onClick = { viewModel.resetRangeTrial() }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset trials")
            }

            HorizontalDivider()

            SectionTitle("Burst auto test (Step B — 500 ms SNR hold)")
            when (burstAutoTest.phase) {
                BurstAutoPhase.COUNTDOWN -> {
                    Text(
                        text = "Start PC burst when countdown hits 0… ${burstAutoTest.countdownSec}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "dotnet run -- --freq 19000 --mode burst --duration 60",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BurstAutoPhase.RUNNING -> {
                    Text(
                        text = "Running… ${burstAutoTest.elapsedSec}/${DetectorViewModel.BURST_AUTO_DURATION_SEC}s " +
                            "(burst ${burstAutoTest.currentBurst}/${burstAutoTest.expectedBursts})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${burstAutoTest.hits} hits / ${burstAutoTest.total} scored " +
                            "(${"%.0f".format(burstAutoTest.ratePercent)}%)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BurstAutoPhase.FINISHED -> {
                    if (burstAutoTest.finished) {
                        Text(
                            text = "Done — ${burstAutoTest.hits}/${burstAutoTest.expectedBursts} hits " +
                                "(${"%.0f".format(burstAutoTest.ratePercent)}%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (burstAutoTest.passGate) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text(
                            text = if (burstAutoTest.passGate) "PASS (≥ 90%, 500 ms SNR hold)" else "FAIL (< 90%)",
                            fontWeight = FontWeight.Bold,
                            color = if (burstAutoTest.passGate) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    } else {
                        Text(
                            text = "60 s auto burst; hit = SNR ≥ 18 dB for 500 ms audio time (block accum)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startBurstAutoTest() },
                    modifier = Modifier.weight(1f),
                    enabled = isListening && !burstAutoTest.active && !silentTest.active
                ) {
                    Text("Start auto burst")
                }
                OutlinedButton(
                    onClick = { viewModel.cancelBurstAutoTest() },
                    modifier = Modifier.weight(1f),
                    enabled = burstAutoTest.active
                ) {
                    Text("Cancel")
                }
            }

            HorizontalDivider()

            SectionTitle("Silent room (Step D)")
            when {
                silentTest.active -> {
                    Text(
                        text = "Running… ${silentTest.elapsedSec}/${DetectorViewModel.SILENT_TEST_DURATION_SEC}s",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(text = "False positives: ${silentTest.falsePositives}")
                }
                silentTest.finished -> {
                    Text(
                        text = "Done — false positives: ${silentTest.falsePositives}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (silentTest.pass) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text(
                        text = if (silentTest.pass) "PASS (0 false DETECTED)" else "FAIL",
                        fontWeight = FontWeight.Bold,
                        color = if (silentTest.pass) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
                else -> {
                    Text(
                        text = "PC silent; counts DETECTED transitions for 60 s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startSilentTest() },
                    modifier = Modifier.weight(1f),
                    enabled = isListening && !silentTest.active && !burstAutoTest.active
                ) {
                    Text("Start 60s test")
                }
                OutlinedButton(
                    onClick = { viewModel.cancelSilentTest() },
                    modifier = Modifier.weight(1f),
                    enabled = silentTest.active
                ) {
                    Text("Cancel")
                }
            }

            HorizontalDivider()

            SectionTitle("FSK decode (Phase 2)")
            Text(
                text = "Expected device: $fskDeviceId",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Expected epoch: $fskEpoch",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { viewModel.useTestEpoch100() }) {
                    Text("Use test epoch 100")
                }
                OutlinedButton(onClick = { viewModel.refreshFskEpochFromClock() }) {
                    Text("Refresh epoch")
                }
                OutlinedButton(onClick = { viewModel.resetFskDecodeStats() }) {
                    Text("Reset FSK stats")
                }
            }
            Text(
                text = "Expected token: $expectedTokenHex",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Decoder: $fskDecoderState · CRC rejects: $fskCrcRejectCount · lock SNR: ${"%.0f".format(fskLastLockSnrDb)} dB",
                style = MaterialTheme.typography.bodySmall,
                color = when (fskDecoderState) {
                    "Data" -> Color(0xFF1565C0)
                    "Preamble" -> Color(0xFFE65100)
                    "Cooldown" -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = "PC epoch must match phone epoch (tap Refresh epoch or pass --epoch on PC)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "PC: dotnet run -- --mode fsk --device-id $fskDeviceId --epoch $fskEpoch",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            fskLastRejectedTokenHex?.let { rejected ->
                Text(
                    text = "Last CRC reject: $rejected (expected $expectedTokenHex)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828)
                )
            }
            lastFskDecode?.let { decode ->
                Text(
                    text = "Last token: ${decode.tokenHex}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (decode.tokenMatchesDevice) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
                Text(text = "CRC valid: ${decode.crcValid}")
                Text(text = "Token match: ${decode.tokenMatchesDevice}")
            } ?: Text(
                text = if (isListening) "Listening for FSK frames…" else "Start listening to decode FSK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = "Frames decoded: $fskDecodeCount")

            Text(
                text = "Logcat: adb logcat -s SpaceDropAcousticPoc",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}
