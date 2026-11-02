package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.receiver.UsbConnectionBus
import com.example.receiver.UsbStorageDeviceInfo
import com.example.receiver.UsbStorageReceiver
import com.example.ui.components.BackupProgressModal
import com.example.ui.components.UsbDeviceConnectedDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.PrivacyAuditScreen
import com.example.ui.screens.WebSyncScreen
import com.example.ui.theme.BgDark
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.DiskVaultViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: DiskVaultViewModel by viewModels()
    private val usbReceiver = UsbStorageReceiver()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register runtime broadcast receiver for USB OTG storage device attachment/detachment
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // Check already connected USB devices on launch
        checkInitialConnectedUsbDevices()

        // Check if Activity was launched directly by USB attachment Intent
        handleUsbLaunchIntent(intent)

        setContent {
            MyApplicationTheme {
                val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
                val photos by viewModel.photos.collectAsStateWithLifecycle()
                val usbProgress by viewModel.usbBackupProgress.collectAsStateWithLifecycle()
                val selectedDiskName by viewModel.selectedDiskName.collectAsStateWithLifecycle()
                val selectedDiskUri by viewModel.selectedDiskUri.collectAsStateWithLifecycle()
                val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
                val usbPlugEvent by viewModel.usbPlugEvent.collectAsStateWithLifecycle()
                val requestFolderPicker by viewModel.requestFolderPicker.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }

                // SAF Folder Picker
                val folderPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri: Uri? ->
                    viewModel.onFolderPickerHandled()
                    if (uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (_: Exception) {}

                        val folderName = uri.pathSegments.lastOrNull()?.split(":")?.lastOrNull()
                            ?: "Connected External Hard Disk"
                        viewModel.setSelectedHardDisk(uri, folderName)
                        viewModel.startHardDiskBackup(onlySelected = false)
                    }
                }

                LaunchedEffect(requestFolderPicker) {
                    if (requestFolderPicker) {
                        folderPicker.launch(null)
                    }
                }

                LaunchedEffect(uiMessage) {
                    uiMessage?.let {
                        snackbarHostState.showSnackbar(it.text)
                        viewModel.clearUiMessage()
                    }
                }

                // Hardware back press handler
                BackHandler(enabled = currentTab != AppTab.OVERVIEW) {
                    viewModel.setTab(AppTab.OVERVIEW)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BgDark,
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "DiskVault",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Surface(
                                        color = Color(0x3310B981),
                                        shape = RoundedCornerShape(100.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6610B981))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = EmeraldShield,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "NO CLOUD",
                                                color = EmeraldShield,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = SurfaceDark,
                                titleContentColor = TextPrimary
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("main_bottom_nav")
                        ) {
                            NavigationBarItem(
                                selected = currentTab == AppTab.OVERVIEW,
                                onClick = { viewModel.setTab(AppTab.OVERVIEW) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.OVERVIEW) Icons.Default.Dashboard else Icons.Outlined.Dashboard,
                                        contentDescription = "Dashboard"
                                    )
                                },
                                label = { Text("Dashboard") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF042F38),
                                    selectedTextColor = CyanNeon,
                                    indicatorColor = CyanNeon,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_dashboard")
                            )

                            NavigationBarItem(
                                selected = currentTab == AppTab.PHOTOS,
                                onClick = { viewModel.setTab(AppTab.PHOTOS) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.PHOTOS) Icons.Default.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                        contentDescription = "Photos"
                                    )
                                },
                                label = { Text("Photos") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF042F38),
                                    selectedTextColor = CyanNeon,
                                    indicatorColor = CyanNeon,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_photos")
                            )

                            NavigationBarItem(
                                selected = currentTab == AppTab.WEB_HUB,
                                onClick = { viewModel.setTab(AppTab.WEB_HUB) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.WEB_HUB) Icons.Default.Wifi else Icons.Outlined.Wifi,
                                        contentDescription = "Wi-Fi Portal"
                                    )
                                },
                                label = { Text("Wi-Fi Sync") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF042F38),
                                    selectedTextColor = CyanNeon,
                                    indicatorColor = CyanNeon,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_wifi_sync")
                            )

                            NavigationBarItem(
                                selected = currentTab == AppTab.AUDIT,
                                onClick = { viewModel.setTab(AppTab.AUDIT) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.AUDIT) Icons.Default.Security else Icons.Outlined.Security,
                                        contentDescription = "Privacy Shield"
                                    )
                                },
                                label = { Text("Privacy") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF042F38),
                                    selectedTextColor = EmeraldShield,
                                    indicatorColor = EmeraldShield,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_privacy")
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            AppTab.OVERVIEW -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateTab = { tab -> viewModel.setTab(tab) }
                            )
                            AppTab.PHOTOS -> PhotosScreen(viewModel = viewModel)
                            AppTab.WEB_HUB -> WebSyncScreen(viewModel = viewModel)
                            AppTab.AUDIT -> PrivacyAuditScreen(viewModel = viewModel)
                        }
                    }
                }

                // USB Storage Device Attached Alert Dialog (Triggered by BroadcastReceiver)
                usbPlugEvent?.let { device ->
                    val pendingPhotos = photos.filter { !it.isBackedUp }
                    val totalPendingBytes = pendingPhotos.sumOf { it.sizeBytes }

                    UsbDeviceConnectedDialog(
                        device = device,
                        pendingPhotoCount = pendingPhotos.size,
                        totalSizeBytes = totalPendingBytes,
                        selectedDiskFolder = selectedDiskName,
                        onInitiateTransfer = {
                            if (selectedDiskUri == null) {
                                folderPicker.launch(null)
                            } else {
                                viewModel.startHardDiskBackup(onlySelected = false)
                            }
                            viewModel.dismissUsbPlugEvent()
                        },
                        onPickFolder = {
                            folderPicker.launch(null)
                        },
                        onDismiss = {
                            viewModel.dismissUsbPlugEvent()
                        }
                    )
                }

                // Active backup modal
                usbProgress?.let { progress ->
                    BackupProgressModal(
                        progress = progress,
                        diskName = selectedDiskName,
                        onDismiss = { viewModel.dismissBackupProgress() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbLaunchIntent(intent)
    }

    private fun handleUsbLaunchIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            if (device != null) {
                var isMassStorage = false
                for (i in 0 until device.interfaceCount) {
                    if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        isMassStorage = true
                        break
                    }
                }
                val info = UsbStorageDeviceInfo(
                    deviceName = device.deviceName ?: "USB Drive",
                    manufacturerName = device.manufacturerName ?: "",
                    productName = device.productName ?: "",
                    vendorId = device.vendorId,
                    productId = device.productId,
                    isMassStorage = isMassStorage
                )
                UsbConnectionBus.notifyAttached(info)
            }
        }
    }

    private fun checkInitialConnectedUsbDevices() {
        try {
            val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
            val deviceList = usbManager.deviceList
            for (device in deviceList.values) {
                var isMassStorage = false
                for (i in 0 until device.interfaceCount) {
                    if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        isMassStorage = true
                        break
                    }
                }
                if (isMassStorage || device.interfaceCount > 0) {
                    val info = UsbStorageDeviceInfo(
                        deviceName = device.deviceName ?: "USB Drive",
                        manufacturerName = device.manufacturerName ?: "",
                        productName = device.productName ?: "",
                        vendorId = device.vendorId,
                        productId = device.productId,
                        isMassStorage = isMassStorage
                    )
                    UsbConnectionBus.notifyAttached(info)
                    break
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
