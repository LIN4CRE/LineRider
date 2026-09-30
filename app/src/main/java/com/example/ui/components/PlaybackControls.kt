package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.LineRiderUiState
import com.example.ui.LineRiderViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlaybackControls(
    viewModel: LineRiderViewModel,
    uiState: LineRiderUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Crash / Wipeout notification banner
        AnimatedVisibility(
            visible = uiState.isCrashed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFFF1744), RoundedCornerShape(16.dp)),
                color = Color(0xEE1E1E24)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Crashed",
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "WIPEOUT! Rider took a tumble",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = { viewModel.resetSledder() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("RETRY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Telemetry stats HUD chip
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp)),
            color = Color(0xCC0D1B2A),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TelemetryItem(
                    label = "SPEED",
                    value = "${uiState.currentSpeedKmH.roundToInt()} km/h",
                    color = Color(0xFF00E5FF)
                )
                TelemetryItem(
                    label = "MAX",
                    value = "${uiState.maxSpeedKmH.roundToInt()} km/h",
                    color = Color(0xFFFFAB00)
                )
                TelemetryItem(
                    label = "AIRTIME",
                    value = String.format("%.1fs", uiState.airTimeSec),
                    color = Color(0xFF00E676)
                )
                TelemetryItem(
                    label = "DIST",
                    value = "${uiState.distanceMeters.roundToInt()}m",
                    color = Color(0xFFE2E8F0)
                )
                if (abs(uiState.totalFlips) >= 1.0f) {
                    TelemetryItem(
                        label = "FLIPS",
                        value = "${abs(uiState.totalFlips).toInt()}x",
                        color = Color(0xFFFF4081)
                    )
                }
            }
        }

        // Main Controls Bar
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp)),
            color = Color(0xEE0B132B),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Return to Start / Reset Sledder
                IconButton(
                    onClick = { viewModel.resetSledder() },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .testTag("action_reset_sledder")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Reset Sled to Start Gate",
                        tint = Color.White
                    )
                }

                // Big Primary Play / Pause FAB
                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    containerColor = if (uiState.isPlaying) Color(0xFFFF3366) else Color(0xFF00E5FF),
                    contentColor = Color.Black,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("action_play_pause")
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause simulation" else "Play simulation",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Speed Selector Pill (0.5x, 1x, 2x)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(0.5f, 1.0f, 2.0f).forEach { spd ->
                        val isCurrent = uiState.playbackSpeed == spd
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Color(0xFF00E5FF) else Color.Transparent)
                                .clickable { viewModel.setPlaybackSpeed(spd) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("speed_${spd}x")
                        ) {
                            Text(
                                text = if (spd == 1.0f) "1x" else "${spd}x",
                                color = if (isCurrent) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Camera follow toggle
                IconButton(
                    onClick = { viewModel.toggleCameraFollow() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (uiState.cameraFollow) Color(0x4400E5FF) else Color(0x22FFFFFF))
                        .testTag("action_toggle_camera_follow")
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Toggle Camera Follow",
                        tint = if (uiState.cameraFollow) Color(0xFF00E5FF) else Color.White
                    )
                }

                // Blueprint Grid toggle
                IconButton(
                    onClick = { viewModel.toggleGrid() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (uiState.showGrid) Color(0x4438BDF8) else Color(0x22FFFFFF))
                        .testTag("action_toggle_grid")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Toggle Blueprint Grid",
                        tint = if (uiState.showGrid) Color(0xFF38BDF8) else Color(0xAAFFFFFF)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0x88FFFFFF), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
