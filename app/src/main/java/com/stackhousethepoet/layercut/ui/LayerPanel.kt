package com.stackhousethepoet.layercut.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stackhousethepoet.layercut.editor.EditorLayer
import com.stackhousethepoet.layercut.editor.EditorViewModel

@Composable
fun LayerPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val layers = viewModel.layers
    val activeId = viewModel.activeLayerId

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(8.dp)
    ) {
        Text("Layers", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top of list = top of stack visually: reverse display
            itemsIndexed(layers.asReversed()) { displayIndex, layer ->
                val realIndex = layers.lastIndex - displayIndex
                LayerRow(
                    layer = layer,
                    selected = layer.id == activeId,
                    onSelect = { viewModel.selectLayer(layer.id) },
                    onToggleVisible = {
                        viewModel.setLayerVisibility(layer.id, !layer.visible)
                    },
                    onOpacity = { viewModel.setLayerOpacity(layer.id, it) },
                    onMoveUp = { viewModel.moveLayerUp(layer.id) },
                    onMoveDown = { viewModel.moveLayerDown(layer.id) },
                    onDelete = { viewModel.deleteLayer(layer.id) },
                    canMoveUp = realIndex < layers.lastIndex,
                    canMoveDown = realIndex > 0,
                    canDelete = layers.size > 1
                )
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: EditorLayer,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onOpacity: (Float) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onSelect)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleVisible, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Visibility"
                )
            }
            Text(
                layer.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
        Text("Opacity ${(layer.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = layer.opacity,
            onValueChange = onOpacity,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
