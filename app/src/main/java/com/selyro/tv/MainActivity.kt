package com.selyro.tv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.selyro.tv.ui.SelyroApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("SelyroStartup", "activity:onCreate:start")
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Log.i("SelyroStartup", "activity:setContent:before")
        setContent {
            SideEffect { Log.i("SelyroStartup", "compose:first-composition:ok") }
            SelyroApp()
        }
        Log.i("SelyroStartup", "activity:setContent:after")
    }

    override fun onResume() {
        super.onResume()
        Log.i("SelyroStartup", "activity:onResume")
    }
}
