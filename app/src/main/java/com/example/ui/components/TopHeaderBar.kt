package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.LineRiderUiState
import com.example.ui.LineRiderViewModel

@Composable
fun TopHeaderBar(
    viewModel: LineRiderViewModel,
    uiState: LineRiderUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // "Install to Home Screen" Call-To-Action Banner
        AnimatedVisibility(
            visible = uiState.showInstallBanner,
            enter = slideInVertically(),
            exit = slideOutVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(16.dp)),
                color = Color(0xF00A192F),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0077B6)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddToHomeScreen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Install Line Rider to Home Screen",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Pin 1-tap shortcut to your phone or tablet launcher",
                                color = Color(0xBB94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { viewModel.promptInstallToHomeScreen() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("action_install_banner_pin")
                        ) {
                            Text("INSTALL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = { viewModel.dismissInstallBanner() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Banner",
                                tint = Color(0x88FFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Top Navigation & Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Track Title Pill & App Brand
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)),
                color = Color(0xDD0D1B2A)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DownhillSkiing,
                        contentDescription = "Line Rider",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = uiState.trackTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• ${uiState.lines.size} lines",
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            // Quick Actions: Library, Save, Web Export, Install/Pin
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)),
                color = Color(0xDD0D1B2A)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Tracks Library
                    IconButton(
                        onClick = { viewModel.setShowLibraryDialog(true) },
                        modifier = Modifier.size(36.dp).testTag("action_open_library")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Tracks Library",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Save Track
                    IconButton(
                        onClick = { viewModel.setShowSaveDialog(true) },
                        modifier = Modifier.size(36.dp).testTag("action_save_track")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Track",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Web Browser Export & Play
                    IconButton(
                        onClick = { viewModel.setShowWebExportDialog(true) },
                        modifier = Modifier.size(36.dp).testTag("action_web_export")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Browser Web Player & Export",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add to Home Screen Dialog
                    IconButton(
                        onClick = { viewModel.setShowInstallDialog(true) },
                        modifier = Modifier.size(36.dp).testTag("action_prompt_install_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddToHomeScreen,
                            contentDescription = "Add to Home Screen",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
