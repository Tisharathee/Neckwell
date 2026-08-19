package com.humblecoders.neckwell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
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
import com.humblecoders.neckwell.ui.theme.AccentTealDark
import com.humblecoders.neckwell.ui.theme.BackgroundGray
import com.humblecoders.neckwell.ui.theme.InfoBlue
import com.humblecoders.neckwell.ui.theme.Purple
import com.humblecoders.neckwell.ui.theme.TextDark
import com.humblecoders.neckwell.ui.theme.TextGray
import com.humblecoders.neckwell.ui.theme.WarningAmber

private data class PostureTip(val icon: ImageVector, val title: String, val description: String, val color: Color)

private val postureTips = listOf(
    PostureTip(Icons.Outlined.EventSeat, "Sit correctly", "Keep your back supported, shoulders relaxed, and both feet flat on the floor.", AccentTealDark),
    PostureTip(Icons.Outlined.DesktopWindows, "Position your monitor", "Place your screen at eye level and roughly an arm's length from your face.", InfoBlue),
    PostureTip(Icons.Outlined.Timer, "Take regular breaks", "Stand, stretch, or walk for a few minutes every 30 minutes.", WarningAmber),
    PostureTip(Icons.Outlined.SelfImprovement, "Release neck tension", "Slowly tilt your head side to side without forcing the movement.", Purple),
    PostureTip(Icons.Outlined.Visibility, "Protect your eye line", "Keep the top third of your monitor close to eye level to avoid looking down.", InfoBlue),
    PostureTip(Icons.Outlined.FitnessCenter, "Strengthen your core", "A stronger core makes neutral sitting posture easier to maintain.", AccentTealDark),
    PostureTip(Icons.Outlined.Bedtime, "Sleep in alignment", "Use a supportive pillow that keeps your neck in line with your spine.", Purple),
    PostureTip(Icons.Outlined.Smartphone, "Lift your phone", "Bring your phone toward eye level instead of bending your neck toward it.", WarningAmber)
)

@Composable
fun TipsScreen() {
    Column(Modifier.fillMaxSize().background(BackgroundGray)) {
        NeckWellHeader("Posture Tips", "Small habits, lasting relief", compact = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NeckWellCard {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(Icons.Outlined.Lightbulb, AccentTealDark, size = 50)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Build a posture-friendly day", color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Choose one tip to practice consistently this week.", color = TextGray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            postureTips.forEachIndexed { index, tip ->
                TipCard(tip, index + 1)
            }
        }
    }
}

@Composable
private fun TipCard(tip: PostureTip, number: Int) {
    NeckWellCard {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconTile(tip.icon, tip.color, size = 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("TIP ${number.toString().padStart(2, '0')}", color = tip.color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(tip.title, color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(tip.description, color = TextGray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
