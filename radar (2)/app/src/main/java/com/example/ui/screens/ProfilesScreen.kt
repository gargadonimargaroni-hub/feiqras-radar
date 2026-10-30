package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.PersonWithPhotos
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
fun ProfilesScreen(
    viewModel: RadarViewModel,
    onBack: () -> Unit,
    onOpenPersonDetail: (PersonWithPhotos) -> Unit
) {
    BackHandler { onBack() }

    val profiles by viewModel.registeredProfiles.collectAsStateWithLifecycle()
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var personToDelete by remember { mutableStateOf<PersonWithPhotos?>(null) }
    var personToRename by remember { mutableStateOf<PersonWithPhotos?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarPitchBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.testTag("profiles_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Monitoring",
                            tint = RadarCyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "REGISTERED TARGETS",
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // CONSENT & PRIVACY NOTICE
            Card(
                colors = CardDefaults.cardColors(containerColor = RadarDarkSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = RadarGreenSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Consent & Local Biometric Privacy",
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensure you have the explicit permission and consent of anyone you register. All facial embeddings and reference photos are kept strictly local on this device and are never transmitted to any cloud or server.",
                            color = TextSecondaryDark,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // PROFILES LIST
            if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Targets Registered",
                            color = TextPrimaryDark,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button below to create a profile and add reference photos of people you want RADAR to detect.",
                            color = TextSecondaryDark,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(profiles) { item ->
                        ProfileCard(
                            personWithPhotos = item,
                            onClick = { onOpenPersonDetail(item) },
                            onRename = { personToRename = item },
                            onDelete = { personToDelete = item }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // FAB to add person
        FloatingActionButton(
            onClick = { showAddPersonDialog = true },
            containerColor = RadarCyanPrimary,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_person_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Person Profile")
        }
    }

    // Add Person Dialog
    if (showAddPersonDialog) {
        var nameInput by remember { mutableStateOf("") }
        var notesInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPersonDialog = false },
            title = {
                Text("Register Person", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Enter the person's name. You will be able to add reference photos right after.",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Person Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadarCyanPrimary,
                            unfocusedBorderColor = RadarBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("person_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadarCyanPrimary,
                            unfocusedBorderColor = RadarBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.createPerson(nameInput, notesInput) { id ->
                                showAddPersonDialog = false
                            }
                        }
                    },
                    enabled = nameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyanPrimary),
                    modifier = Modifier.testTag("confirm_add_person_button")
                ) {
                    Text("Create Profile", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPersonDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = RadarDarkSurface
        )
    }

    // Rename Dialog
    personToRename?.let { target ->
        var renameInput by remember { mutableStateOf(target.person.name) }
        AlertDialog(
            onDismissRequest = { personToRename = null },
            title = { Text("Rename Profile", color = TextPrimaryDark) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RadarCyanPrimary,
                        unfocusedBorderColor = RadarBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.updatePerson(target.person.copy(name = renameInput.trim()))
                            personToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyanPrimary)
                ) {
                    Text("Save", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { personToRename = null }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = RadarDarkSurface
        )
    }

    // Delete Confirmation Dialog
    personToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            title = { Text("Delete Profile?", color = TextPrimaryDark) },
            text = {
                Text(
                    "Are you sure you want to delete '${target.person.name}' and all associated photos and face recognition embeddings from this device?",
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePerson(target)
                        personToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarDangerRed)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { personToDelete = null }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            },
            containerColor = RadarDarkSurface
        )
    }
}

@Composable
private fun ProfileCard(
    personWithPhotos: PersonWithPhotos,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val photoCount = personWithPhotos.validPhotoCount
    val isReady = personWithPhotos.isReadyForRecognition
    val firstPhoto = personWithPhotos.photos.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(containerColor = RadarDarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("profile_card_${personWithPhotos.person.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or Avatar
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RadarDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (firstPhoto != null && File(firstPhoto.imagePath).exists()) {
                    AsyncImage(
                        model = File(firstPhoto.imagePath),
                        contentDescription = "Photo of ${personWithPhotos.person.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = personWithPhotos.person.name,
                    color = TextPrimaryDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isReady) RadarGreenSecondary else RadarWarningAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isReady) "READY ($photoCount photos)" else "NEEDS PHOTOS ($photoCount)",
                        color = if (isReady) RadarGreenSecondary else RadarWarningAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action buttons
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename profile",
                    tint = TextSecondaryDark
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete profile",
                    tint = RadarDangerRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}
