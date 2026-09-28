package com.embyplayernext.he

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.embyplayernext.he.ui.EmbyApp
import com.embyplayernext.he.ui.theme.EmbyPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmbyPlayerTheme { EmbyApp() } }
    }
}
