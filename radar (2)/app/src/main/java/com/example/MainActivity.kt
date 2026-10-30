package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.RadarViewModel
import com.example.ui.screens.MonitoringScreen
import com.example.ui.screens.ProfileDetailScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RadarPitchBlack

enum class RadarScreen {
    MONITORING,
    PROFILES,
    PROFILE_DETAIL
}

class MainActivity : ComponentActivity() {

    private val viewModel: RadarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

                LaunchedEffect(userMessage) {
                    userMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearUserMessage()
                    }
                }

                // Request notification permission on Android 13+
                val notifLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* no-op */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                var currentScreen by remember { mutableStateOf(RadarScreen.MONITORING) }
                var selectedPersonId by remember { mutableStateOf<Long?>(null) }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RadarPitchBlack)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                RadarScreen.MONITORING -> {
                                    MonitoringScreen(
                                        viewModel = viewModel,
                                        onNavigateToProfiles = {
                                            currentScreen = RadarScreen.PROFILES
                                        },
                                        onSelectPerson = { personWithPhotos ->
                                            selectedPersonId = personWithPhotos.person.id
                                            currentScreen = RadarScreen.PROFILE_DETAIL
                                        }
                                    )
                                }
                                RadarScreen.PROFILES -> {
                                    ProfilesScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            currentScreen = RadarScreen.MONITORING
                                        },
                                        onOpenPersonDetail = { personWithPhotos ->
                                            selectedPersonId = personWithPhotos.person.id
                                            currentScreen = RadarScreen.PROFILE_DETAIL
                                        }
                                    )
                                }
                                RadarScreen.PROFILE_DETAIL -> {
                                    selectedPersonId?.let { id ->
                                        ProfileDetailScreen(
                                            viewModel = viewModel,
                                            personId = id,
                                            onBack = {
                                                currentScreen = RadarScreen.PROFILES
                                            }
                                        )
                                    } ?: run {
                                        currentScreen = RadarScreen.PROFILES
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
