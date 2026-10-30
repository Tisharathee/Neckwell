package com.humblecoders.neckwell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.humblecoders.neckwell.ui.theme.AlertCoral
import com.humblecoders.neckwell.ui.theme.NeckWellAccent
import com.humblecoders.neckwell.ui.theme.NeckWellSurface
import com.humblecoders.neckwell.ui.theme.NeckWellTextPrimary
import com.humblecoders.neckwell.ui.theme.NeckWellTextSecondary

/**
 * Composable that displays real-time connection status for the ESP32-C6 device.
 * Displays a colored dot and label:
 *  - Green "Connected"
 *  - Red "Disconnected"
 *  - Gray "Checking…"
 */
@Composable
fun ConnectionStatus(
    modifier: Modifier = Modifier,
    viewModel: DeviceStatusViewModel = viewModel()
) {
    val connState by viewModel.connectionState.collectAsStateWithLifecycle()

    val (dotColor, label) = when (connState) {
        DeviceConn.CONNECTED -> NeckWellAccent to "Connected"
        DeviceConn.DISCONNECTED -> AlertCoral to "Disconnected"
        DeviceConn.CHECKING -> NeckWellTextSecondary to "Checking…"
    }

    Row(
        modifier = modifier
            .background(NeckWellSurface, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = label,
            color = NeckWellTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

