package com.humblecoders.neckwell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.neckwell.ui.theme.AccentTeal
import com.humblecoders.neckwell.ui.theme.AccentTealDark
import com.humblecoders.neckwell.ui.theme.BorderSoft
import com.humblecoders.neckwell.ui.theme.PrimaryBlue
import com.humblecoders.neckwell.ui.theme.PrimaryBlueDark
import com.humblecoders.neckwell.ui.theme.TextDark
import com.humblecoders.neckwell.ui.theme.TextGray

@Composable
fun NeckWellHeader(
    title: String,
    subtitle: String,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(PrimaryBlue, PrimaryBlueDark, AccentTealDark)),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val lineColor = Color.White.copy(alpha = 0.055f)
            repeat(6) { index ->
                val startY = size.height * (0.24f + index * 0.1f)
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.63f, startY),
                    end = Offset(size.width, startY - size.height * 0.18f),
                    strokeWidth = 1.2f
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = if (compact) 22.dp else 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 52.dp else 62.dp)
                    .background(Color.White, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessibilityNew,
                    contentDescription = null,
                    tint = AccentTealDark,
                    modifier = Modifier.size(if (compact) 30.dp else 38.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (compact) 26.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            action?.invoke()
        }
    }
}

@Composable
fun NeckWellCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) { content() }
}

@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = AccentTealDark,
    background: Color = tint.copy(alpha = 0.10f),
    size: Int = 46
) {
    Box(
        modifier = Modifier.size(size.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column {
        Text(title, color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextGray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
