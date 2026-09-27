package com.devfahim00.lychee.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devfahim00.lychee.ui.components.GlassBackground
import com.devfahim00.lychee.ui.theme.LycheePink
import com.devfahim00.lychee.ui.theme.TextPrimary
import com.devfahim00.lychee.ui.theme.TextTertiary

private enum class Tab(val label: String) {
    HOME("Home"),
    DOWNLOADS("Downloads"),
    SETTINGS("Settings")
}

@Composable
fun MainScreen(sharedUrl: String?) {
    val vm: AppViewModel = viewModel()
    var tab by remember { mutableStateOf(Tab.HOME) }

    Box(Modifier.fillMaxSize()) {
        GlassBackground(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 96.dp)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 24 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 24 })
                },
                label = "tabContent"
            ) { current ->
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        Tab.HOME -> HomeScreen(vm, sharedUrl)
                        Tab.DOWNLOADS -> DownloadsScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }

        GlassBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            selected = tab,
            onSelect = { tab = it }
        )
    }
}

@Composable
private fun GlassBottomBar(
    modifier: Modifier = Modifier,
    selected: Tab,
    onSelect: (Tab) -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xD9161126))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.30f), Color.White.copy(0.08f))
                ),
                RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Tab.entries.forEach { t ->
            val isSelected = t == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent
                    )
                    .clickable { onSelect(t) }
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                val icon: ImageVector = when (t) {
                    Tab.HOME -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
                    Tab.DOWNLOADS -> if (isSelected) Icons.Filled.Download else Icons.Outlined.Download
                    Tab.SETTINGS -> if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings
                }
                Icon(icon, contentDescription = t.label, tint = if (isSelected) LycheePink else TextTertiary)
                Text(
                    t.label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) TextPrimary else TextTertiary
                )
            }
        }
    }
}
