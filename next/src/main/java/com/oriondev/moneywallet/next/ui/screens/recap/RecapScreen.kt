package com.oriondev.moneywallet.next.ui.screens.recap

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.domain.Period
import com.oriondev.moneywallet.next.ui.components.SpecIcon
import com.oriondev.moneywallet.next.ui.components.containerViewModel
import com.oriondev.moneywallet.next.ui.screens.home.TotalText
import com.oriondev.moneywallet.next.ui.theme.MoneyDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(period: Period, onBack: () -> Unit) {
    val vm = containerViewModel(key = "recap-${Period.encode(period)}") { RecapViewModel(it, period) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isYear) "Year in review" else "Recap") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val ctx = state.ctx ?: return@Scaffold
        val footer = "MoneyWallet · ${state.period.label}"
        val so = if (state.partial) " so far" else ""
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "spent") {
                RecapCard("spent", footer, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                    Text("You spent$so", style = MaterialTheme.typography.titleMedium)
                    TotalText(state.spent, ctx, style = MoneyDisplay)
                    state.change?.let { c ->
                        val than = if (state.partial) "at this point last ${state.noun}" else "last ${state.noun}"
                        Text(if (abs(c) < 3) "About the same as $than" else "${abs(c)}% ${if (c < 0) "less" else "more"} than $than", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            state.topCategory?.let { top ->
                item(key = "category") {
                    RecapCard("top-category", footer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                        Text("Top category", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpecIcon(top.icon, size = 48.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(top.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TotalText(top.total, ctx, style = MaterialTheme.typography.titleLarge)
                            Text("  ·  ${(top.fraction * 100).toInt()}% of spending", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            state.biggestDay?.let { day ->
                item(key = "day") {
                    RecapCard("biggest-day", footer, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
                        Text("Biggest day", style = MaterialTheme.typography.titleMedium)
                        Text(day.day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), style = MaterialTheme.typography.headlineSmall)
                        TotalText(day.total, ctx, style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
            item(key = "savings") {
                RecapCard("savings-rate", footer, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                    Text("Savings rate", style = MaterialTheme.typography.titleMedium)
                    val rate = state.savingsRate
                    Text(rate?.let { "$it%" } ?: "—", style = MaterialTheme.typography.displayMedium)
                    Text(
                        when {
                            rate == null -> "No income recorded this ${state.noun}$so."
                            rate >= 0 -> "Of what you earned$so, you kept $rate%."
                            else -> "You spent more than you earned$so."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (state.trackedDays > 0) item(key = "no-spend") {
                RecapCard("no-spend-days", footer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text("No-spend days", style = MaterialTheme.typography.titleMedium)
                    Text("${state.noSpendDays}", style = MaterialTheme.typography.displayMedium)
                    Text("out of ${state.trackedDays} days tracked", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.subscriptionCount > 0) item(key = "subscriptions") {
                RecapCard("subscriptions", footer, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
                    Text("Subscriptions & bills", style = MaterialTheme.typography.titleMedium)
                    TotalText(state.subscriptions, ctx, style = MaterialTheme.typography.displaySmall)
                    Text("across ${state.subscriptionCount} regular ${if (state.subscriptionCount == 1) "charge" else "charges"} this ${state.noun}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item(key = "note") {
                Text(
                    "Tap the share icon on a card to send it as an image.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A big-number card that can be shared as a PNG: its drawing is recorded into a graphics layer
 * (the share button sits outside it), rendered to a bitmap on demand and handed to the share sheet.
 */
@Composable
private fun RecapCard(name: String, footer: String, container: Color, content: Color, body: @Composable ColumnScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Box {
        Card(
            Modifier.fillMaxWidth().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                body()
                Spacer(Modifier.height(8.dp))
                Text(footer, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
            }
        }
        IconButton(
            onClick = { scope.launch { runCatching { shareImage(context, layer.toImageBitmap().asAndroidBitmap(), "recap-$name") } } },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) { Icon(Icons.Filled.Share, contentDescription = "Share this card", tint = content) }
    }
}

/** Writes [bitmap] to cache/shared (exposed by the FileProvider) and opens the share sheet. */
private suspend fun shareImage(context: Context, bitmap: Bitmap, name: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "$name.png")
        val software = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        file.outputStream().use { software.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share recap"))
}
