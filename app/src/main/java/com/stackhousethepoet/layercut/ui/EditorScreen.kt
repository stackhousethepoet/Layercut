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
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoLibrary
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
import com.stackhousethepoet.layercut.editor.EditorViewModel
import com.stackhousethepoet.layercut.editor.ToolMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var showLayers by remember { mutableStateOf(true) }
    val hasProject = viewModel.layers.isNotEmpty()
    val showBrushChrome =
        viewModel.toolMode == ToolMode.PAINT || viewModel.toolMode == ToolMode.ERASER

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

                    if (showBrushChrome) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(
                                "Brush ${viewModel.brushSettings.size.toInt()}px · opacity ${(viewModel.brushSettings.opacity * 100).toInt()}%",
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
                                    valueRange = 2f..120f,
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
                            if (viewModel.toolMode == ToolMode.PAINT) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            "Erase",
                            ToolMode.ERASER,
                            viewModel.toolMode,
                            Icons.Default.AutoFixOff
                        ) {
                            viewModel.setTool(ToolMode.ERASER)
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
                            label = { Text("Layers") },
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
