package com.homeport.app.ui.screens.activity

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.homeport.app.data.mock.MockDataRepository
import com.homeport.app.domain.model.*
import com.homeport.app.ui.components.GlassCard
import com.homeport.app.ui.components.SectionHeader
import com.homeport.app.ui.theme.*

@Composable
fun ActivityScreen(onBack: () -> Unit) {
    val repo = remember { MockDataRepository() }

    Scaffold(
        containerColor = Background,
        topBar = {
            Column {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = White90, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "HISTORY",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold),
                            color = White40
                        )
                        Text(
                            "Activity Log",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = White100
                        )
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 8.dp,
                bottom = 32.dp, start = 20.dp, end = 20.dp
            )
        ) {
            item {
                SectionHeader("Today", modifier = Modifier)
                Spacer(Modifier.height(10.dp))
            }

            items(repo.activityEvents) { event ->
                ActivityEventRow(event)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ActivityEventRow(event: ActivityEvent) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getEventColor(event.type).copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(getEventIcon(event.type), null,
                    tint = getEventColor(event.type), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyMedium,
                    color = White100, fontWeight = FontWeight.Medium)
                Text(event.subtitle, style = MaterialTheme.typography.bodySmall, color = White60)
            }
            Text(event.formattedTime, style = MaterialTheme.typography.labelSmall, color = White40)
        }
    }
}

private fun getEventColor(type: ActivityEventType) = when (type) {
    ActivityEventType.TRANSFER_COMPLETE   -> StatusOnline
    ActivityEventType.TRANSFER_FAILED     -> StatusError
    ActivityEventType.DEVICE_PAIRED       -> VoltGreen
    ActivityEventType.DEVICE_CONNECTED    -> StatusOnline
    ActivityEventType.DEVICE_DISCONNECTED -> StatusOffline
    ActivityEventType.DEVICE_REVOKED      -> StatusError
    ActivityEventType.FILE_CREATED        -> VoltGreen
    ActivityEventType.FILE_RENAMED        -> VoltGreen
    ActivityEventType.FILE_DELETED        -> StatusError
    ActivityEventType.PERMISSION_CHANGED  -> StatusWarning
    ActivityEventType.JOB_COMPLETED       -> StatusOnline
    ActivityEventType.JOB_FAILED          -> StatusError
    ActivityEventType.SECURITY_ALERT      -> StatusError
}

private fun getEventIcon(type: ActivityEventType): ImageVector = when (type) {
    ActivityEventType.TRANSFER_COMPLETE   -> Icons.Outlined.CheckCircle
    ActivityEventType.TRANSFER_FAILED     -> Icons.Outlined.Error
    ActivityEventType.DEVICE_PAIRED       -> Icons.Outlined.Link
    ActivityEventType.DEVICE_CONNECTED    -> Icons.Outlined.Wifi
    ActivityEventType.DEVICE_DISCONNECTED -> Icons.Outlined.WifiOff
    ActivityEventType.DEVICE_REVOKED      -> Icons.Outlined.RemoveCircleOutline
    ActivityEventType.FILE_CREATED        -> Icons.Outlined.AddCircleOutline
    ActivityEventType.FILE_RENAMED        -> Icons.Outlined.Edit
    ActivityEventType.FILE_DELETED        -> Icons.Outlined.Delete
    ActivityEventType.PERMISSION_CHANGED  -> Icons.Outlined.Shield
    ActivityEventType.JOB_COMPLETED       -> Icons.Outlined.Task
    ActivityEventType.JOB_FAILED          -> Icons.Outlined.Cancel
    ActivityEventType.SECURITY_ALERT      -> Icons.Outlined.Warning
}

private val ActivityEvent.formattedTime: String get() = formatRelativeTime(timestamp)
