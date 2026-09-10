package com.selyro.tv

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

data class StartupIssue(
    val reportId: String,
    val stage: String,
    val device: String,
    val details: String
)

object CrashReporter {
    private const val PREFS = "selyro_startup_diagnostics"
    private const val KEY_PENDING = "startup_pending"
    private const val KEY_STAGE = "startup_stage"
    private const val KEY_PREVIOUS_INCOMPLETE = "previous_incomplete"
    private const val KEY_PREVIOUS_STAGE = "previous_stage"
    private const val KEY_CRASH = "last_crash"
    private const val KEY_CRASH_STAGE = "last_crash_stage"
    private const val KEY_REPORT_ID = "last_report_id"
    private const val KEY_DEVICE = "last_device"
    private const val KEY_TIME = "last_crash_time"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val wasPending = prefs.getBoolean(KEY_PENDING, false)
            val previousStage = prefs.getString(KEY_STAGE, "unknown").orEmpty()
            prefs.edit()
                .apply {
                    if (wasPending) {
                        putBoolean(KEY_PREVIOUS_INCOMPLETE, true)
                        putString(KEY_PREVIOUS_STAGE, previousStage)
                    }
                    putBoolean(KEY_PENDING, true)
                    putString(KEY_STAGE, "application:onCreate")
                }
                .commit()

            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    val stage = prefs.getString(KEY_STAGE, "uncaught").orEmpty()
                    record(app, throwable, "$stage | thread=${thread.name}")
                }
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    exitProcess(10)
                }
            }
            installed = true
            Log.i("SelyroStartup", "crash-reporter:installed previousIncomplete=$wasPending")
        }
    }

    fun markStage(context: Context, stage: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STAGE, stage).commit()
        Log.i("SelyroStartup", stage)
    }

    fun markHealthy(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, false)
            .putBoolean(KEY_PREVIOUS_INCOMPLETE, false)
            .remove(KEY_PREVIOUS_STAGE)
            .remove(KEY_CRASH)
            .remove(KEY_CRASH_STAGE)
            .remove(KEY_REPORT_ID)
            .remove(KEY_DEVICE)
            .remove(KEY_TIME)
            .putString(KEY_STAGE, "compose:first-composition:healthy")
            .commit()
        Log.i("SelyroStartup", "compose:first-composition:healthy")
    }

    fun record(context: Context, throwable: Throwable, stage: String) {
        val app = context.applicationContext
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val trace = writer.toString().take(14_000)
        val fingerprint = (throwable.javaClass.name + ":" + (throwable.message ?: "") + ":" + trace.take(1200)).hashCode()
        val reportId = "SEL-${BuildConfig.VERSION_CODE}-${Integer.toHexString(fingerprint)}"
        val device = deviceSummary()
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_CRASH, trace)
            .putString(KEY_CRASH_STAGE, stage)
            .putString(KEY_REPORT_ID, reportId)
            .putString(KEY_DEVICE, device)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .commit()
        Log.e("SelyroStartup", "startup crash $reportId at $stage", throwable)
    }

    fun lastIssue(context: Context): StartupIssue? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val crash = prefs.getString(KEY_CRASH, null)
        if (!crash.isNullOrBlank()) {
            return StartupIssue(
                reportId = prefs.getString(KEY_REPORT_ID, "SEL-UNKNOWN").orEmpty(),
                stage = prefs.getString(KEY_CRASH_STAGE, "unknown").orEmpty(),
                device = prefs.getString(KEY_DEVICE, deviceSummary()).orEmpty(),
                details = crash
            )
        }
        if (prefs.getBoolean(KEY_PREVIOUS_INCOMPLETE, false)) {
            val stage = prefs.getString(KEY_PREVIOUS_STAGE, "unknown").orEmpty()
            val details = "The previous Selyro process ended before startup was marked healthy. No Java/Kotlin uncaught exception was captured. This can indicate a native crash, process kill, resource/theme failure, or device-level termination. Previous startup stage: $stage"
            val reportId = "SEL-${BuildConfig.VERSION_CODE}-INCOMPLETE-${Integer.toHexString(details.hashCode())}"
            return StartupIssue(reportId, stage, deviceSummary(), details)
        }
        return null
    }

    fun clearIssueForRetry(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CRASH)
            .remove(KEY_CRASH_STAGE)
            .remove(KEY_REPORT_ID)
            .remove(KEY_DEVICE)
            .remove(KEY_TIME)
            .putBoolean(KEY_PREVIOUS_INCOMPLETE, false)
            .remove(KEY_PREVIOUS_STAGE)
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_STAGE, "recovery:retry")
            .commit()
    }

    private fun deviceSummary(): String = buildString {
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append(" | Android ").append(Build.VERSION.RELEASE)
        append(" | API ").append(Build.VERSION.SDK_INT)
        append(" | ABI ").append(Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        append(" | Selyro ").append(BuildConfig.VERSION_NAME)
    }
}
