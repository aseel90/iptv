package com.selyro.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val Background = Color(0xFF080B10)
private val Panel = Color(0xFF111720)
private val PanelFocused = Color(0xFF1C2A35)
private val Accent = Color(0xFF6ED7C7)
private val Muted = Color(0xFFA3AFBC)

private enum class Section(val label: String) {
    Home("Home"),
    Live("Live TV"),
    Movies("Movies"),
    Series("Series"),
    Favorites("Favorites"),
    Settings("Settings")
}

@Composable
fun SelyroApp() {
    MaterialTheme {
        var section by remember { mutableStateOf(Section.Home) }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            NavigationRail(
                selected = section,
                onSelect = { section = it }
            )
            Content(section)
        }
    }
}

@Composable
private fun NavigationRail(selected: Section, onSelect: (Section) -> Unit) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFF0D1219))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "SELYRO",
            color = Accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = "TV", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))

        Section.entries.forEach { item ->
            FocusItem(
                label = item.label,
                selected = item == selected,
                onClick = { onSelect(item) }
            )
        }
    }
}

@Composable
private fun FocusItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> PanelFocused
        selected -> Color(0xFF17212B)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(
            text = label,
            color = if (selected || focused) Color.White else Muted,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun Content(section: Section) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp, vertical = 36.dp)
    ) {
        Text(
            text = section.label,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (section) {
                Section.Home -> "Fast IPTV playback, designed for Android TV."
                Section.Live -> "Your channel groups and EPG will appear here."
                Section.Movies -> "Movies from your connected provider."
                Section.Series -> "Series, seasons and episodes."
                Section.Favorites -> "Your saved channels and titles."
                Section.Settings -> "Playback, buffering, language and provider settings."
            },
            color = Muted,
            fontSize = 17.sp
        )
        Spacer(Modifier.height(32.dp))

        if (section == Section.Home) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                InfoCard("Live TV", "Quick channel switching")
                InfoCard("Stable mode", "Adaptive buffer for weak networks")
                InfoCard("EPG", "Program guide ready")
            }
        } else {
            EmptyState(section)
        }
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(260.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) PanelFocused else Panel)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun EmptyState(section: Section) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Panel),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${section.label} module is ready for implementation",
            color = Muted,
            fontSize = 19.sp
        )
    }
}
