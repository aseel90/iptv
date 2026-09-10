package com.selyro.tv.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.selyro.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val percent: Int) : UpdateStatus
    data class Ready(val info: UpdateInfo, val file: File) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

object UpdateManager {
    const val CHANNEL_URL = "https://raw.githubusercontent.com/aseel90/iptv/qa/full-v1-ci/update/latest.json"

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(CHANNEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "Selyro-TV/${BuildConfig.VERSION_NAME}")
            try {
                if (conn.responseCode !in 200..299) error("Update server returned ${conn.responseCode}")
                val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val info = UpdateInfo(
                    versionCode = obj.getInt("versionCode"),
                    versionName = obj.getString("versionName"),
                    apkUrl = obj.getString("apkUrl"),
                    sha256 = obj.getString("sha256").lowercase(),
                    notes = obj.optString("notes", "")
                )
                if (info.versionCode > BuildConfig.VERSION_CODE) UpdateStatus.Available(info) else UpdateStatus.UpToDate
            } finally {
                conn.disconnect()
            }
        }.getOrElse { UpdateStatus.Error(it.message ?: "Unable to check for updates") }
    }

    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(context.getExternalFilesDir("updates"), "Selyro-TV-update.apk")
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()

            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("Selyro TV ${info.versionName}")
                .setDescription("Downloading update")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(target))

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(request)
            var finished = false
            while (!finished) {
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (!c.moveToFirst()) error("Update download disappeared")
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) onProgress(((done * 100L) / total).toInt().coerceIn(0, 100))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> finished = true
                        DownloadManager.STATUS_FAILED -> error("Update download failed")
                    }
                }
                if (!finished) delay(750)
            }
            if (!target.exists() || target.length() == 0L) error("Downloaded APK is empty")
            val actual = sha256(target)
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                target.delete()
                error("Update verification failed")
            }
            target
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
