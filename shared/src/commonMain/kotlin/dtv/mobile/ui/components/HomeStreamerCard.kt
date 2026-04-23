package dtv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dtv.mobile.model.Streamer
import dtv.mobile.util.formatViewerCountWanIfNeeded
import dtv.mobile.util.normalizeHttpUrl

@Composable
fun HomeStreamerCard(
  streamer: Streamer,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  followed: Boolean = false,
  onToggleFollow: (() -> Unit)? = null,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val bg = if (isDark) Color(0xFF121212) else MaterialTheme.colorScheme.surface
  val border = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
  val accent = MaterialTheme.colorScheme.primary
  val cover = normalizeHttpUrl(streamer.coverUrl) ?: normalizeHttpUrl(streamer.avatarUrl)
  val avatar = normalizeHttpUrl(streamer.avatarUrl)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      val mediaShape = RoundedCornerShape(32.dp)
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(4f / 3f)
          .clip(mediaShape),
        shape = mediaShape,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          if (cover != null) {
            NetworkImage(url = cover, contentDescription = streamer.title, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
          } else {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
              Text(text = streamer.name.take(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
          }
          Box(
            modifier = Modifier
              .matchParentSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                ),
              ),
          )
        }
      }

      Surface(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(start = 14.dp, top = 14.dp),
        shape = RoundedCornerShape(8.dp),
        color = accent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
      ) {
        Text(
          text = streamer.platform.title,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
          color = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (streamer.viewerText.isNotBlank()) {
        Surface(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = 12.dp, top = 12.dp),
          shape = RoundedCornerShape(999.dp),
          color = Color.Black.copy(alpha = 0.40f),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
        ) {
          Text(
            text = formatViewerCountWanIfNeeded(streamer.viewerText),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .padding(start = 14.dp)
          .offset(y = (-20).dp)
          .size(44.dp),
      ) {
        Surface(
          modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(18.dp)),
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.background,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.10f else 0.05f)),
        ) {
          if (avatar != null) {
            NetworkImage(url = avatar, contentDescription = streamer.name, modifier = Modifier.matchParentSize())
          } else {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
              Text(text = streamer.name.take(1), style = MaterialTheme.typography.titleSmall)
            }
          }
        }

        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 1.dp, y = 1.dp)
            .size(12.dp)
            .clip(CircleShape)
            .background(if (streamer.isLive) accent else Color(0xFF9CA3AF))
            .border(width = 2.dp, color = MaterialTheme.colorScheme.background, shape = CircleShape),
        )
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 72.dp, top = 0.dp)
          .offset(y = (-2).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "@${streamer.name}",
            style = MaterialTheme.typography.bodySmall.copy(
              fontWeight = FontWeight.Black,
              fontStyle = FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = streamer.title,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = if (isDark) Color(0xFF6B7280) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
