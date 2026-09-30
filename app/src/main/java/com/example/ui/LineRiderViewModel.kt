package com.example.ui

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.R
import com.example.data.LineRiderDatabase
import com.example.data.TrackEntity
import com.example.data.TrackRepository
import com.example.physics.*
import com.example.tools.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

data class LineRiderUiState(
    val trackTitle: String = "The Mega Loop & Jump",
    val trackDescription: String = "",
    val currentTrackId: Long? = null,
    val lines: List<TrackLine> = emptyList(),
    val toolMode: ToolMode = ToolMode.FREEHAND,
    val lineType: LineType = LineType.RIDE,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val cameraFollow: Boolean = true,
    val showGrid: Boolean = true,
    val snapToGrid: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isCrashed: Boolean = false,
    val currentSpeedKmH: Float = 0f,
    val maxSpeedKmH: Float = 0f,
    val airTimeSec: Float = 0f,
    val distanceMeters: Float = 0f,
    val totalFlips: Float = 0f,
    val startX: Float = 120f,
    val startY: Float = 140f,
    val startAngle: Float = 0.25f,
    val showLibraryDialog: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showInstallPromptDialog: Boolean = false,
    val showWebExportDialog: Boolean = false,
    val showInstallBanner: Boolean = true,
    val eraserRadius: Float = 28f
)

class LineRiderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TrackRepository
    private val physicsEngine = PhysicsEngine()
    val sledder = Sledder(120f, 140f, 0.25f)
    private val history = TrackHistory()

    private val _uiState = MutableStateFlow(LineRiderUiState())
    val uiState: StateFlow<LineRiderUiState> = _uiState.asStateFlow()

    val savedTracks: StateFlow<List<TrackEntity>>

    // Live preview states for tools
    val activeFreehandPoints = MutableStateFlow<List<Vector2D>>(emptyList())
    val activeRulerStart = MutableStateFlow<Vector2D?>(null)
    val activeRulerCurrent = MutableStateFlow<Vector2D?>(null)
    val activeCurveStart = MutableStateFlow<Vector2D?>(null)
    val activeCurveEnd = MutableStateFlow<Vector2D?>(null)
    val activeCurveControl = MutableStateFlow<Vector2D?>(null)

    // Camera transform
    var cameraOffsetX = MutableStateFlow(0f)
    var cameraOffsetY = MutableStateFlow(0f)
    var cameraZoom = MutableStateFlow(1.0f)

    private var physicsJob: Job? = null

    init {
        val database = LineRiderDatabase.getInstance(application)
        repository = TrackRepository(database.trackDao())
        savedTracks = repository.allTracks.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        viewModelScope.launch {
            repository.ensurePresetsSeeded()
            // Load initial preset
            val tracks = repository.allTracks.first { it.isNotEmpty() }
            val initial = tracks.firstOrNull { it.isPreset } ?: tracks.first()
            loadTrack(initial)
        }
    }

    fun setToolMode(mode: ToolMode) {
        _uiState.update { it.copy(toolMode = mode) }
    }

    fun setLineType(type: LineType) {
        _uiState.update { it.copy(lineType = type) }
    }

    fun togglePlayPause() {
        val nextPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = nextPlaying) }
        if (nextPlaying) {
            startPhysicsLoop()
        } else {
            stopPhysicsLoop()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleCameraFollow() {
        _uiState.update { it.copy(cameraFollow = !it.cameraFollow) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun toggleGridSnap() {
        _uiState.update { it.copy(snapToGrid = !it.snapToGrid) }
    }

    fun resetSledder() {
        val state = _uiState.value
        sledder.resetTo(state.startX, state.startY, state.startAngle)
        _uiState.update {
            it.copy(
                isCrashed = false,
                currentSpeedKmH = 0f,
                airTimeSec = 0f,
                distanceMeters = 0f,
                totalFlips = 0f
            )
        }
    }

    fun setStartPosition(x: Float, y: Float, angle: Float? = null) {
        _uiState.update {
            it.copy(
                startX = x,
                startY = y,
                startAngle = angle ?: it.startAngle
            )
        }
        if (!_uiState.value.isPlaying) {
            resetSledder()
        }
    }

    private fun startPhysicsLoop() {
        physicsJob?.cancel()
        physicsJob = viewModelScope.launch {
            var lastTime = System.nanoTime()
            val targetDeltaSeconds = 1f / 60f

            while (isActive && _uiState.value.isPlaying) {
                val now = System.nanoTime()
                val elapsed = (now - lastTime) / 1_000_000_000f
                lastTime = now

                val speedMultiplier = _uiState.value.playbackSpeed
                val steps = (elapsed / targetDeltaSeconds * speedMultiplier).coerceIn(0.5f, 3.0f)
                val intSteps = max(1, steps.roundToInt())

                for (i in 0 until intSteps) {
                    physicsEngine.update(sledder, _uiState.value.lines)
                }

                // Update UI state telemetry
                _uiState.update {
                    it.copy(
                        isCrashed = sledder.isCrashed,
                        currentSpeedKmH = sledder.currentSpeed,
                        maxSpeedKmH = sledder.maxSpeed,
                        airTimeSec = sledder.airTime,
                        distanceMeters = sledder.totalDistance,
                        totalFlips = sledder.totalRotations
                    )
                }

                // Smooth Camera Follow
                if (_uiState.value.cameraFollow) {
                    val center = sledder.getCenter()
                    val vel = sledder.getVelocity()
                    // Lead camera in direction of motion
                    val leadX = vel.x * 12f
                    val leadY = vel.y * 12f
                    val targetX = center.x + leadX
                    val targetY = center.y + leadY

                    val currentCamX = cameraOffsetX.value
                    val currentCamY = cameraOffsetY.value
                    cameraOffsetX.value += (targetX - currentCamX) * 0.12f
                    cameraOffsetY.value += (targetY - currentCamY) * 0.12f
                }

                delay(16)
            }
        }
    }

    private fun stopPhysicsLoop() {
        physicsJob?.cancel()
        physicsJob = null
    }

    // --- DRAWING ACTIONS ---

    fun onAddLines(newLines: List<TrackLine>) {
        if (newLines.isEmpty()) return
        val current = _uiState.value.lines
        val updated = current + newLines
        history.record(HistoryAction.AddLines(newLines))
        _uiState.update {
            it.copy(
                lines = updated,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    fun eraseLinesAt(worldPoint: Vector2D, radius: Float) {
        val current = _uiState.value.lines
        val toRemove = current.filter { line ->
            line.distanceToPoint(worldPoint.x, worldPoint.y) <= radius
        }
        if (toRemove.isNotEmpty()) {
            val updated = current - toRemove.toSet()
            history.record(HistoryAction.RemoveLines(toRemove))
            _uiState.update {
                it.copy(
                    lines = updated,
                    canUndo = history.canUndo,
                    canRedo = history.canRedo
                )
            }
        }
    }

    fun clearAllLines() {
        val current = _uiState.value.lines
        if (current.isEmpty()) return
        history.record(HistoryAction.RemoveLines(current))
        _uiState.update {
            it.copy(
                lines = emptyList(),
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    fun undo() {
        val action = history.popUndo() ?: return
        when (action) {
            is HistoryAction.AddLines -> {
                val current = _uiState.value.lines
                val updated = current - action.lines.toSet()
                _uiState.update { it.copy(lines = updated) }
            }
            is HistoryAction.RemoveLines -> {
                val current = _uiState.value.lines
                val updated = current + action.lines
                _uiState.update { it.copy(lines = updated) }
            }
            is HistoryAction.Batch -> {
                val current = _uiState.value.lines
                val updated = (current - action.added.toSet()) + action.removed
                _uiState.update { it.copy(lines = updated) }
            }
        }
        _uiState.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    fun redo() {
        val action = history.popRedo() ?: return
        when (action) {
            is HistoryAction.AddLines -> {
                val current = _uiState.value.lines
                val updated = current + action.lines
                _uiState.update { it.copy(lines = updated) }
            }
            is HistoryAction.RemoveLines -> {
                val current = _uiState.value.lines
                val updated = current - action.lines.toSet()
                _uiState.update { it.copy(lines = updated) }
            }
            is HistoryAction.Batch -> {
                val current = _uiState.value.lines
                val updated = (current - action.removed.toSet()) + action.added
                _uiState.update { it.copy(lines = updated) }
            }
        }
        _uiState.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    // --- TRACK MANAGEMENT ---

    fun loadTrack(track: TrackEntity) {
        stopPhysicsLoop()
        val lines = repository.deserializeLines(track.linesJson)
        history.clear()
        _uiState.update {
            it.copy(
                trackTitle = track.title,
                trackDescription = track.description,
                currentTrackId = track.id,
                lines = lines,
                startX = track.startX,
                startY = track.startY,
                startAngle = track.startAngle,
                isPlaying = false,
                canUndo = false,
                canRedo = false,
                showLibraryDialog = false
            )
        }
        sledder.resetTo(track.startX, track.startY, track.startAngle)
        cameraOffsetX.value = track.startX
        cameraOffsetY.value = track.startY
        cameraZoom.value = 1.0f
    }

    fun saveCurrentTrack(title: String, description: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val id = repository.saveTrack(
                title = title,
                description = description,
                lines = state.lines,
                startX = state.startX,
                startY = state.startY,
                startAngle = state.startAngle,
                existingId = state.currentTrackId
            )
            _uiState.update {
                it.copy(
                    trackTitle = title,
                    trackDescription = description,
                    currentTrackId = id,
                    showSaveDialog = false
                )
            }
            Toast.makeText(getApplication(), "Track saved successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteTrack(id: Long) {
        viewModelScope.launch {
            repository.deleteTrack(id)
            Toast.makeText(getApplication(), "Track deleted", Toast.LENGTH_SHORT).show()
        }
    }

    fun newBlankTrack() {
        stopPhysicsLoop()
        val blank = repository.deserializeLines(
            TrackRepository(LineRiderDatabase.getInstance(getApplication()).trackDao())
                .serializeLines(
                    listOf(
                        TrackLine(x1 = 80f, y1 = 150f, x2 = 180f, y2 = 180f, type = LineType.RIDE),
                        TrackLine(x1 = 180f, y1 = 180f, x2 = 300f, y2 = 250f, type = LineType.RIDE)
                    )
                )
        )
        history.clear()
        _uiState.update {
            it.copy(
                trackTitle = "New Custom Track",
                trackDescription = "Draw your lines and test!",
                currentTrackId = null,
                lines = blank,
                startX = 100f,
                startY = 140f,
                startAngle = 0.3f,
                isPlaying = false,
                canUndo = false,
                canRedo = false,
                showLibraryDialog = false
            )
        }
        sledder.resetTo(100f, 140f, 0.3f)
        cameraOffsetX.value = 100f
        cameraOffsetY.value = 140f
    }

    // --- HOME SCREEN SHORTCUT / INSTALLATION ---

    fun promptInstallToHomeScreen() {
        val context = getApplication<Application>()
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val shortcut = ShortcutInfoCompat.Builder(context, "line_rider_pinned_shortcut")
                .setShortLabel("Line Rider")
                .setLongLabel("Line Rider - Physics Tracks")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(shortcutIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            Toast.makeText(context, "Prompting launcher to pin shortcut to Home Screen...", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Shortcut pinning not supported by your current launcher", Toast.LENGTH_SHORT).show()
        }
        _uiState.update { it.copy(showInstallPromptDialog = false, showInstallBanner = false) }
    }

    fun dismissInstallBanner() {
        _uiState.update { it.copy(showInstallBanner = false) }
    }

    fun setShowInstallDialog(show: Boolean) {
        _uiState.update { it.copy(showInstallPromptDialog = show) }
    }

    fun setShowLibraryDialog(show: Boolean) {
        _uiState.update { it.copy(showLibraryDialog = show) }
    }

    fun setShowSaveDialog(show: Boolean) {
        _uiState.update { it.copy(showSaveDialog = show) }
    }

    fun setShowWebExportDialog(show: Boolean) {
        _uiState.update { it.copy(showWebExportDialog = show) }
    }

    fun generateWebPlayerHtml(): String {
        val state = _uiState.value
        return repository.generateStandaloneWebPlayerHtml(
            title = state.trackTitle,
            lines = state.lines,
            startX = state.startX,
            startY = state.startY
        )
    }

    fun exportTrackJson(): String {
        return repository.serializeLines(_uiState.value.lines)
    }

    fun importTrackJson(json: String): Boolean {
        return try {
            val lines = repository.deserializeLines(json)
            if (lines.isNotEmpty()) {
                onAddLines(lines)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
