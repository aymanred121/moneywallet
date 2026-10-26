package com.oriondev.moneywallet.next.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.oriondev.moneywallet.next.ui.Routes

/** 1×1 "+" that opens the transaction editor. Shows no data, so it never needs a refresh. */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val add = openRoute(context, Routes.addTransaction())
        provideContent {
            GlanceTheme {
                Box(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primary).cornerRadius(24.dp)
                        .clickable(actionStartActivity(add)).semantics { contentDescription = "Add transaction" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
