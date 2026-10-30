package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.PersonNicknameEntity
import com.example.data.PersonPhotoEntity
import com.example.data.PersonWithPhotos
import com.example.data.PhotoEnrollmentResult
import com.example.ui.RadarViewModel
import com.example.ui.theme.RadarBorder
import com.example.ui.theme.RadarCyanPrimary
import com.example.ui.theme.RadarDangerRed
import com.example.ui.theme.RadarDarkSurface
import com.example.ui.theme.RadarDarkSurfaceVariant
import com.example.ui.theme.RadarGreenSecondary
import com.example.ui.theme.RadarPitchBlack
import com.example.ui.theme.RadarWarningAmber
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import java.io.File

@Composable
fun ProfileDetailScreen(
    viewModel: RadarViewModel,
    personId: Long,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val profiles by viewModel.registeredProfiles.collectAsStateWithLifecycle()
    val personWithPhotos = profiles.find { it.person.id == personId }

    val nicknames by viewModel.getNicknamesForPerson(personId).collectAsStateWithLifecycle(emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddNicknameDialog by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var enrollmentMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Gallery Photo Picker
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            enrollmentMessage = "Analyzing photo and generating face embedding..."
            isError = false
            viewModel.enrollPhotoFromUri(personId, uri) { result ->
                isProcessing = false
                when (result) {
                    is PhotoEnrollmentResult.Success -> {
                        enrollmentMessage = result.message
                        isError = false
                    }
                    is PhotoEnrollmentResult.Error -> {
                        enrollmentMessage = result.reason
                        isError = true
                    }
                }
            }
        }
    }

    // Camera Photo Capture
    val cameraCapture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            isProcessing = true
            enrollmentMessage = "Analyzing photo and generating face embedding..."
            isError = false
            viewModel.enrollBitmap(personId, bitmap) { result ->
                isProcessing = false
                when (result) {
                    is PhotoEnrollmentResult.Success -> {
                        enrollmentMessage = result.message
                        isError = false
                    }
                    is PhotoEnrollmentResult.Error -> {
                        enrollmentMessage = result.reason
                        isError = true
                    }
                }
            }
        }
    }

    if (personWithPhotos == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RadarPitchBlack),
            contentAlignment = Alignment.Center
        ) {
            Text("Profile not found", color = TextPrimaryDark)
        }
        return
    }

    val photoCount = personWithPhotos.validPhotoCount
    val isReady = personWithPhotos.isReadyForRecognition

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarPitchBlack)
    ) {
        // TOP BAR
        Surface(
            color = RadarDarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = RadarCyanPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = personWithPhotos.person.name.uppercase(),
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "$photoCount photos • ${nicknames.size} funny nicknames",
                        color = TextSecondaryDark,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // TABS: PHOTOS vs NICKNAMES
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = RadarDarkSurface,
            contentColor = RadarCyanPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = RadarCyanPrimary
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photos ($photoCount)")
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Nicknames (${nicknames.size})")
                    }
                }
            )
        }

        if (selectedTab == 0) {
            // TAB 0: REFERENCE PHOTOS
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // STATUS BADGE CARD
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isReady) RadarDarkSurfaceVariant else RadarWarningAmber.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isReady) RadarGreenSecondary else RadarWarningAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isReady) {
                                "Ready for real-time camera recognition ($photoCount verified photos)."
                            } else {
                                "Profile needs at least 1 verified face photo to trigger recognition alerts."
                            },
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ADD PHOTO BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            galleryPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_photo_gallery_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyanPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add from Gallery", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { cameraCapture.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("take_photo_camera_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RadarCyanPrimary)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = RadarCyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo", fontSize = 13.sp)
                    }
                }

                // PROCESSING / ENROLLMENT FEEDBACK BANNER
                if (isProcessing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RadarDarkSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = RadarCyanPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = enrollmentMessage ?: "Processing...",
                            color = TextPrimaryDark,
                            fontSize = 13.sp
                        )
                    }
                } else if (enrollmentMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isError) RadarDangerRed.copy(alpha = 0.2f) else RadarGreenSecondary.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) RadarDangerRed else RadarGreenSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = enrollmentMessage!!,
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { enrollmentMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondaryDark)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "REFERENCE PHOTOS (${personWithPhotos.photos.size})",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (personWithPhotos.photos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Photos Yet",
                                color = TextPrimaryDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Use the buttons above to add photos from your gallery or camera. RADAR will automatically extract face embeddings for real-time matching.",
                                color = TextSecondaryDark,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(personWithPhotos.photos) { photo ->
                            PhotoGridItem(
                                photo = photo,
                                onDelete = { viewModel.deletePhoto(photo) }
                            )
                        }
                    }
                }
            }
        } else {
            // TAB 1: AI FUNNY NICKNAMES
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Overview card
                Card(
                    colors = CardDefaults.cardColors(containerColor = RadarDarkSurfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = null,
                            tint = RadarWarningAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "RADAR speaks one of these creative, harmless titles when ${personWithPhotos.person.name} is recognized in view.",
                            color = TextPrimaryDark,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Regenerate & Add Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.regenerateNicknamesForPerson(personId, personWithPhotos.person.name)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = RadarDarkSurfaceVariant,
                            contentColor = RadarCyanPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("regenerate_nicknames_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Regenerate Fresh", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showAddNicknameDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyanPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_custom_nickname_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Custom", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SAVED NICKNAMES FOR THIS PROFILE",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (nicknames.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = null,
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Nicknames Generated Yet",
                                color = TextPrimaryDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap \"Regenerate Fresh\" to create a distinct collection of hilarious titles for ${personWithPhotos.person.name}.",
                                color = TextSecondaryDark,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(nicknames) { nick ->
                            NicknameItemCard(
                                nicknameEntity = nick,
                                onDelete = { viewModel.deleteNickname(nick) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Custom Nickname Dialog
    if (showAddNicknameDialog) {
        var customInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddNicknameDialog = false },
            title = { Text("Add Custom Nickname", color = TextPrimaryDark) },
            text = {
                Column {
                    Text(
                        "Enter a playful, friendly nickname (e.g., \"The Coffee Magician\" or \"The Master of Puns\").",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        label = { Text("Nickname") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadarCyanPrimary,
                            unfocusedBorderColor = RadarBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_nickname_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customInput.isNotBlank()) {
                            viewModel.addCustomNickname(personId, customInput)
                            showAddNicknameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyanPrimary),
                    modifier = Modifier.testTag("save_custom_nickname_button")
                ) {
                    Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNicknameDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = RadarDarkSurface
        )
    }
}

@Composable
private fun NicknameItemCard(
    nicknameEntity: PersonNicknameEntity,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RadarDarkSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nickname_item_${nicknameEntity.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nicknameEntity.nickname,
                        color = RadarCyanPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (nicknameEntity.isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = RadarGreenSecondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "CUSTOM",
                                color = RadarGreenSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "“${nicknameEntity.announcement}”",
                    color = TextPrimaryDark,
                    fontSize = 12.sp
                )
                if (nicknameEntity.timesUsed > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Spoken ${nicknameEntity.timesUsed} ${if (nicknameEntity.timesUsed == 1) "time" else "times"}",
                        color = TextSecondaryDark,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete nickname",
                    tint = RadarDangerRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: PersonPhotoEntity,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RadarDarkSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .testTag("photo_item_${photo.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(photo.imagePath),
                contentDescription = "Reference photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Delete button overlay
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete photo",
                    tint = RadarDangerRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Embedding verified tag
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "192-D EMBEDDING VERIFIED",
                    color = RadarGreenSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
