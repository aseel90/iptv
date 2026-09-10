package com.selyro.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.selyro.tv.model.Channel
import com.selyro.tv.model.Episode

internal val Bg = Color(0xFF05080D)
internal val Rail = Color(0xFF090E15)
internal val Surface = Color(0xFF0E1620)
internal val SurfaceRaised = Color(0xFF141F2B)
internal val SurfaceFocus = Color(0xFF203346)
internal val Accent = Color(0xFF6BE4D2)
internal val AccentSoft = Color(0xFF96F0E2)
internal val TextPrimary = Color(0xFFF5F8FB)
internal val Muted = Color(0xFF9AA9B8)
internal val Subtle = Color(0xFF667788)
internal val Danger = Color(0xFFFF8B8B)
internal val Border = Color(0xFF223140)

internal fun surfacePanelModifier(radius: Dp = 20.dp): Modifier = Modifier
    .clip(RoundedCornerShape(radius))
    .background(Surface)
    .border(1.dp, Border, RoundedCornerShape(radius))

@Composable
internal fun BrandLockup(compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(if (compact) 35.dp else 50.dp)
        Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
        Column {
            Text(
                "SELYRO",
                color = TextPrimary,
                fontSize = if (compact) 19.sp else 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            Text(
                "TV",
                color = Accent,
                fontSize = if (compact) 9.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
internal fun BrandMark(markSize: Dp) {
    Canvas(Modifier.size(markSize)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF59D8C5), Color(0xFF7CEAD9))),
            topLeft = Offset(w * 0.04f, h * 0.08f),
            size = Size(w * 0.92f, h * 0.84f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.24f, w * 0.24f)
        )
        val portal = Path().apply {
            moveTo(w * 0.27f, h * 0.23f)
            lineTo(w * 0.44f, h * 0.23f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.77f)
            lineTo(w * 0.27f, h * 0.77f)
            lineTo(w * 0.52f, h * 0.50f)
            close()
        }
        drawPath(portal, color = Color(0xFF071118))
        val play = Path().apply {
            moveTo(w * 0.50f, h * 0.33f)
            lineTo(w * 0.74f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.67f)
            close()
        }
        drawPath(play, color = Color.White)
    }
}

@Composable
internal fun Heading(title: String, subtitle: String) {
    Text(title, color = TextPrimary, fontSize = 31.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(17.dp))
}

@Composable
internal fun DashboardCard(title: String, value: String, caption: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "dashboardScale")
    Column(
        Modifier.width(205.dp).height(126.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) SurfaceFocus else Surface)
            .border(1.dp, if (focused) Accent.copy(alpha = 0.65f) else Border, RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(17.dp)
    ) {
        Text(title, color = if (focused) TextPrimary else Muted, fontSize = 14.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(caption, color = Subtle, fontSize = 11.sp)
    }
}

