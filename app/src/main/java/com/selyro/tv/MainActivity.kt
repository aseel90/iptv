package com.selyro.tv

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.selyro.tv.ui.SelyroApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        CrashReporter.markStage(this, "activity:onCreate:ready")

        val previousIssue = CrashReporter.lastIssue(this)
        if (previousIssue != null) {
            Log.w("SelyroStartup", "showing startup recovery ${previousIssue.reportId}")
            showRecovery(previousIssue)
            return
        }

        startComposeUi()
    }

    private fun startComposeUi() {
        CrashReporter.markStage(this, "activity:setContent:before")
        try {
            setContent {
                var markedHealthy by remember { mutableStateOf(false) }
                SideEffect {
                    if (!markedHealthy) {
                        markedHealthy = true
                        CrashReporter.markHealthy(applicationContext)
                    }
                }
                SelyroApp()
            }
            CrashReporter.markStage(this, "activity:setContent:returned")
        } catch (throwable: Throwable) {
            CrashReporter.record(this, throwable, "activity:setContent:synchronous")
            val issue = CrashReporter.lastIssue(this)
            if (issue != null) showRecovery(issue)
        }
    }

    private fun showRecovery(issue: StartupIssue) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 11, 16))
            setPadding(dp(42), dp(30), dp(42), dp(30))
        }

        val title = TextView(this).apply {
            text = "SELYRO TV — STARTUP RECOVERY"
            textSize = 28f
            setTextColor(Color.rgb(99, 216, 198))
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val subtitle = TextView(this).apply {
            text = "The previous launch failed. This screen is native Android and does not use Compose.\n\nReport ID: ${issue.reportId}\nStage: ${issue.stage}\nDevice: ${issue.device}"
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(18))
        }
        root.addView(subtitle)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }

        val retry = Button(this).apply {
            text = "Retry Selyro"
            isFocusable = true
            setOnClickListener {
                CrashReporter.clearIssueForRetry(this@MainActivity)
                startComposeUi()
            }
        }
        actions.addView(retry, LinearLayout.LayoutParams(dp(230), dp(58)).apply { marginEnd = dp(14) })

        val reset = Button(this).apply {
            text = "Reset local data + retry"
            isFocusable = true
            setOnClickListener {
                getSharedPreferences("selyro", MODE_PRIVATE).edit().clear().commit()
                CrashReporter.clearIssueForRetry(this@MainActivity)
                startComposeUi()
            }
        }
        actions.addView(reset, LinearLayout.LayoutParams(dp(290), dp(58)))
        root.addView(actions)

        val reportTitle = TextView(this).apply {
            text = "Crash report / diagnostic details"
            textSize = 18f
            setTextColor(Color.rgb(170, 181, 193))
            setPadding(0, dp(22), 0, dp(8))
        }
        root.addView(reportTitle)

        val report = TextView(this).apply {
            text = issue.details.take(8_000)
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setTextIsSelectable(true)
        }
        root.addView(report)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
        retry.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i("SelyroStartup", "activity:onNewIntent:reused")
    }

    override fun onResume() {
        super.onResume()
        Log.i("SelyroStartup", "activity:onResume")
    }

    override fun onStop() {
        super.onStop()
        Log.i("SelyroStartup", "activity:onStop")
    }
}
