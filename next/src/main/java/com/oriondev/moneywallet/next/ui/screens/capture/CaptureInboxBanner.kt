package com.oriondev.moneywallet.next.ui.screens.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.ui.components.LocalAppContainer
import com.oriondev.moneywallet.next.ui.theme.Motion

/**
 * "N payments to review" card for Home; takes no space while the capture inbox is empty.
 * Pads itself horizontally (16dp) like the other Home cards.
 */
@Composable
fun CaptureInboxBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val count by remember(container) { container.db.captures().observePendingCount() }.collectAsStateWithLifecycle(initialValue = 0)
    // keep the last non-zero count on screen while the card animates away
    var shown by remember { mutableIntStateOf(0) }
    if (count > 0) shown = count
    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = fadeIn(Motion.effects()) + expandVertically(Motion.spatial()),
        exit = fadeOut(Motion.effects()) + shrinkVertically(Motion.spatial()),
    ) {
        Card(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Contactless, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (shown == 1) "1 payment to review" else "$shown payments to review", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Captured from your wallet and bank notifications", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}
