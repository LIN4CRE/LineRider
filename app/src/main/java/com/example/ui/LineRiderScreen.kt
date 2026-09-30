package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.*

@Composable
fun LineRiderScreen(
    viewModel: LineRiderViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0B132B),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isLandscape = maxWidth > maxHeight

            // 1. Interactive Physics Canvas Layer
            CanvasGameView(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Top Header Bar & Install Prompt Banner
            TopHeaderBar(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 3. Floating Toolbox (Side in landscape, below header in portrait)
            if (isLandscape) {
                ToolboxBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                )
            } else {
                ToolboxBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (uiState.showInstallBanner) 120.dp else 68.dp)
                )
            }

            // 4. Playback Controls & HUD at bottom
            PlaybackControls(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )

            // Dialogs
            if (uiState.showLibraryDialog) {
                TrackLibraryDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.setShowLibraryDialog(false) }
                )
            }

            if (uiState.showSaveDialog) {
                SaveTrackDialog(
                    viewModel = viewModel,
                    initialTitle = uiState.trackTitle,
                    initialDescription = uiState.trackDescription,
                    onDismiss = { viewModel.setShowSaveDialog(false) }
                )
            }

            if (uiState.showInstallPromptDialog) {
                InstallPromptDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.setShowInstallDialog(false) }
                )
            }

            if (uiState.showWebExportDialog) {
                WebExportDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.setShowWebExportDialog(false) }
                )
            }
        }
    }
}
