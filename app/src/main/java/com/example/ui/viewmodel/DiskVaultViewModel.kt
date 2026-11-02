package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DiskVaultDatabase
import com.example.data.model.BackupDestinationType
import com.example.data.model.HardDiskTarget
import com.example.data.model.PhotoItem
import com.example.data.model.TransferLog
import com.example.data.repository.PhotoRepository
import com.example.receiver.UsbConnectionBus
import com.example.receiver.UsbStorageDeviceInfo
import com.example.server.LocalWebSyncServer
import com.example.server.ServerStatus
import com.example.storage.BackupProgress
import com.example.storage.UsbHardDiskManager
import com.example.util.NetworkUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    OVERVIEW("Dashboard"),
    PHOTOS("Photo Vault"),
    WEB_HUB("Wi-Fi Sync"),
    AUDIT("Zero-Cloud Shield")
}

data class UiMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false
)

class DiskVaultViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DiskVaultDatabase.getDatabase(application)
    private val repository = PhotoRepository(application, database.diskVaultDao())
    private val webServer = LocalWebSyncServer(application, repository)
    private val usbHardDiskManager = UsbHardDiskManager(application, repository)

    val photos: StateFlow<List<PhotoItem>> = repository.photos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hardDiskTargets: StateFlow<List<HardDiskTarget>> = repository.hardDiskTargets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<TransferLog>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverStatus: StateFlow<ServerStatus> = webServer.status

    // USB OTG Connection monitoring from BroadcastReceiver
    val connectedUsbDevice: StateFlow<UsbStorageDeviceInfo?> = UsbConnectionBus.connectedDevice
    val usbPlugEvent: StateFlow<UsbStorageDeviceInfo?> = UsbConnectionBus.usbPlugEvent

    private val _currentTab = MutableStateFlow(AppTab.OVERVIEW)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _selectedPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<String>> = _selectedPhotoIds.asStateFlow()

    private val _usbBackupProgress = MutableStateFlow<BackupProgress?>(null)
    val usbBackupProgress: StateFlow<BackupProgress?> = _usbBackupProgress.asStateFlow()

    private val _selectedDiskUri = MutableStateFlow<Uri?>(null)
    val selectedDiskUri: StateFlow<Uri?> = _selectedDiskUri.asStateFlow()

    private val _selectedDiskName = MutableStateFlow<String>("External Hard Disk (USB OTG)")
    val selectedDiskName: StateFlow<String> = _selectedDiskName.asStateFlow()

    private val _requestFolderPicker = MutableStateFlow(false)
    val requestFolderPicker: StateFlow<Boolean> = _requestFolderPicker.asStateFlow()

    private val _uiMessage = MutableStateFlow<UiMessage?>(null)
    val uiMessage: StateFlow<UiMessage?> = _uiMessage.asStateFlow()

    init {
        // Observe USB connection events to update drive label
        viewModelScope.launch {
            UsbConnectionBus.connectedDevice.collect { device ->
                if (device != null) {
                    _selectedDiskName.value = device.displayName
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun toggleServer(customPort: Int = 8080) {
        if (serverStatus.value.isRunning) {
            webServer.stopServer()
            _uiMessage.value = UiMessage(text = "Local sync server stopped")
        } else {
            val ip = NetworkUtil.getLocalIpAddress()
            webServer.startServer(customPort, ip)
            _uiMessage.value = UiMessage(text = "Local server active on http://$ip:$customPort")
        }
    }

    fun setSelectedHardDisk(uri: Uri, displayName: String) {
        _selectedDiskUri.value = uri
        _selectedDiskName.value = displayName
        viewModelScope.launch {
            repository.saveHardDiskTarget(
                id = uri.toString(),
                name = displayName,
                uriString = uri.toString(),
                type = BackupDestinationType.USB_HARD_DISK
            )
        }
    }

    fun handleUsbTransferInitiated() {
        UsbConnectionBus.dismissPlugPrompt()
        if (_selectedDiskUri.value == null) {
            _requestFolderPicker.value = true
        } else {
            startHardDiskBackup(onlySelected = false)
        }
    }

    fun onFolderPickerHandled() {
        _requestFolderPicker.value = false
    }

    fun dismissUsbPlugEvent() {
        UsbConnectionBus.dismissPlugPrompt()
    }

    fun simulateUsbPlugIn(
        name: String = "Samsung Portable SSD T7",
        manufacturer: String = "Samsung Electronics"
    ) {
        UsbConnectionBus.simulateConnection(name, manufacturer)
        _uiMessage.value = UiMessage(text = "USB storage device connected: $name")
    }

    fun simulateUsbUnplug() {
        UsbConnectionBus.simulateDisconnection()
        _uiMessage.value = UiMessage(text = "USB storage device disconnected")
    }

    fun startHardDiskBackup(onlySelected: Boolean = false) {
        val uri = _selectedDiskUri.value
        if (uri == null) {
            _requestFolderPicker.value = true
            return
        }

        val allPhotos = photos.value
        val targets = if (onlySelected) {
            val selected = _selectedPhotoIds.value
            allPhotos.filter { it.id in selected }
        } else {
            allPhotos.filter { !it.isBackedUp }
        }

        if (targets.isEmpty()) {
            _uiMessage.value = UiMessage(text = "No pending photos to back up!")
            return
        }

        viewModelScope.launch {
            val result = usbHardDiskManager.backupPhotosToHardDisk(
                treeUri = uri,
                hardDiskName = _selectedDiskName.value,
                photos = targets
            ) { progress ->
                _usbBackupProgress.value = progress
            }

            if (result.isSuccess) {
                _uiMessage.value = UiMessage(
                    text = "Backup complete! ${result.getOrNull()} photos saved directly to ${_selectedDiskName.value}"
                )
                _selectedPhotoIds.value = emptySet()
            } else {
                _uiMessage.value = UiMessage(
                    text = "Backup failed: ${result.exceptionOrNull()?.message}",
                    isError = true
                )
            }
        }
    }

    fun dismissBackupProgress() {
        _usbBackupProgress.value = null
    }

    fun addPhotosFromPicker(uris: List<Uri>) {
        viewModelScope.launch {
            repository.addPickedPhotos(uris)
            _uiMessage.value = UiMessage(text = "Imported ${uris.size} photos into DiskVault")
        }
    }

    fun togglePhotoSelection(photoId: String) {
        val current = _selectedPhotoIds.value.toMutableSet()
        if (current.contains(photoId)) {
            current.remove(photoId)
        } else {
            current.add(photoId)
        }
        _selectedPhotoIds.value = current
    }

    fun selectAll() {
        _selectedPhotoIds.value = photos.value.map { it.id }.toSet()
    }

    fun deselectAll() {
        _selectedPhotoIds.value = emptySet()
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            repository.clearAllAuditLogs()
            _uiMessage.value = UiMessage(text = "Audit logs cleared")
        }
    }

    override fun onCleared() {
        super.onCleared()
        webServer.stopServer()
    }
}
