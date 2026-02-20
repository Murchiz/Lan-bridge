package com.lanbridge.network

import com.lanbridge.model.SelectedFileMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.nio.file.Files

actual object PlatformFileAccess {
    actual suspend fun readMetadata(fileReference: String): Result<SelectedFileMeta> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(fileReference)
                require(file.exists()) { "Selected file does not exist" }
                val mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                SelectedFileMeta(
                    displayName = file.name,
                    sizeBytes = file.length(),
                    mimeType = mimeType
                )
            }
        }
    }

    actual suspend fun readBytes(fileReference: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(fileReference)
                require(file.exists()) { "Selected file does not exist" }
                file.readBytes()
            }
        }
    }

    actual suspend fun saveIncomingFile(fileName: String, data: ByteArray): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(defaultSaveDirectory())
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val output = uniqueDestination(dir, fileName)
                output.outputStream().use { stream ->
                    stream.write(data)
                    stream.flush()
                }
                output.absolutePath
            }
        }
    }

    actual fun defaultSaveDirectory(): String = File(System.getProperty("user.home"), "LanBridge").absolutePath

    actual fun openFile(path: String): Result<Unit> {
        return runCatching {
            val file = File(path)
            require(file.exists()) { "File does not exist" }
            Desktop.getDesktop().open(file)
        }
    }

    actual fun openFolder(path: String): Result<Unit> {
        return runCatching {
            val folder = File(path)
            require(folder.exists()) { "Folder does not exist" }
            Desktop.getDesktop().open(folder)
        }
    }

    private fun uniqueDestination(dir: File, name: String): File {
        val base = File(dir, name)
        if (!base.exists()) {
            return base
        }

        val dotIndex = name.lastIndexOf('.')
        val stem = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        while (true) {
            val candidate = File(dir, "$stem-$counter$ext")
            if (!candidate.exists()) {
                return candidate
            }
            counter += 1
        }
    }
}
