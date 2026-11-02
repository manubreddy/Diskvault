package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.PhotoItem
import com.example.ui.theme.AmberPending
import com.example.ui.theme.BgDark
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DiskVaultViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotosScreen(
    viewModel: DiskVaultViewModel,
    modifier: Modifier = Modifier
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedPhotoIds.collectAsStateWithLifecycle()

    var activeFilter by remember { mutableStateOf("ALL") }
    var selectedPhotoForDetail by remember { mutableStateOf<PhotoItem?>(null) }

    // Android Photo Picker Launcher (compliant with zero-permission play policies)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addPhotosFromPicker(uris)
        }
    }

    val filteredPhotos = when (activeFilter) {
        "BACKED_UP" -> photos.filter { it.isBackedUp }
        "PENDING" -> photos.filter { !it.isBackedUp }
        else -> photos
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Action Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Photo Vault",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${photos.size} total &bull; ${photos.count { it.isBackedUp }} on hard disk",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_photos_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = Color(0xFF042F38),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Import", color = Color(0xFF042F38), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips and selection controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeFilter == "ALL",
                    onClick = { activeFilter = "ALL" },
                    label = { Text("All (${photos.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0x3300E5FF),
                        selectedLabelColor = CyanNeon,
                        labelColor = TextSecondary
                    )
                )
                FilterChip(
                    selected = activeFilter == "PENDING",
                    onClick = { activeFilter = "PENDING" },
                    label = { Text("Pending (${photos.count { !it.isBackedUp }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0x33F59E0B),
                        selectedLabelColor = AmberPending,
                        labelColor = TextSecondary
                    )
                )
                FilterChip(
                    selected = activeFilter == "BACKED_UP",
                    onClick = { activeFilter = "BACKED_UP" },
                    label = { Text("On Disk (${photos.count { it.isBackedUp }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0x3310B981),
                        selectedLabelColor = EmeraldShield,
                        labelColor = TextSecondary
                    )
                )
            }

            TextButton(
                onClick = {
                    if (selectedIds.size == photos.size) {
                        viewModel.deselectAll()
                    } else {
                        viewModel.selectAll()
                    }
                }
            ) {
                Text(
                    text = if (selectedIds.size == photos.size) "Deselect" else "Select All",
                    color = CyanNeon,
                    fontSize = 12.sp
                )
            }
        }

        // Multiselect floating bar
        if (selectedIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFF162032),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.startHardDiskBackup(onlySelected = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("backup_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                tint = Color(0xFF003822),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup to Disk", color = Color(0xFF003822), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        IconButton(onClick = { viewModel.deselectAll() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Photos Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredPhotos, key = { it.id }) { photo ->
                val isSelected = photo.id in selectedIds
                PhotoGridCard(
                    photo = photo,
                    isSelected = isSelected,
                    onToggleSelect = { viewModel.togglePhotoSelection(photo.id) },
                    onClickDetail = { selectedPhotoForDetail = photo }
                )
            }
        }
    }

    // Detail Modal Dialog
    selectedPhotoForDetail?.let { photo ->
        PhotoDetailModal(
            photo = photo,
            onDismiss = { selectedPhotoForDetail = null }
        )
    }
}

@Composable
fun PhotoGridCard(
    photo: PhotoItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClickDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSelected) CyanNeon else Color(0xFF1E293B),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClickDetail() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF050811))
            ) {
                // Async image from file path or URI
                val imageModel = remember(photo.uriString) {
                    if (photo.uriString.startsWith("file://")) {
                        File(photo.uriString.removePrefix("file://"))
                    } else {
                        photo.uriString
                    }
                }

                AsyncImage(
                    model = imageModel,
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Selection Checkbox
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color(0x88000000), CircleShape)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CyanNeon,
                            checkmarkColor = Color(0xFF042F38),
                            uncheckedColor = Color.White
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Backup Status Tag
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Surface(
                        color = if (photo.isBackedUp) Color(0xDD003822) else Color(0xDD3E2700),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (photo.isBackedUp) EmeraldShield else AmberPending
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (photo.isBackedUp) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = if (photo.isBackedUp) EmeraldShield else AmberPending,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (photo.isBackedUp) "ON DISK" else "PENDING",
                                color = if (photo.isBackedUp) EmeraldShield else AmberPending,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = photo.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = photo.formattedSize, color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "SHA: ${photo.sha256Checksum.take(6)}...",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoDetailModal(
    photo: PhotoItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photo Integrity Details",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    val imageModel = remember(photo.uriString) {
                        if (photo.uriString.startsWith("file://")) {
                            File(photo.uriString.removePrefix("file://"))
                        } else {
                            photo.uriString
                        }
                    }
                    AsyncImage(
                        model = imageModel,
                        contentDescription = photo.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                DetailRow(label = "Filename", value = photo.title)
                DetailRow(label = "Resolution", value = "${photo.width} × ${photo.height}")
                DetailRow(label = "File Size", value = "${photo.formattedSize} (${photo.sizeBytes} bytes)")
                DetailRow(
                    label = "Backup Status",
                    value = if (photo.isBackedUp) "Backed Up to ${photo.backupDestination}" else "Pending Backup to Hard Disk"
                )
                if (photo.backedUpAt > 0) {
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    DetailRow(label = "Backup Time", value = df.format(Date(photo.backedUpAt)))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Cryptographic SHA-256 Hash", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = photo.sha256Checksum,
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyanPrimary, RoundedCornerShape(10.dp))
                ) {
                    Text(text = "Close", color = Color(0xFF042F38), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
