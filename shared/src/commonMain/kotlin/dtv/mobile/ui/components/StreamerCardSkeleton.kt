package dtv.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun StreamerCardSkeleton(
  modifier: Modifier = Modifier,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val shape = RoundedCornerShape(32.dp)
  val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
  val base = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
  val highlight = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)

  val transition = rememberInfiniteTransition(label = "streamerCardSkeleton")
  val x = transition.animateFloat(
    initialValue = 0f,
    targetValue = 900f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 950),
      repeatMode = RepeatMode.Restart,
    ),
    label = "x",
  ).value

  val shimmer = Brush.linearGradient(
    colors = listOf(base, highlight, base),
    start = Offset(x = x - 240f, y = 0f),
    end = Offset(x = x, y = 0f),
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape),
    shape = shape,
    color = if (isDark) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
    tonalElevation = 0.dp,
    shadowElevation = if (isDark) 0.dp else 2.dp,
    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 10f)
          .background(shimmer),
      ) {
        Box(
          modifier = Modifier
            .padding(12.dp)
            .size(width = 56.dp, height = 22.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(shimmer),
        )
      }

      Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(shimmer),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(CircleShape)
              .background(shimmer),
          )
          Box(
            modifier = Modifier
              .fillMaxWidth(0.55f)
              .height(12.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(shimmer),
          )
        }
      }

      Spacer(modifier = Modifier.size(2.dp))
    }
  }
}

