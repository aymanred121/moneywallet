package com.oriondev.moneywallet.next.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.oriondev.moneywallet.next.ui.Routes
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Per-day safe-to-spend (see [com.oriondev.moneywallet.next.domain.pacing.PacingEngine]) and days to
 * payday. Without a budget or income this cycle it shows the spend so far instead of inventing a number.
 */
class SafeToSpendWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withTimeoutOrNull(4_000) { WidgetSnapshot.load(context) } ?: WidgetSnapshot.EMPTY
        provideContent { GlanceTheme { Content(context, data) } }
    }

    @Composable
    private fun Content(context: Context, data: WidgetSnapshot) {
        val compact = LocalSize.current.height < FULL.height
        val on = GlanceTheme.colors.onSecondaryContainer
        val headline = data.overLine ?: data.safePerDay ?: data.spentLine
        val label = when {
            data.overLine != null -> "This cycle"
            data.safePerDay != null -> "Safe to spend"
            else -> data.spentLabel
        }
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.secondaryContainer).cornerRadius(24.dp)
                .clickable(actionStartActivity(openRoute(context, Routes.HOME))).padding(if (compact) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) Text(label, style = TextStyle(color = on, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(headline, style = TextStyle(color = if (data.overLine != null) GlanceTheme.colors.error else on, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (data.daysLeftLabel.isNotEmpty()) Text(data.daysLeftLabel, style = TextStyle(color = on, fontSize = 12.sp), maxLines = 1)
        }
    }

    companion object {
        private val COMPACT = DpSize(110.dp, 40.dp)
        private val FULL = DpSize(110.dp, 90.dp)
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()
}
