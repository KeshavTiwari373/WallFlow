package com.keshav.wallflow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keshav.wallflow.ui.navigation.WallFlowNavHost
import com.keshav.wallflow.ui.onboarding.OnboardingScreen
import com.keshav.wallflow.ui.theme.WallFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: the share intent survives recreation (theme change, process
        // restore), and importing it again would duplicate the image.
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            WallFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val onboarded by viewModel.onboardingComplete.collectAsStateWithLifecycle()
                    when (onboarded) {
                        null -> Unit // still loading the flag
                        false -> OnboardingScreen(onDone = viewModel::completeOnboarding)
                        true -> {
                            val pendingEditorId by viewModel.pendingEditorId.collectAsStateWithLifecycle()
                            WallFlowNavHost(
                                pendingEditorId = pendingEditorId,
                                onPendingEditorConsumed = viewModel::consumePendingEditor,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /** When launched via the share sheet with an image, import it and route to the editor. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri?.let { viewModel.handleSharedImage(it) }
    }
}
