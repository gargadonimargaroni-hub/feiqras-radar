package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.example.ml.HumorLevel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.camera.CameraFacingMode
import com.example.data.PersonWithPhotos
import com.example.ml.RadarStatus
import com.example.ui.RadarViewModel
import com.example.ui.components.CameraPreviewView
import com.example.ui.theme.RadarCyanPrimary
import com.example.ui.theme.RadarDangerRed
import com.example.ui.theme.RadarDarkSurface
import com.example.ui.theme.RadarDarkSurfaceVariant
import com.example.ui.theme.RadarGreenSecondary
import com.example.ui.theme.RadarPitchBlack
import com.example.ui.theme.RadarWarningAmber
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    viewModel: RadarViewModel,
    onNavigateToProfiles: () -> Unit,
    onSelectPerson: (PersonWithPhotos) -> Unit
) {
    val context = LocalContext.current
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    val radarStatus by viewModel.radarStatus.collectAsStateWithLifecycle()
    val trackedFaces by viewModel.trackedFaces.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isAlarmActive by viewModel.isAlarmActive.collectAsStateWithLifecycle()
    val ttsErrorMessage by viewModel.ttsErrorMessage.collectAsStateWithLifecycle()
    val currentCommentary by viewModel.currentCommentary.collectAsStateWithLifecycle()
    val humorSettings by viewModel.humorSettings.collectAsStateWithLifecycle()
    val profiles by viewModel.registeredProfiles.collectAsStateWithLifecycle()
    val facingMode by viewModel.facingMode.collectAsStateWithLifecycle()
    val threshold by viewModel.confidenceThreshold.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isSimultaneousSupported by viewModel.isSimultaneousSupported.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            viewModel.startMonitoring()
        } else {
            viewModel.showMessage("Camera permission is required to monitor for registered persons.")
        }
    }

    var showSettingsSheet by remember { mutableStateOf(false) }

    val isDangerAlert = radarStatus is RadarStatus.DangerRecognized

    val infiniteTransition = rememberInfiniteTransition(label = "DangerPulse")
    val alertPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlertPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarPitchBlack)
    ) {
        // TOP APP BAR & STATUS
        Surface(
            color = RadarDarkSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and Radar status icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDangerAlert -> RadarDangerRed
                                    isMonitoring -> RadarCyanPrimary
                                    else -> TextSecondaryDark
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RADAR",
                        color = TextPrimaryDark,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }

                // Action buttons: Mute, Camera Flip, Profiles, Settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier.testTag("mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute alerts" else "Mute alerts",
                            tint = if (isMuted) RadarWarningAmber else RadarCyanPrimary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.switchCamera() },
                        enabled = isMonitoring,
                        modifier = Modifier.testTag("camera_switch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera (Front/Rear)",
                            tint = if (isMonitoring) RadarCyanPrimary else TextSecondaryDark.copy(alpha = 0.5f)
                        )
                    }

                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sensitivity Settings",
                            tint = RadarCyanPrimary
                        )
                    }

                    IconButton(
                        onClick = onNavigateToProfiles,
                        modifier = Modifier.testTag("profiles_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Registered Profiles",
                            tint = RadarGreenSecondary
                        )
                    }
                }
            }
        }

        // STATUS BANNER
        StatusBanner(
            status = radarStatus,
            isMonitoring = isMonitoring,
            facingMode = facingMode,
            alertPulse = alertPulse,
            isMuted = isMuted
        )

        // CAMERA PREVIEW & RADAR OVERLAY
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (hasCameraPermission) {
                CameraPreviewView(
                    modifier = Modifier.fillMaxSize(),
                    cameraManager = viewModel.cameraManager,
                    isMonitoring = isMonitoring,
                    trackedFaces = trackedFaces,
                    isDangerAlert = isDangerAlert
                )
            } else {
                // Permission placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = RadarWarningAmber,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RADAR needs camera access to monitor for recognized persons in real time.",
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyanPrimary),
                        modifier = Modifier.testTag("grant_camera_permission_button")
                    ) {
                        Text("Grant Camera Access", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Lens & hardware indicator badge overlay
            if (isMonitoring && hasCameraPermission) {
                Surface(
                    color = RadarDarkSurface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${facingMode.name} CAMERA • ${if (isSimultaneousSupported) "DUAL HW" else "SINGLE HW"}",
                        color = RadarCyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live AI Humor Commentary Overlay Banner
            currentCommentary?.let { commentary ->
                Surface(
                    color = RadarDangerRed.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI COMMENTARY • ${commentary.nickname.uppercase()}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "“${commentary.announcement}”",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM CONTROLS & TARGETS CAROUSEL
        Surface(
            color = RadarDarkSurface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Registered Targets Mini Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REGISTERED TARGETS (${profiles.size})",
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Manage",
                        color = RadarCyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onNavigateToProfiles() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (profiles.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RadarDarkSurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToProfiles() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RadarWarningAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No persons registered yet. Tap to add photos to monitor for.",
                                color = TextPrimaryDark,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(profiles) { personWithPhotos ->
                            val isReady = personWithPhotos.isReadyForRecognition
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isReady) RadarDarkSurfaceVariant else RadarDarkSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable { onSelectPerson(personWithPhotos) }
                                    .testTag("target_chip_${personWithPhotos.person.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isReady) RadarGreenSecondary else RadarWarningAmber)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = personWithPhotos.person.name,
                                        color = TextPrimaryDark,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(${personWithPhotos.validPhotoCount})",
                                        color = TextSecondaryDark,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // TTS Error notice if speech synthesis is unavailable on device
                ttsErrorMessage?.let { errMsg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RadarDarkSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RadarWarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errMsg,
                                color = TextPrimaryDark,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearTtsError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SOUND CONTROLS & ALARM SILENCE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clearly visible Mute/Unmute button
                    FilledTonalButton(
                        onClick = { viewModel.toggleMute() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isMuted) RadarWarningAmber else RadarDarkSurfaceVariant,
                            contentColor = if (isMuted) Color.Black else RadarCyanPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("prominent_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Sound" else "Mute Sound",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMuted) "UNMUTE ALERTS" else "MUTE SOUND",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Separate Silence Alarm button if alarm is active
                    if (isAlarmActive) {
                        Button(
                            onClick = { viewModel.silenceAlarm() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadarDangerRed.copy(alpha = 0.85f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("silence_alarm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SILENCE ALARM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PRIMARY ACTION BUTTON (START / STOP MONITORING)
                if (!isMonitoring) {
                    Button(
                        onClick = {
                            if (hasCameraPermission) {
                                viewModel.startMonitoring()
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_monitoring_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarCyanPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "START MONITORING",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopMonitoring() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("stop_monitoring_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarDangerRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "STOP MONITORING",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }

    // SETTINGS / SENSITIVITY BOTTOM SHEET
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = RadarDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "RADAR Settings",
                    color = TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // AI HUMOR & NICKNAMES SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = null,
                                tint = RadarWarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Funny Nicknames & Voice Commentary",
                                color = TextPrimaryDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Speaks playful, harmless fictional titles when registered persons are detected.",
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = humorSettings.isEnabled,
                        onCheckedChange = { viewModel.setHumorEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RadarCyanPrimary,
                            checkedTrackColor = RadarCyanPrimary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("humor_enabled_switch")
                    )
                }

                if (humorSettings.isEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Humor Style",
                        color = TextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = humorSettings.humorLevel == HumorLevel.EXTRA_SILLY,
                            onClick = { viewModel.setHumorLevel(HumorLevel.EXTRA_SILLY) },
                            label = { Text("Extra Silly (Absurd)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RadarWarningAmber.copy(alpha = 0.25f),
                                selectedLabelColor = RadarWarningAmber
                            )
                        )
                        FilterChip(
                            selected = humorSettings.humorLevel == HumorLevel.MILD,
                            onClick = { viewModel.setHumorLevel(HumorLevel.MILD) },
                            label = { Text("Mild (Whimsical)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RadarCyanPrimary.copy(alpha = 0.25f),
                                selectedLabelColor = RadarCyanPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Announcement Interval: Every ${humorSettings.intervalSeconds}s",
                        color = TextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 12, 15, 20).forEach { seconds ->
                            FilterChip(
                                selected = humorSettings.intervalSeconds == seconds,
                                onClick = { viewModel.setHumorInterval(seconds) },
                                label = { Text("${seconds}s") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Language: ${humorSettings.language} (US)",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )

                        FilledTonalButton(
                            onClick = { viewModel.clearAllNicknames() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = RadarDarkSurfaceVariant,
                                contentColor = RadarCyanPrimary
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("clear_all_nicknames_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear & Regenerate", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = RadarDarkSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Confidence Threshold",
                        color = TextPrimaryDark,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${(threshold * 100).toInt()}%",
                        color = RadarCyanPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = threshold,
                    onValueChange = { viewModel.setThreshold(it) },
                    valueRange = 0.60f..0.85f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = RadarCyanPrimary,
                        activeTrackColor = RadarCyanPrimary,
                        inactiveTrackColor = RadarDarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("confidence_threshold_slider")
                )

                Text(
                    text = "Higher threshold reduces false alarms. Lower threshold recognizes people faster under difficult lighting.",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Camera Architecture",
                    color = TextPrimaryDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSimultaneousSupported) {
                        "Your device hardware reports support for simultaneous camera streaming."
                    } else {
                        "Simultaneous front+back camera streaming is not supported on this device. RADAR uses high-speed camera switching."
                    },
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyanPrimary)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    status: RadarStatus,
    isMonitoring: Boolean,
    facingMode: CameraFacingMode,
    alertPulse: Float,
    isMuted: Boolean
) {
    val bgColor by animateColorAsState(
        targetValue = when (status) {
            is RadarStatus.DangerRecognized -> RadarDangerRed.copy(alpha = 0.95f)
            is RadarStatus.Scanning -> RadarDarkSurfaceVariant
            is RadarStatus.CameraError -> RadarWarningAmber.copy(alpha = 0.8f)
            RadarStatus.Ready -> RadarDarkSurfaceVariant
        },
        label = "BannerColor"
    )

    Surface(
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when (status) {
                    is RadarStatus.DangerRecognized -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DANGER! DANGER!",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Target: ${status.personName.uppercase()} (${(status.confidence * 100).toInt()}%) • ${if (isMuted) "MUTED" else "LOUD ALARM SOUNDING"}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is RadarStatus.Scanning -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RadarCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "MONITORING ACTIVE — SCANNING",
                            color = RadarCyanPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    is RadarStatus.CameraError -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = status.message,
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    RadarStatus.Ready -> {
                        Text(
                            text = "RADAR STANDBY — READY TO MONITOR",
                            color = TextSecondaryDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            if (isMuted) {
                Surface(
                    color = RadarWarningAmber,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "MUTED",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
