package com.humblecoders.neckwell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.neckwell.ui.theme.NeckWellAccent
import com.humblecoders.neckwell.ui.theme.NeckWellAccentDark
import com.humblecoders.neckwell.ui.theme.NeckWellSurface
import com.humblecoders.neckwell.ui.theme.NeckWellSurfaceVariant
import com.humblecoders.neckwell.ui.theme.NeckWellTextPrimary
import com.humblecoders.neckwell.ui.theme.NeckWellTextSecondary

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
            .background(NeckWellSurfaceVariant)
            .padding(horizontal = 22.dp, vertical = if (compact) 18.dp else 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = NeckWellTextPrimary,
                    fontSize = if (compact) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = NeckWellTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NeckWellSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { content() }
}

@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = NeckWellAccent,
    background: Color = tint.copy(alpha = 0.12f),
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
        Text(title, color = NeckWellTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = NeckWellTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
