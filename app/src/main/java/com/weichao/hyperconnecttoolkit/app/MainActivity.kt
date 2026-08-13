package com.weichao.hyperconnecttoolkit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weichao.hyperconnecttoolkit.ui.about.AboutRoute
import com.weichao.hyperconnecttoolkit.ui.home.HomeRoute
import com.weichao.hyperconnecttoolkit.ui.home.HomeViewModel
import com.weichao.hyperconnecttoolkit.ui.theme.HyperConnectTheme
import com.weichao.hyperconnecttoolkit.diagnostics.DiagnosticLogExporter

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<HomeViewModel>()
    private val createDiagnosticDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { destination ->
        if (destination != null) viewModel.exportDiagnostics(destination)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        setContent {
            HyperConnectTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var showAbout by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showAbout) { showAbout = false }

                if (showAbout) {
                    AboutRoute(onNavigateUp = { showAbout = false })
                } else {
                    HomeRoute(
                        state = state,
                        events = viewModel.events,
                        onRefresh = viewModel::refresh,
                        onOpenNotificationSettings = viewModel::openCrossDeviceNotification,
                        onRestartServices = viewModel::restartServices,
                        onFeatureChanged = viewModel::setFeatureEnabled,
                        onDetailedLoggingChanged = viewModel::setDetailedLoggingEnabled,
                        onExportDiagnostics = {
                            createDiagnosticDocument.launch(
                                DiagnosticLogExporter.defaultFileName(),
                            )
                        },
                        onOpenAbout = { showAbout = true },
                    )
                }
            }
        }
    }
}
