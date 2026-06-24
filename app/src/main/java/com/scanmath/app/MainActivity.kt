package com.scanmath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scanmath.app.ui.ScanScreen
import com.scanmath.app.ui.theme.ScanMathTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanMathTheme {
                ScanScreen()
            }
        }
    }
}
