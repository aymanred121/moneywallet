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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.oriondev.moneywallet.next.ui.Routes
import kotlinx.coroutines.withTimeoutOrNull

/** Balance + spend this payday cycle + add button; a small placement shows just the balance. */
class BalanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withTimeoutOrNull(4_000) { WidgetSnapshot.load(context) } ?: WidgetSnapshot.EMPTY
        provideContent { GlanceTheme { Content(context, data) } }
    }

    @Composable
    private fun Content(context: Context, data: WidgetSnapshot) {
        val size = LocalSize.current
        val compact = size.width < FULL.width || size.height < FULL.height
        val on = GlanceTheme.colors.onPrimaryContainer
        Box(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primaryContainer).cornerRadius(24.dp)
                .clickable(actionStartActivity(openRoute(context, Routes.HOME))),
            contentAlignment = if (compact) Alignment.CenterStart else Alignment.TopStart,
        ) {
            Column(GlanceModifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp), verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top) {
                Text("Total balance", style = TextStyle(color = on, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.Medium))
                data.balanceLines.take(if (compact) 1 else 2).forEachIndexed { i, line ->
                    Text(line, style = TextStyle(color = on, fontSize = if (i > 0) 16.sp else if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                if (compact) return@Column
                if (data.rateHint) Text("Rate unavailable", style = TextStyle(color = on, fontSize = 10.sp))
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(data.spentLabel, style = TextStyle(color = on, fontSize = 11.sp), maxLines = 1)
                        Text(data.spentLine, style = TextStyle(color = on, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Box(
                        GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(20.dp).height(40.dp).padding(horizontal = 16.dp)
                            .clickable(actionStartActivity(openRoute(context, Routes.addTransaction())))
                            .semantics { contentDescription = "Add transaction" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ Add", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }

    companion object {
        private val COMPACT = DpSize(110.dp, 40.dp)
        private val FULL = DpSize(180.dp, 110.dp)
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
