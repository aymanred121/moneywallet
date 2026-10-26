package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.ui.theme.Motion

/** Wraps [onClose] so it runs at most once, e.g. when a Saved event races a tap on Close. */
@Composable
fun rememberCloseOnce(onClose: () -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onClose)
    return remember {
        var closed = false
        { if (!closed) { closed = true; latest() } }
    }
}

/** Full-width primary action at the bottom of an editor, within thumb reach; shows progress while [saving]. */
@Composable
fun SaveButton(saving: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, label: String = "Save") {
    Button(
        onClick = onClick, enabled = enabled && !saving,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp)
            .semantics { if (saving) contentDescription = "Saving" },
    ) {
        AnimatedContent(saving, transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) }, label = "save") { busy ->
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = LocalContentColor.current)
            else Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