@Composable
internal fun TvNavItem(label: String, short: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val inset by animateDpAsState(if (focused) 4.dp else 0.dp, label = "navInset")
    Row(
        Modifier.fillMaxWidth().padding(start = inset)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) SurfaceFocus else if (selected) SurfaceRaised else Color.Transparent)
            .border(1.dp, if (focused) Accent.copy(alpha = 0.42f) else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(if (selected || focused) Accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center
        ) {
            Text(short, color = if (selected || focused) AccentSoft else Subtle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (focused || selected) TextPrimary else Muted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
internal fun TvFilterItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (focused) SurfaceFocus else if (selected) SurfaceRaised else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent))
        Spacer(Modifier.width(9.dp))
        Text(label, color = if (focused || selected) TextPrimary else Muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
internal fun ChannelRow(channel: Channel, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.018f else 1f, label = "channelScale")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(13.dp))
            .background(if (focused) SurfaceFocus else Surface)
            .border(1.dp, if (focused) Accent.copy(alpha = 0.45f) else Border.copy(alpha = 0.5f), RoundedCornerShape(13.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = channel.logo,
            contentDescription = null,
            modifier = Modifier.size(39.dp).clip(RoundedCornerShape(9.dp)).background(SurfaceRaised).padding(4.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.group, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        Text("▶", color = if (focused) Accent else Subtle, fontSize = 11.sp)
    }
}

@Composable
internal fun MediaPosterCard(
    title: String,
    image: String?,
    subtitle: String,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "posterScale")
    Column(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box(
            Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(14.dp))
                .background(SurfaceRaised)
                .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(14.dp))
        ) {
            AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (focused) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(31.dp).clip(CircleShape).background(Accent),
                    contentAlignment = Alignment.Center
                ) { Text("▶", color = Color(0xFF061014), fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title,
            color = if (focused) TextPrimary else Color(0xFFDDE4EA),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(subtitle, color = Subtle, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MediaListRow(title: String, image: String?, subtitle: String, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (focused) SurfaceFocus else Surface)
            .border(1.dp, if (focused) Accent.copy(alpha = 0.45f) else Border, RoundedCornerShape(13.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick).focusable().padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = image,
            contentDescription = null,
            modifier = Modifier.width(50.dp).height(70.dp).clip(RoundedCornerShape(9.dp)).background(SurfaceRaised),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("▶", color = if (focused) Accent else Subtle, fontSize = 11.sp)
    }
}

@Composable
internal fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (focused) SurfaceFocus else SurfaceRaised)
            .border(1.dp, if (focused) Accent.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable().padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Text(episode.episode.toString(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(episode.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "S${episode.season} E${episode.episode}${episode.duration?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}",
                color = Muted,
                fontSize = 10.sp
            )
        }
        Text("▶", color = if (focused) Accent else Subtle, fontSize = 11.sp)
    }
}

@Composable
internal fun ModernListRow(title: String, subtitle: String, badge: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (focused) SurfaceFocus else Surface)
            .border(1.dp, if (focused) Accent.copy(alpha = 0.45f) else Border, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text("▶", color = Accent, fontSize = 11.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        if (badge.isNotBlank()) MetaChip(badge)
    }
}

@Composable
internal fun TvButton(label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        !enabled -> Color(0xFF111820)
        focused -> Accent
        selected -> Color(0xFF214D49)
        else -> SurfaceRaised
    }
    val borderColor = when {
        focused -> Accent
        selected -> Accent.copy(alpha = 0.32f)
        else -> Border
    }
    Box(
        Modifier.clip(RoundedCornerShape(11.dp)).background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(11.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled)
            .padding(horizontal = 17.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (focused) Color(0xFF071014) else if (enabled) TextPrimary else Subtle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun SmallPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (focused) Accent else if (selected) Color(0xFF214D49) else SurfaceRaised)
            .border(1.dp, if (focused || selected) Accent.copy(alpha = 0.55f) else Border, RoundedCornerShape(9.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (focused) Color(0xFF071014) else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun TvInput(
    label: String,
    value: String,
    password: Boolean = false,
    compact: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.then(if (compact) Modifier.width(430.dp) else Modifier.fillMaxWidth())) {
        Text(label, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().height(if (compact) 43.dp else 48.dp)
                .clip(RoundedCornerShape(11.dp)).background(Surface)
                .border(if (focused) 2.dp else 1.dp, if (focused) Accent else Border, RoundedCornerShape(11.dp))
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 13.dp, vertical = if (compact) 11.dp else 13.dp)
        )
    }
}

@Composable
internal fun MetaChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text, color = Muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
internal fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().then(surfacePanelModifier(18.dp)).padding(20.dp)) {
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
internal fun LoadingBox(text: String) {
    Box(
        Modifier.fillMaxWidth().height(170.dp).then(surfacePanelModifier(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Accent))
            Spacer(Modifier.width(10.dp))
            Text(text, color = TextPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
internal fun EmptyPrompt(title: String, message: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandMark(45.dp)
        Spacer(Modifier.height(13.dp))
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun EmptyLarge(title: String, message: String) {
    Box(
        Modifier.fillMaxWidth().height(260.dp).then(surfacePanelModifier(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(50.dp)
            Spacer(Modifier.height(13.dp))
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(message, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF341C22))
            .border(1.dp, Color(0xFF6B3039), RoundedCornerShape(13.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 2)
        TvButton("Dismiss", onClick = onDismiss)
    }
}
