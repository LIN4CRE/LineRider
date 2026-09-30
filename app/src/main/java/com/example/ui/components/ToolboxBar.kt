package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.physics.LineType
import com.example.tools.ToolMode
import com.example.ui.LineRiderUiState
import com.example.ui.LineRiderViewModel

@Composable
fun ToolboxBar(
    viewModel: LineRiderViewModel,
    uiState: LineRiderUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp)),
        color = Color(0xDD0D1B2A),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mode Selectors
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(
                    selected = uiState.toolMode == ToolMode.FREEHAND,
                    icon = Icons.Default.Edit,
                    contentDescription = "Smooth Freehand Line Tool",
                    tag = "tool_freehand",
                    onClick = { viewModel.setToolMode(ToolMode.FREEHAND) }
                )
                ToolButton(
                    selected = uiState.toolMode == ToolMode.STRAIGHT,
                    icon = Icons.Default.Straighten,
                    contentDescription = "Straight Ramp Ruler Tool",
                    tag = "tool_straight",
                    onClick = { viewModel.setToolMode(ToolMode.STRAIGHT) }
                )
                ToolButton(
                    selected = uiState.toolMode == ToolMode.CURVE,
                    icon = Icons.Default.Gesture,
                    contentDescription = "Smooth Arc Curve Tool",
                    tag = "tool_curve",
                    onClick = { viewModel.setToolMode(ToolMode.CURVE) }
                )
                ToolButton(
                    selected = uiState.toolMode == ToolMode.ERASER,
                    icon = Icons.Default.AutoFixHigh,
                    contentDescription = "Track Eraser Tool",
                    tag = "tool_eraser",
                    onClick = { viewModel.setToolMode(ToolMode.ERASER) }
                )
                ToolButton(
                    selected = uiState.toolMode == ToolMode.PAN,
                    icon = Icons.Default.PanTool,
                    contentDescription = "Pan and Move Camera",
                    tag = "tool_pan",
                    onClick = { viewModel.setToolMode(ToolMode.PAN) }
                )

                VerticalDivider(
                    modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
                    color = Color(0x44FFFFFF)
                )

                // Undo / Redo
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = uiState.canUndo,
                    modifier = Modifier.size(38.dp).testTag("action_undo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (uiState.canUndo) Color.White else Color(0x44FFFFFF)
                    )
                }

                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = uiState.canRedo,
                    modifier = Modifier.size(38.dp).testTag("action_redo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (uiState.canRedo) Color.White else Color(0x44FFFFFF)
                    )
                }
            }

            // Line Type Color Palettes
            AnimatedVisibility(visible = uiState.toolMode != ToolMode.ERASER && uiState.toolMode != ToolMode.PAN) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LineType.entries.forEach { type ->
                        val isSelected = uiState.lineType == type
                        val borderCol by animateColorAsState(if (isSelected) Color.White else Color.Transparent)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0x33FFFFFF) else Color.Transparent)
                                .clickable { viewModel.setLineType(type) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("line_type_${type.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(type.displayColor)
                                    .border(1.5.dp, borderCol, CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = type.title,
                                color = if (isSelected) Color.White else Color(0xBBFFFFFF),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF)
    val tint = if (selected) Color.White else Color(0xDDFFFFFF)

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
