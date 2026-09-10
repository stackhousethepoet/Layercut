package com.stackhousethepoet.layercut.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.FilterTiltShift
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stackhousethepoet.layercut.editor.BrushEngine
import com.stackhousethepoet.layercut.editor.CoordMath
import com.stackhousethepoet.layercut.editor.EditorViewModel
import com.stackhousethepoet.layercut.editor.ToolMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val snackbar = remember { SnackbarHostState() }
    // Default hidden so canvas is clear while editing; Layers chip toggles panel.
    var showLayers by remember { mutableStateOf(false) }
    val hasProject = viewModel.layers.isNotEmpty()
    val showBrushChrome =
        viewModel.toolMode == ToolMode.PAINT ||
            viewModel.toolMode == ToolMode.ERASER ||
            viewModel.toolMode == ToolMode.RESTORE
    val showEyedropperChrome = viewModel.toolMode == ToolMode.EYEDROPPER
    val showMagicChrome = viewModel.toolMode == ToolMode.MAGIC
    val showDistortChrome = viewModel.toolMode == ToolMode.DISTORT
    val showTransformChrome = viewModel.toolMode == ToolMode.TRANSFORM && hasProject
    val showPanChrome = viewModel.toolMode == ToolMode.PAN && hasProject

    val pickBase = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { viewModel.loadBaseImage(it) } }

    val pickLayer = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { viewModel.addLayerFromUri(it) } }

    val getContentBase = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.loadBaseImage(it) } }

    val getContentLayer = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addLayerFromUri(it) } }

    fun openBasePicker() {
        try {
            pickBase.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) {
            getContentBase.launch("image/*")
        }
    }

    fun openLayerPicker() {
        try {
            pickLayer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: Exception) {
            getContentLayer.launch("image/*")
        }
    }

    LaunchedEffect(viewModel.statusMessage) {
        val msg = viewModel.statusMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearStatus()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("LayerCut") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = viewModel.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(
                        onClick = { viewModel.exportPng { } },
                        enabled = hasProject
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Export PNG")
                    }
                }
            )
        },
        bottomBar = {
            // Opaque chrome outside the canvas — tools can never be covered by zoom/pan drawing.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (showLayers && hasProject) {
                        LayerPanel(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        HorizontalDivider()
                    }

                    if (showPanChrome) {
                        val zoomPct = (viewModel.viewport.scale * 100f).roundToInt()
                        Text(
                            "Zoom $zoomPct%",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        HorizontalDivider()
                    }

                    if (showBrushChrome) {
                        val atFull =
                            viewModel.brushSettings.opacity >= BrushEngine.FULL_OPACITY_THRESHOLD
                        // Effective hardness: Hard when selected, or forced at 100% opacity.
                        val effectiveHard = !viewModel.brushSettings.soft || atFull
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            val screenPx = viewModel.brushSettings.size
                            val active = viewModel.activeLayer
                            val tipLabel = if (active != null && !active.bitmap.isRecycled) {
                                val layerPx = CoordMath.screenBrushSizeToLayer(
                                    screenPx, viewModel.viewport, active
                                )
                                val ref = maxOf(active.bitmap.width, active.bitmap.height).coerceAtLeast(1)
                                val pct = ((layerPx / ref) * 100f).roundToInt().coerceIn(0, 999)
                                "Brush ${screenPx.toInt()} · ~$pct% of layer"
                            } else {
                                "Brush ${screenPx.toInt()}px"
                            }
                            Text(
                                "$tipLabel · opacity ${(viewModel.brushSettings.opacity * 100).toInt()}%" +
                                    if (atFull) " · Hard (100%)" else "",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Size",
                                    modifier = Modifier.width(48.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = viewModel.brushSettings.size,
                                    onValueChange = { viewModel.updateBrush(size = it) },
                                    valueRange = BrushEngine.SCREEN_SIZE_MIN..BrushEngine.SCREEN_SIZE_MAX,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Opacity",
                                    modifier = Modifier.width(48.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = viewModel.brushSettings.opacity,
                                    onValueChange = { viewModel.updateBrush(opacity = it) },
                                    valueRange = 0.05f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Edge",
                                    modifier = Modifier.width(48.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                FilterChip(
                                    selected = effectiveHard,
                                    onClick = { viewModel.updateBrush(soft = false) },
                                    label = { Text("Hard") }
                                )
                                FilterChip(
                                    selected = !effectiveHard,
                                    onClick = {
                                        // Soft only applies below full opacity; at 100% engine stays Hard.
                                        viewModel.updateBrush(soft = true)
                                    },
                                    label = { Text("Soft") }
                                )
                            }
                            if (viewModel.toolMode == ToolMode.PAINT) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Current brush swatch (includes eyedropper picks)
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(viewModel.brushSettings.color))
                                            .border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.setTool(ToolMode.EYEDROPPER) },
                                        label = { Text("Dropper") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Colorize, contentDescription = null)
                                        }
                                    )
                                    val colors = listOf(
                                        0xFFFF1744.toInt(),
                                        0xFFFFEA00.toInt(),
                                        0xFF00E676.toInt(),
                                        0xFF2979FF.toInt(),
                                        0xFFFFFFFF.toInt(),
                                        0xFF000000.toInt()
                                    )
                                    colors.forEach { c ->
                                        val selected = viewModel.brushSettings.color == c
                                        Box(
                                            Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(c))
                                                .border(
                                                    width = if (selected) 3.dp else 1.dp,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.Gray
                                                    },
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.updateBrush(color = c) }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }

                    if (showEyedropperChrome) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(viewModel.brushSettings.color))
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                                Column {
                                    Text(
                                        "Dropper — tap to sample color",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        "Samples the topmost visible pixel under your finger.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }

                    if (showMagicChrome) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(
                                "Magic erase · tolerance ${viewModel.brushSettings.magicTolerance}" +
                                    if (viewModel.magicBusy) " · working…" else "",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "Tap similar contiguous pixels to clear. Finish edges with Erase/Restore.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Tol",
                                    modifier = Modifier.width(48.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = viewModel.brushSettings.magicTolerance.toFloat(),
                                    onValueChange = {
                                        viewModel.updateBrush(magicTolerance = it.roundToInt())
                                    },
                                    valueRange = 8f..80f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    if (showDistortChrome) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(
                                "Distort · radius ${viewModel.distortSettings.radius.toInt()}px · strength ${(viewModel.distortSettings.strength * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "Tap to set center. Drag away = bulge, toward = pinch. Two-finger pans/zooms.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Radius",
                                    modifier = Modifier.width(56.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = viewModel.distortSettings.radius,
                                    onValueChange = { viewModel.updateDistort(radius = it) },
                                    valueRange = 12f..320f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Strength",
                                    modifier = Modifier.width(56.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = viewModel.distortSettings.strength,
                                    onValueChange = { viewModel.updateDistort(strength = it) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider()
                    }


                    if (showTransformChrome) {
                        val active = viewModel.layers.find { it.id == viewModel.activeLayerId }
                        val layerOpacity = active?.opacity ?: 1f
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(
                                "Layer opacity ${(layerOpacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "Ghost the active layer to line up, then restore to 100%. Not brush opacity.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Opacity",
                                    modifier = Modifier.width(56.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = layerOpacity,
                                    onValueChange = { v ->
                                        active?.let { viewModel.setLayerOpacity(it.id, v) }
                                    },
                                    valueRange = 0.05f..1f,
                                    enabled = active != null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Quick",
                                    modifier = Modifier.width(56.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                FilterChip(
                                    selected = layerOpacity in 0.28f..0.32f,
                                    onClick = {
                                        active?.let { viewModel.setLayerOpacity(it.id, 0.30f) }
                                    },
                                    enabled = active != null,
                                    label = { Text("30%") }
                                )
                                FilterChip(
                                    selected = layerOpacity >= 0.98f,
                                    onClick = {
                                        active?.let { viewModel.setLayerOpacity(it.id, 1f) }
                                    },
                                    enabled = active != null,
                                    label = { Text("100%") }
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { openBasePicker() }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (hasProject) "New" else "Pick photo")
                        }
                        TextButton(onClick = { openLayerPicker() }, enabled = hasProject) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add layer")
                        }
                        ToolChip("Pan", ToolMode.PAN, viewModel.toolMode, Icons.Default.PanTool) {
                            viewModel.setTool(ToolMode.PAN)
                        }
                        ToolChip("Paint", ToolMode.PAINT, viewModel.toolMode, Icons.Default.Brush) {
                            viewModel.setTool(ToolMode.PAINT)
                        }
                        ToolChip(
                            "Dropper",
                            ToolMode.EYEDROPPER,
                            viewModel.toolMode,
                            Icons.Default.Colorize
                        ) {
                            viewModel.setTool(ToolMode.EYEDROPPER)
                        }
                        ToolChip(
                            "Erase",
                            ToolMode.ERASER,
                            viewModel.toolMode,
                            Icons.Default.AutoFixOff
                        ) {
                            viewModel.setTool(ToolMode.ERASER)
                        }
                        ToolChip(
                            "Restore",
                            ToolMode.RESTORE,
                            viewModel.toolMode,
                            Icons.Default.Restore
                        ) {
                            viewModel.setTool(ToolMode.RESTORE)
                        }
                        ToolChip(
                            "Magic",
                            ToolMode.MAGIC,
                            viewModel.toolMode,
                            Icons.Default.AutoFixHigh
                        ) {
                            viewModel.setTool(ToolMode.MAGIC)
                        }
                        ToolChip(
                            "Distort",
                            ToolMode.DISTORT,
                            viewModel.toolMode,
                            Icons.Default.FilterTiltShift
                        ) {
                            viewModel.setTool(ToolMode.DISTORT)
                        }
                        ToolChip(
                            "Move",
                            ToolMode.TRANSFORM,
                            viewModel.toolMode,
                            Icons.Default.OpenWith
                        ) {
                            viewModel.setTool(ToolMode.TRANSFORM)
                        }
                        FilterChip(
                            selected = showLayers,
                            onClick = { showLayers = !showLayers },
                            enabled = hasProject,
                            label = {
                                Text(if (showLayers) "Layers" else "Layers (hidden)")
                            },
                            leadingIcon = { Icon(Icons.Default.Layers, null) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Canvas lives only between top and bottom chrome; clip so zoom/pan cannot paint over tools.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .clipToBounds()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!hasProject) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Pick a photo to start editing",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { openBasePicker() }) {
                            Text("Pick photo")
                        }
                    }
                }
            } else {
                EditorCanvas(
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                )
            }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    mode: ToolMode,
    current: ToolMode,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FilterChip(
        selected = current == mode,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) }
    )
}
