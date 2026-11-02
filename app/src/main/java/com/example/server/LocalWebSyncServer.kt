package com.example.server

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.PhotoItem
import com.example.data.repository.PhotoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ServerStatus(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val ipAddress: String = "127.0.0.1",
    val activeConnections: Int = 0,
    val totalDownloadsCount: Int = 0,
    val totalBytesTransferred: Long = 0L,
    val lastClientIp: String = "",
    val lastDownloadedFile: String = ""
)

class LocalWebSyncServer(
    private val context: Context,
    private val repository: PhotoRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _status = MutableStateFlow(ServerStatus())
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    fun startServer(port: Int = 8080, ipAddress: String) {
        if (_status.value.isRunning) return

        serverJob = scope.launch {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
                serverSocket = socket

                _status.value = _status.value.copy(
                    isRunning = true,
                    port = port,
                    ipAddress = ipAddress
                )
                Log.d("LocalWebSyncServer", "Server started on port $port, bound to $ipAddress")

                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (!socket.isClosed) {
                            Log.e("LocalWebSyncServer", "Error accepting client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalWebSyncServer", "Failed to start server", e)
                _status.value = _status.value.copy(isRunning = false)
            }
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            _status.value = _status.value.copy(isRunning = false, activeConnections = 0)
            Log.d("LocalWebSyncServer", "Server stopped")
        } catch (e: Exception) {
            Log.e("LocalWebSyncServer", "Error stopping server", e)
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            _status.value = _status.value.copy(
                activeConnections = _status.value.activeConnections + 1,
                lastClientIp = socket.inetAddress.hostAddress ?: ""
            )
            try {
                socket.soTimeout = 15000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val output = BufferedOutputStream(socket.getOutputStream())

                val requestLine = reader.readLine()
                if (requestLine.isNullOrBlank()) {
                    socket.close()
                    return@launch
                }

                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val rawPath = parts.getOrNull(1) ?: "/"
                val path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name())

                // Consume remaining headers
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    line = reader.readLine()
                }

                when {
                    path == "/" || path == "/index.html" -> serveHtmlDashboard(output)
                    path == "/api/status" -> serveStatusJson(output)
                    path == "/api/photos" -> servePhotosJson(output)
                    path == "/api/download-all.zip" -> serveAllPhotosZip(output, onlyUnbacked = false)
                    path == "/api/download-incremental.zip" -> serveAllPhotosZip(output, onlyUnbacked = true)
                    path.startsWith("/api/photo/") -> {
                        val photoId = path.removePrefix("/api/photo/")
                        servePhotoFile(output, photoId)
                    }
                    path.startsWith("/api/confirm/") -> {
                        val photoId = path.removePrefix("/api/confirm/")
                        confirmBackupForPhoto(output, photoId)
                    }
                    else -> serve404(output)
                }

                output.flush()
            } catch (e: Exception) {
                Log.e("LocalWebSyncServer", "Error handling client request", e)
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {}
                _status.value = _status.value.copy(
                    activeConnections = maxOf(0, _status.value.activeConnections - 1)
                )
            }
        }
    }

    private suspend fun serveHtmlDashboard(output: OutputStream) = withContext(Dispatchers.IO) {
        val photos = repository.currentPhotosList()
        val backedUpCount = photos.count { it.isBackedUp }
        val pendingCount = photos.size - backedUpCount
        val totalBytes = photos.sumOf { it.sizeBytes }
        val totalMb = String.format("%.1f", totalBytes / (1024.0 * 1024.0))

        val html = buildDashboardHtml(photos, backedUpCount, pendingCount, totalMb)
        val bytes = html.toByteArray(StandardCharsets.UTF_8)

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
    }

    private suspend fun serveStatusJson(output: OutputStream) = withContext(Dispatchers.IO) {
        val photos = repository.currentPhotosList()
        val backedUpCount = photos.count { it.isBackedUp }
        val json = """
            {
                "appName": "DiskVault",
                "version": "1.0",
                "device": "${android.os.Build.MODEL}",
                "totalPhotos": ${photos.size},
                "backedUpPhotos": $backedUpCount,
                "pendingPhotos": ${photos.size - backedUpCount},
                "zeroCloudGuaranteed": true
            }
        """.trimIndent()
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
    }

    private suspend fun servePhotosJson(output: OutputStream) = withContext(Dispatchers.IO) {
        val photos = repository.currentPhotosList()
        val sb = StringBuilder("[")
        photos.forEachIndexed { index, p ->
            sb.append("""{
                "id": "${p.id}",
                "title": "${p.title.replace("\"", "\\\"")}",
                "sizeBytes": ${p.sizeBytes},
                "sha256": "${p.sha256Checksum}",
                "isBackedUp": ${p.isBackedUp},
                "downloadUrl": "/api/photo/${p.id}"
            }""")
            if (index < photos.size - 1) sb.append(",")
        }
        sb.append("]")

        val bytes = sb.toString().toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
    }

    private suspend fun servePhotoFile(output: OutputStream, photoId: String) = withContext(Dispatchers.IO) {
        val photo = repository.getPhotoById(photoId)
        if (photo == null) {
            serve404(output)
            return@withContext
        }

        val inputStream = repository.openPhotoInputStream(photo)
        if (inputStream == null) {
            serve404(output)
            return@withContext
        }

        try {
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: ${photo.mimeType}\r\n" +
                    "Content-Length: ${photo.sizeBytes}\r\n" +
                    "Content-Disposition: attachment; filename=\"${photo.title}\"\r\n" +
                    "X-SHA256-Checksum: ${photo.sha256Checksum}\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(header.toByteArray(StandardCharsets.UTF_8))

            val buffer = ByteArray(32768)
            var bytesRead: Int
            var totalSent = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalSent += bytesRead
            }

            // Record as backed up to desktop hard disk
            repository.recordPhotoBackup(
                photoId = photo.id,
                fileName = photo.title,
                fileSize = photo.sizeBytes,
                sha256 = photo.sha256Checksum,
                targetDiskName = "Local Desktop Hard Disk (Web Hub)",
                destinationPath = "Downloads/${photo.title}"
            )

            _status.value = _status.value.copy(
                totalDownloadsCount = _status.value.totalDownloadsCount + 1,
                totalBytesTransferred = _status.value.totalBytesTransferred + totalSent,
                lastDownloadedFile = photo.title
            )
        } finally {
            inputStream.close()
        }
    }

    private suspend fun serveAllPhotosZip(output: OutputStream, onlyUnbacked: Boolean) = withContext(Dispatchers.IO) {
        val allPhotos = repository.currentPhotosList()
        val photosToZip = if (onlyUnbacked) allPhotos.filter { !it.isBackedUp } else allPhotos

        val filename = if (onlyUnbacked) "DiskVault_Unbacked_Photos.zip" else "DiskVault_Photos_Backup.zip"

        // Chunked transfer encoding for streaming dynamic ZIP
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/zip\r\n" +
                "Content-Disposition: attachment; filename=\"$filename\"\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))

        val chunkedOut = ChunkedOutputStream(output)
        val zipOut = ZipOutputStream(BufferedOutputStream(chunkedOut))

        var totalBytesArchived = 0L
        for (photo in photosToZip) {
            val stream = repository.openPhotoInputStream(photo) ?: continue
            try {
                val entryName = photo.title.ifBlank { "photo_${photo.id}.jpg" }
                val entry = ZipEntry(entryName)
                zipOut.putNextEntry(entry)

                val buffer = ByteArray(32768)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    zipOut.write(buffer, 0, read)
                    totalBytesArchived += read
                }
                zipOut.closeEntry()

                // Mark backed up
                repository.recordPhotoBackup(
                    photoId = photo.id,
                    fileName = photo.title,
                    fileSize = photo.sizeBytes,
                    sha256 = photo.sha256Checksum,
                    targetDiskName = "Local Desktop Hard Disk (ZIP Archive)",
                    destinationPath = "Desktop Backup/$entryName"
                )
            } catch (e: Exception) {
                Log.e("LocalWebSyncServer", "Failed to add ${photo.title} to zip", e)
            } finally {
                stream.close()
            }
        }

        zipOut.finish()
        zipOut.flush()
        chunkedOut.finish()

        _status.value = _status.value.copy(
            totalDownloadsCount = _status.value.totalDownloadsCount + photosToZip.size,
            totalBytesTransferred = _status.value.totalBytesTransferred + totalBytesArchived,
            lastDownloadedFile = filename
        )
    }

    private suspend fun confirmBackupForPhoto(output: OutputStream, photoId: String) = withContext(Dispatchers.IO) {
        val photo = repository.getPhotoById(photoId)
        if (photo != null) {
            repository.recordPhotoBackup(
                photoId = photo.id,
                fileName = photo.title,
                fileSize = photo.sizeBytes,
                sha256 = photo.sha256Checksum,
                targetDiskName = "Local PC Hard Disk",
                destinationPath = "HardDrive:/Photos/${photo.title}"
            )
        }
        val response = "{\"success\":true}"
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
    }

    private fun serve404(output: OutputStream) {
        val body = "404 Not Found".toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(body)
    }

    private fun buildDashboardHtml(
        photos: List<PhotoItem>,
        backedUpCount: Int,
        pendingCount: Int,
        totalMb: String
    ): String {
        val photoCards = photos.joinToString("") { photo ->
            val statusBadge = if (photo.isBackedUp) {
                """<span class="badge verified">Backed Up to Hard Disk</span>"""
            } else {
                """<span class="badge pending">Pending Backup</span>"""
            }
            """
            <div class="photo-card">
                <div class="photo-preview">
                    <img src="/api/photo/${photo.id}" loading="lazy" alt="${photo.title}">
                </div>
                <div class="photo-info">
                    <div class="photo-title" title="${photo.title}">${photo.title}</div>
                    <div class="photo-meta">${photo.formattedSize} &bull; ${photo.width}&times;${photo.height}</div>
                    <div class="photo-hash">SHA-256: ${photo.sha256Checksum.take(12)}...</div>
                    <div class="photo-actions">
                        $statusBadge
                        <a href="/api/photo/${photo.id}" download="${photo.title}" class="btn-download" onclick="markSynced('${photo.id}')">Save to Hard Disk</a>
                    </div>
                </div>
            </div>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>DiskVault - Private Hard Disk Sync</title>
            <style>
                :root {
                    --bg: #090D16;
                    --surface: #111827;
                    --card: #162032;
                    --border: #1E293B;
                    --cyan: #00E5FF;
                    --emerald: #10B981;
                    --amber: #F59E0B;
                    --text: #F8FAFC;
                    --text-muted: #94A3B8;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background: var(--bg);
                    color: var(--text);
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                    padding: 24px;
                    line-height: 1.5;
                }
                .container { max-width: 1200px; margin: 0 auto; }
                header {
                    background: linear-gradient(135deg, #111827 0%, #162032 100%);
                    border: 1px solid var(--border);
                    border-radius: 16px;
                    padding: 28px;
                    margin-bottom: 24px;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    flex-wrap: wrap;
                    gap: 16px;
                }
                .brand h1 {
                    font-size: 26px;
                    font-weight: 700;
                    color: var(--cyan);
                    letter-spacing: -0.5px;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .brand p { color: var(--text-muted); font-size: 14px; margin-top: 4px; }
                .shield-badge {
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    background: rgba(16, 185, 129, 0.15);
                    border: 1px solid rgba(16, 185, 129, 0.3);
                    color: var(--emerald);
                    padding: 8px 16px;
                    border-radius: 999px;
                    font-size: 13px;
                    font-weight: 600;
                }
                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 16px;
                    margin-bottom: 24px;
                }
                .stat-box {
                    background: var(--surface);
                    border: 1px solid var(--border);
                    border-radius: 12px;
                    padding: 18px;
                }
                .stat-label { font-size: 13px; color: var(--text-muted); }
                .stat-value { font-size: 24px; font-weight: 700; color: var(--text); margin-top: 6px; }
                .stat-value.cyan { color: var(--cyan); }
                .stat-value.emerald { color: var(--emerald); }
                .stat-value.amber { color: var(--amber); }

                .action-bar {
                    background: var(--surface);
                    border: 1px solid var(--border);
                    border-radius: 14px;
                    padding: 20px;
                    margin-bottom: 24px;
                    display: flex;
                    gap: 16px;
                    flex-wrap: wrap;
                    align-items: center;
                    justify-content: space-between;
                }
                .action-btns { display: flex; gap: 12px; flex-wrap: wrap; }
                .btn {
                    padding: 12px 20px;
                    border-radius: 10px;
                    font-size: 14px;
                    font-weight: 600;
                    text-decoration: none;
                    cursor: pointer;
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    transition: all 0.2s;
                    border: none;
                }
                .btn-primary {
                    background: var(--cyan);
                    color: #042f38;
                }
                .btn-primary:hover { opacity: 0.9; transform: translateY(-1px); }
                .btn-secondary {
                    background: var(--card);
                    border: 1px solid var(--border);
                    color: var(--text);
                }
                .btn-secondary:hover { background: #1E293B; }

                .gallery-title {
                    font-size: 18px;
                    font-weight: 600;
                    margin-bottom: 16px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .gallery-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
                    gap: 18px;
                }
                .photo-card {
                    background: var(--card);
                    border: 1px solid var(--border);
                    border-radius: 14px;
                    overflow: hidden;
                    display: flex;
                    flex-direction: column;
                    transition: transform 0.2s, border-color 0.2s;
                }
                .photo-card:hover {
                    transform: translateY(-2px);
                    border-color: rgba(0, 229, 255, 0.4);
                }
                .photo-preview {
                    height: 180px;
                    background: #000;
                    overflow: hidden;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .photo-preview img {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                }
                .photo-info { padding: 14px; flex: 1; display: flex; flex-direction: column; }
                .photo-title {
                    font-weight: 600;
                    font-size: 14px;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                .photo-meta { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
                .photo-hash {
                    font-family: monospace;
                    font-size: 11px;
                    color: #64748B;
                    margin-top: 4px;
                }
                .photo-actions {
                    margin-top: 12px;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }
                .badge {
                    font-size: 11px;
                    padding: 3px 8px;
                    border-radius: 6px;
                    font-weight: 600;
                }
                .badge.verified { background: rgba(16, 185, 129, 0.2); color: var(--emerald); }
                .badge.pending { background: rgba(245, 158, 11, 0.2); color: var(--amber); }
                .btn-download {
                    background: var(--cyan);
                    color: #042f38;
                    font-size: 12px;
                    font-weight: 600;
                    padding: 6px 12px;
                    border-radius: 6px;
                    text-decoration: none;
                }
                .btn-download:hover { opacity: 0.9; }

                footer {
                    margin-top: 40px;
                    padding-top: 20px;
                    border-top: 1px solid var(--border);
                    text-align: center;
                    color: var(--text-muted);
                    font-size: 13px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <div class="brand">
                        <h1>DiskVault Desktop Portal</h1>
                        <p>Direct peer-to-peer Wi-Fi photo backup directly to your computer's hard disk</p>
                    </div>
                    <div class="shield-badge">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                        Zero-Cloud Privacy Guarantee
                    </div>
                </header>

                <div class="stats-grid">
                    <div class="stat-box">
                        <div class="stat-label">Total Device Photos</div>
                        <div class="stat-value cyan">${photos.size}</div>
                    </div>
                    <div class="stat-box">
                        <div class="stat-label">Backed Up to Hard Disk</div>
                        <div class="stat-value emerald">$backedUpCount</div>
                    </div>
                    <div class="stat-box">
                        <div class="stat-label">Pending Transfer</div>
                        <div class="stat-value amber">$pendingCount</div>
                    </div>
                    <div class="stat-box">
                        <div class="stat-label">Total Storage Size</div>
                        <div class="stat-value">$totalMb MB</div>
                    </div>
                </div>

                <div class="action-bar">
                    <div>
                        <strong style="color: var(--text);">Hard Disk Backup Destination:</strong>
                        <div style="font-size: 13px; color: var(--text-muted);">Save directly to your PC, Mac, External SSD, or NAS Hard Drive</div>
                    </div>
                    <div class="action-btns">
                        <a href="/api/download-all.zip" class="btn btn-primary">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                            Download All to Hard Disk (.ZIP)
                        </a>
                        <a href="/api/download-incremental.zip" class="btn btn-secondary">
                            Download $pendingCount Pending Photos
                        </a>
                    </div>
                </div>

                <div class="gallery-title">
                    <span>Photo Staging Vault (${photos.size} items)</span>
                    <span style="font-size: 13px; font-weight: normal; color: var(--text-muted);">Bit-for-bit uncompressed original transfer</span>
                </div>

                <div class="gallery-grid">
                    $photoCards
                </div>

                <footer>
                    DiskVault Private Storage &bull; End-to-end Local Wi-Fi socket &bull; 0 KB transmitted over the Internet
                </footer>
            </div>

            <script>
                function markSynced(id) {
                    fetch('/api/confirm/' + id, { method: 'POST' }).catch(() => {});
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}

/**
 * A simple helper to write HTTP/1.1 chunked response
 */
class ChunkedOutputStream(private val raw: OutputStream) : OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        val hexLen = Integer.toHexString(len) + "\r\n"
        raw.write(hexLen.toByteArray(StandardCharsets.US_ASCII))
        raw.write(b, off, len)
        raw.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
    }

    fun finish() {
        raw.write("0\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        raw.flush()
    }

    override fun flush() {
        raw.flush()
    }
}
