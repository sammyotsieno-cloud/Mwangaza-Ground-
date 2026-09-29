package org.SamilliMed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.SamilliMed.app.ui.navigation.AppNavigation
import org.SamilliMed.app.ui.screens.SamilliMedSplashScreen
import org.SamilliMed.app.ui.theme.SamilliMedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamilliMedTheme {
                MainActivityContent()
            }
        }
    }
}

@Composable
fun MainActivityContent() {
    // rememberSaveable keeps the post-splash destination through Android
    // configuration recreation (for example portrait <-> landscape).
    var showSplash by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(true)
    }

    if (showSplash) {
        SamilliMedSplashScreen(
            onSplashFinished = { showSplash = false }
        )
    } else {
        AppNavigation()
    }
}
