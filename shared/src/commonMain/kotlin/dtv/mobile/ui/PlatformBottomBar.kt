package dtv.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dtv.mobile.model.Platform
import dtv.mobile.state.Screen

@Composable
fun PlatformBottomBar(
  selectedScreen: Screen,
  selectedPlatform: Platform,
  onHomeClick: () -> Unit,
  onPlatformClick: (Platform) -> Unit,
  switchingLoading: Boolean = false,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.92f else 0.98f)
  val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
  val inactiveIcon = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
  val barShape = RoundedCornerShape(0.dp)

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(barShape),
    shape = barShape,
    color = containerColor,
    tonalElevation = 0.dp,
    shadowElevation = if (isDark) 0.dp else 18.dp,
    border = null,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(vertical = 3.dp),
      horizontalArrangement = Arrangement.SpaceAround,
    ) {
      DockItem(
        selected = selectedScreen == Screen.Home,
        label = "首页",
        activeBackground = activeBg,
        onClick = onHomeClick.takeUnless { switchingLoading } ?: {},
      ) {
        Icon(
          imageVector = Icons.Default.Home,
          contentDescription = "首页",
          tint = if (selectedScreen == Screen.Home) MaterialTheme.colorScheme.primary else inactiveIcon,
        )
      }

      Platform.entries.filter { it != Platform.Custom }.forEach { platform ->
        val isSelected = selectedScreen == Screen.Platform && platform == selectedPlatform
        DockItem(
          selected = isSelected,
          label = platform.title,
          activeBackground = activeBg,
          onClick = { if (!switchingLoading) onPlatformClick(platform) },
        ) {
          val icon = when (platform) {
            Platform.Douyu -> Icons.Default.WaterDrop
            Platform.Huya -> Icons.Default.Pets
            Platform.Douyin -> Icons.Default.MusicNote
            Platform.Bilibili -> Icons.Default.LiveTv
            Platform.Custom -> Icons.Default.Home
          }
          Icon(
            imageVector = icon,
            contentDescription = platform.title,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else inactiveIcon,
          )
        }
      }
    }
  }
}

@Composable
private fun RowScope.DockItem(
  selected: Boolean,
  label: String,
  activeBackground: Color,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
) {
  val scale = animateFloatAsState(targetValue = if (selected) 1.08f else 1.0f, label = "dockScale").value
  val bgAlpha = animateFloatAsState(targetValue = if (selected) 1.0f else 0.0f, label = "dockBgAlpha").value

  Column(
    modifier = Modifier
      .weight(1f)
      .clickable { onClick() }
      .defaultMinSize(minHeight = 58.dp)
      .padding(vertical = 3.dp),
    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = activeBackground.copy(alpha = activeBackground.alpha * bgAlpha),
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Box(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
          icon()
        }
      }
    }

    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
