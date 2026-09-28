package com.homeport.app.ui.screens.notifications

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.homeport.app.ui.components.AtmosphericBackground
import com.homeport.app.ui.components.GlassCard
import com.homeport.app.ui.components.SpotlightFilterBar
import com.homeport.app.ui.theme.*

enum class NotificationCategory(val label: String) {
    ALL("All"),
    TRANSFERS("Transfers"),
    DEVICES("Devices"),
    SECURITY("Security")
}

data class NotificationItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: String,
    val category: NotificationCategory,
    val icon: ImageVector,
    val iconTint: Color,
    val isUnread: Boolean = true,
    val actionLabel: String? = null
)

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf(NotificationCategory.ALL) }
    var notifications by remember {
        mutableStateOf(
            emptyList<NotificationItem>()
        )
    }

    val filtered = remember(selectedCategory, notifications) {
        if (selectedCategory == NotificationCategory.ALL) notifications
        else notifications.filter { it.category == selectedCategory }
    }

    val unreadCount = remember(notifications) {
        notifications.count { it.isUnread }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Scaffold(
            containerColor = Background,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Back button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Carbon)
                                    .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowBackIosNew,
                                    contentDescription = "Back",
                                    tint = White90,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = White100
                            )
                            if (unreadCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(VoltGlassLight)
                                        .border(0.6.dp, VoltBorder, RoundedCornerShape(999.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "$unreadCount New",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = VoltGreen
                                        )
                                    )
                                }
                            }
                        }

                        // Mark all read button
                        if (unreadCount > 0) {
                            Text(
                                text = "Mark all read",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = VoltGreen
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        notifications = notifications.map { it.copy(isUnread = false) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Category Spotlight Filter Bar with Top LED & Downward Light Beam
                    SpotlightFilterBar(
                        items = NotificationCategory.values().toList(),
                        selectedItem = selectedCategory,
                        onItemSelected = { selectedCategory = it },
                        labelProvider = { it.label },
                        countProvider = { cat ->
                            val c = if (cat == NotificationCategory.ALL) notifications.size
                            else notifications.count { it.category == cat }
                            if (c > 0) c else null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { inner ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = 36.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF141720).copy(0.70f))
                                        .border(0.8.dp, Color.White.copy(0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.NotificationsNone,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "You're all caught up with your mesh events",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { item ->
                        NotificationCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem) {
    val cardShape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (item.isUnread) VoltGlassLight else Carbon)
            .border(
                0.8.dp,
                if (item.isUnread) VoltBorder else GlassEdgeSubtle,
                cardShape
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Icon pod
            val iconShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(iconShape)
                    .background(item.iconTint.copy(alpha = 0.14f))
                    .border(0.8.dp, item.iconTint.copy(alpha = 0.30f), iconShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (item.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = White100,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = White40
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = White60,
                        lineHeight = 18.sp
                    )
                )

                if (item.actionLabel != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(0.08f))
                            .border(0.6.dp, Color.White.copy(0.18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item.actionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SoftEmerald
                            )
                        )
                    }
                }
            }

            if (item.isUnread) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SoftEmerald)
                )
            }
        }
    }
}
