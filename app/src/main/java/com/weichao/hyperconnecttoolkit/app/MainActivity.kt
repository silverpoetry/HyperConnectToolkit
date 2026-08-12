package com.weichao.hyperconnecttoolkit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weichao.hyperconnecttoolkit.ui.home.HomeRoute
import com.weichao.hyperconnecttoolkit.ui.home.HomeViewModel
import com.weichao.hyperconnecttoolkit.ui.theme.HyperConnectTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<HomeViewModel>()

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
                HomeRoute(
                    state = state,
                    events = viewModel.events,
                    onRefresh = viewModel::refresh,
                    onOpenNotificationSettings = viewModel::openCrossDeviceNotification,
                    onRestartServices = viewModel::restartServices,
                    onFeatureChanged = viewModel::setFeatureEnabled,
                )
            }
        }
    }
}
