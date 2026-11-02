package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.HeroBannerCard
import com.example.ui.components.StatMetricCard
import com.example.ui.theme.AmberPending
import com.example.ui.theme.BgDark
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DiskVaultViewModel

@Composable
fun DashboardScreen(
    viewModel: DiskVaultViewModel,
    onNavigateTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val selectedDiskUri by viewModel.selectedDiskUri.collectAsStateWithLifecycle()
    val selectedDiskName by viewModel.selectedDiskName.collectAsStateWithLifecycle()
    val connectedUsb by viewModel.connectedUsbDevice.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // SAF Document Tree Picker for external Hard Drive / SSD / USB OTG
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val pathSegs = uri.pathSegments
            val folderName = pathSegs.lastOrNull()?.split(":")?.lastOrNull() ?: "Connected External Hard Disk"
            viewModel.setSelectedHardDisk(uri, folderName)
        }
    }

    val totalCount = photos.size
    val backedUpCount = photos.count { it.isBackedUp }
    val pendingCount = totalCount - backedUpCount
    val totalBytes = photos.sumOf { it.sizeBytes }
    val formattedTotalMb = String.format("%.1f MB", totalBytes / (1024.0 * 1024.0))

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            HeroBannerCard(
                totalPhotos = totalCount,
                backedUpPhotos = backedUpCount
            )
        }

        // Metrics Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMetricCard(
                        label = "Total Photos",
                        value = "$totalCount",
                        subtext = formattedTotalMb,
                        icon = Icons.Default.PermMedia,
                        accentColor = CyanNeon,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        label = "On Hard Disk",
                        value = "$backedUpCount",
                        subtext = "Verified bit-level",
                        icon = Icons.Default.CheckCircle,
                        accentColor = EmeraldShield,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMetricCard(
                        label = "Pending Backup",
                        value = "$pendingCount",
                        subtext = if (pendingCount == 0) "All safe on disk" else "Needs hard disk sync",
                        icon = Icons.Default.HourglassEmpty,
                        accentColor = if (pendingCount > 0) AmberPending else EmeraldShield,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        label = "Cloud Sync",
                        value = "0 Bytes",
                        subtext = "100% Zero Cloud",
                        icon = Icons.Default.Shield,
                        accentColor = EmeraldShield,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Feature 1: Local Wi-Fi Web Sync (Desktop PC Hard Disk)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0x2200E5FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Wi-Fi Hard Disk Sync",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Desktop PC & Laptop Portal",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Server Running Pill
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (serverStatus.isRunning) Color(0x3310B981) else Color(0x3364748B),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (serverStatus.isRunning) EmeraldShield else Color(0xFF475569)
                            )
                        ) {
                            Text(
                                text = if (serverStatus.isRunning) "SERVER ACTIVE" else "STANDBY",
                                color = if (serverStatus.isRunning) EmeraldShield else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (serverStatus.isRunning) {
                            "Open this address in your PC or Mac browser to download all photos directly to your computer's hard disk:"
                        } else {
                            "Start the local peer-to-peer web server to access and download your photo library from your computer's desktop browser without cloud intermediary."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    if (serverStatus.isRunning) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF090D16))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "http://${serverStatus.ipAddress}:${serverStatus.port}",
                                    color = CyanNeon,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "Show QR",
                                    tint = CyanNeon,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onNavigateTab(AppTab.WEB_HUB) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleServer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serverStatus.isRunning) Color(0xFFEF4444) else CyanPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_server_button")
                        ) {
                            Icon(
                                imageVector = if (serverStatus.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (serverStatus.isRunning) Color.White else Color(0xFF042F38),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (serverStatus.isRunning) "Stop Server" else "Start Wi-Fi Sync",
                                color = if (serverStatus.isRunning) Color.White else Color(0xFF042F38),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onNavigateTab(AppTab.WEB_HUB) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.testTag("open_web_hub_tab")
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("QR Code")
                        }
                    }
                }
            }
        }

        // Feature 2: USB OTG / Direct External Hard Disk Cable Backup
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0x2210B981), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = EmeraldShield,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Direct USB Hard Disk",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "USB-C SSD / External Drive / SD Card",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        // USB Connection Status Pill
                        val isUsbAttached = connectedUsb != null
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (isUsbAttached) Color(0x3310B981) else Color(0x3364748B),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isUsbAttached) EmeraldShield else Color(0xFF475569)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isUsbAttached) EmeraldShield else Color(0xFF94A3B8), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isUsbAttached) "OTG ATTACHED" else "NO USB DEVICE",
                                    color = if (isUsbAttached) EmeraldShield else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (connectedUsb != null) {
                            "BroadcastReceiver detected: ${connectedUsb?.displayName}. Ready for immediate direct hardware file transfer."
                        } else {
                            "A BroadcastReceiver actively listens for USB OTG storage devices (SSDs, hard disks, flash drives). Plug in a drive to trigger instant backup."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090D16))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = if (selectedDiskUri != null) EmeraldShield else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDiskName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (selectedDiskUri != null) "Folder mapped • Ready for direct transfer" else "Tap below to choose destination folder on hard drive",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { folderPicker.launch(null) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_hard_disk_folder_button")
                        ) {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Disk Folder")
                        }

                        Button(
                            onClick = { viewModel.handleUsbTransferInitiated() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                            enabled = pendingCount > 0,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_to_disk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                tint = Color(0xFF003822),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Initiate Transfer ($pendingCount)",
                                color = Color(0xFF003822),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // USB OTG Simulator for Testing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BroadcastReceiver Test:",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    if (connectedUsb == null) {
                                        viewModel.simulateUsbPlugIn()
                                    } else {
                                        viewModel.simulateUsbUnplug()
                                    }
                                },
                                modifier = Modifier.testTag("simulate_usb_button")
                            ) {
                                Text(
                                    text = if (connectedUsb == null) "Simulate USB OTG Plug-In" else "Simulate Unplug",
                                    color = CyanNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Privacy Guarantee Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x3310B981), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldShield,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "100% Zero-Cloud Guarantee",
                            color = EmeraldShield,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "DiskVault runs entirely offline on your local network or USB wire. No external servers, no accounts, no subscriptions, complete privacy.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
