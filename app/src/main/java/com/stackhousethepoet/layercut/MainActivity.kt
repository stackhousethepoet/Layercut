package com.stackhousethepoet.layercut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.stackhousethepoet.layercut.editor.EditorViewModel
import com.stackhousethepoet.layercut.theme.LayerCutTheme
import com.stackhousethepoet.layercut.ui.EditorScreen

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LayerCutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EditorScreen(viewModel = viewModel)
                }
            }
        }
    }
}
