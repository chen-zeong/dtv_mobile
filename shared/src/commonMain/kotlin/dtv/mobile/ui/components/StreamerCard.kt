package dtv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dtv.mobile.model.Streamer
import dtv.mobile.util.formatViewerCountWanIfNeeded
import dtv.mobile.util.normalizeHttpUrl

@Composable
fun StreamerCard(
  streamer: Streamer,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  followed: Boolean = false,
  onToggleFollow: (() -> Unit)? = null,
  highlightLiveBorder: Boolean = false,
) {
  val shape = RoundedCornerShape(18.dp)
  val coverRatio = 16f / 10f
  val cover = normalizeHttpUrl(streamer.coverUrl) ?: normalizeHttpUrl(streamer.avatarUrl)
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val liveBorder = highlightLiveBorder && streamer.isLive

  val cardColor = if (isDark) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface
  val borderColor = when {
    liveBorder -> MaterialTheme.colorScheme.primary
    isDark -> Color.White.copy(alpha = 0.10f)
    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
  }
  val borderWidth = if (liveBorder) 2.dp else 1.dp

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onClick),
    shape = shape,
    color = cardColor,
    tonalElevation = 0.dp,
    shadowElevation = if (isDark) 0.dp else 2.dp,
    border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(coverRatio)
          .background(MaterialTheme.colorScheme.secondary),
      ) {
        if (cover != null) {
          NetworkImage(
            url = cover,
            contentDescription = streamer.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(coverRatio),
          )
        } else {
          Text(
            text = streamer.name.take(1),
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleLarge,
          )
        }

        Box(
          modifier = Modifier
            .matchParentSize()
            .background(
              androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f)),
              ),
            ),
        )

        if (streamer.viewerText.isNotBlank()) {
          Surface(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(10.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.40f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = formatViewerCountWanIfNeeded(streamer.viewerText),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = streamer.title,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black),
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val avatar = normalizeHttpUrl(streamer.avatarUrl)
          Box(
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .background(if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
          ) {
            if (avatar != null) {
              NetworkImage(url = avatar, contentDescription = streamer.name, modifier = Modifier.matchParentSize())
            } else {
              Text(streamer.name.take(1), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
            }
          }

          Text(
            text = streamer.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
        }
      }

      Spacer(modifier = Modifier.size(2.dp))
    }
  }
}
