package com.example

import com.example.data.model.PhotoItem
import com.example.receiver.UsbConnectionBus
import com.example.receiver.UsbStorageDeviceInfo
import com.example.util.CryptoUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {
    @Test
    fun testCryptoSha256Calculation() {
        val testString = "DiskVault Zero-Cloud Private Photo Backup"
        val hash = CryptoUtil.calculateSha256(testString.toByteArray())
        assertEquals(64, hash.length) // Standard SHA-256 hex string length

        val streamHash = CryptoUtil.calculateSha256(ByteArrayInputStream(testString.toByteArray()))
        assertEquals(hash, streamHash)
    }

    @Test
    fun testPhotoItemSizeFormatting() {
        val photoKb = PhotoItem(
            id = "1",
            title = "test1.jpg",
            uriString = "file:///tmp/1.jpg",
            dateAdded = 1000L,
            sizeBytes = 512 * 1024L
        )
        assertTrue(photoKb.formattedSize.contains("KB"))

        val photoMb = PhotoItem(
            id = "2",
            title = "test2.jpg",
            uriString = "file:///tmp/2.jpg",
            dateAdded = 1000L,
            sizeBytes = 4 * 1024 * 1024L
        )
        assertTrue(photoMb.formattedSize.contains("MB"))
    }

    @Test
    fun testShortenHash() {
        val fullHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val shortened = CryptoUtil.shortenHash(fullHash, 6)
        assertEquals("e3b0c4...52b855", shortened)
    }

    @Test
    fun testUsbDeviceConnectionFlow() {
        val device = UsbStorageDeviceInfo(
            deviceName = "/dev/bus/usb/001/002",
            manufacturerName = "Western Digital",
            productName = "My Passport 2TB",
            vendorId = 0x1058,
            productId = 0x0820,
            isMassStorage = true
        )

        assertEquals("Western Digital My Passport 2TB (Mass Storage)", device.displayName)

        UsbConnectionBus.notifyAttached(device)
        assertEquals(device, UsbConnectionBus.connectedDevice.value)
        assertEquals(device, UsbConnectionBus.usbPlugEvent.value)

        UsbConnectionBus.dismissPlugPrompt()
        assertNull(UsbConnectionBus.usbPlugEvent.value)
        assertEquals(device, UsbConnectionBus.connectedDevice.value)

        UsbConnectionBus.notifyDetached()
        assertNull(UsbConnectionBus.connectedDevice.value)
    }
}
