package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbStorageDeviceInfo(
    val deviceName: String,
    val manufacturerName: String,
    val productName: String,
    val vendorId: Int,
    val productId: Int,
    val isMassStorage: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() {
            val name = when {
                productName.isNotBlank() && manufacturerName.isNotBlank() -> "$manufacturerName $productName"
                productName.isNotBlank() -> productName
                manufacturerName.isNotBlank() -> "$manufacturerName USB Drive"
                else -> "USB OTG Storage Device"
            }
            return if (isMassStorage) "$name (Mass Storage)" else name
        }
}

object UsbConnectionBus {
    private val _connectedDevice = MutableStateFlow<UsbStorageDeviceInfo?>(null)
    val connectedDevice: StateFlow<UsbStorageDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _usbPlugEvent = MutableStateFlow<UsbStorageDeviceInfo?>(null)
    val usbPlugEvent: StateFlow<UsbStorageDeviceInfo?> = _usbPlugEvent.asStateFlow()

    fun notifyAttached(deviceInfo: UsbStorageDeviceInfo) {
        Log.d("UsbConnectionBus", "USB Device Attached: ${deviceInfo.displayName}")
        _connectedDevice.value = deviceInfo
        _usbPlugEvent.value = deviceInfo
    }

    fun notifyDetached() {
        Log.d("UsbConnectionBus", "USB Device Detached")
        _connectedDevice.value = null
        _usbPlugEvent.value = null
    }

    fun dismissPlugPrompt() {
        _usbPlugEvent.value = null
    }

    fun simulateConnection(
        name: String = "Samsung Portable SSD T7",
        manufacturer: String = "Samsung Electronics"
    ) {
        val simulated = UsbStorageDeviceInfo(
            deviceName = "/dev/bus/usb/001/002",
            manufacturerName = manufacturer,
            productName = name,
            vendorId = 0x04e8,
            productId = 0x4001,
            isMassStorage = true
        )
        notifyAttached(simulated)
    }

    fun simulateDisconnection() {
        notifyDetached()
    }
}

class UsbStorageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("UsbStorageReceiver", "Received broadcast: $action")

        when (action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                if (device != null) {
                    var isMassStorage = false
                    for (i in 0 until device.interfaceCount) {
                        val iface = device.getInterface(i)
                        if (iface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
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
                } else {
                    // Fallback generic mass storage device
                    val fallback = UsbStorageDeviceInfo(
                        deviceName = "USB_OTG_DRIVE",
                        manufacturerName = "External",
                        productName = "Hard Disk Storage",
                        vendorId = 0,
                        productId = 0,
                        isMassStorage = true
                    )
                    UsbConnectionBus.notifyAttached(fallback)
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                UsbConnectionBus.notifyDetached()
            }
        }
    }
}
