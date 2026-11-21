package com.lumafinance.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lumafinance.MainActivity
import com.lumafinance.R
import com.lumafinance.data.FinanceDatabase
import com.lumafinance.domain.FinanceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.YearMonth

abstract class FinanceWidget : AppWidgetProvider() {
    abstract val mode: String
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context, manager, ids, mode) }
    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray, kind: String) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FinanceDatabase.get(context)
                val dao = db.financeDao()
                val accounts = dao.activeAccounts(); val entries = dao.allEntries(); val goals = dao.allGoals()
                val balances = FinanceEngine.balancesByCurrency(accounts, entries)
                val month = FinanceEngine.summariesByCurrency(entries, accounts, YearMonth.now().atDay(1).toEpochDay(), YearMonth.now().plusMonths(1).atDay(1).toEpochDay())
                val goal = goals.maxByOrNull { FinanceEngine.goalProgress(it) }
                val views = RemoteViews(context.packageName, R.layout.widget_card)
                val label = when(kind) { "balance" -> context.getString(R.string.widget_balance); "spending" -> context.getString(R.string.widget_spending); "goals" -> context.getString(R.string.widget_goals); else -> context.getString(R.string.widget_quick) }
                val value = when(kind) {
                    "balance" -> balances.entries.joinToString(" · ") { (currency,cents) -> "$currency ${"%,.2f".format(java.util.Locale.US, cents / 100.0)}" }.ifBlank { "USD 0.00" }
                    "spending" -> month.filterValues { it.expenses > 0 }.entries.joinToString(" · ") { (currency,summary) -> "$currency ${"%,.2f".format(java.util.Locale.US, summary.expenses / 100.0)}" }.ifBlank { "No spending yet" }
                    "goals" -> goal?.let { "${it.name} · ${(FinanceEngine.goalProgress(it)*100).toInt()}%" } ?: "Create your first goal"
                    else -> "Tap to add an expense"
                }
                views.setTextViewText(R.id.widget_title, label)
                views.setTextViewText(R.id.widget_value, value)
                val target = Intent(context, MainActivity::class.java).putExtra("destination", if(kind=="quick") "quick" else "home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, kind.hashCode(), target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally { pending.finish() }
        }
    }
}

class BalanceWidget : FinanceWidget() { override val mode = "balance" }
class SpendingWidget : FinanceWidget() { override val mode = "spending" }
class GoalsWidget : FinanceWidget() { override val mode = "goals" }
class QuickExpenseWidget : FinanceWidget() { override val mode = "quick" }

fun refreshAllWidgets(context: Context) {
    listOf(BalanceWidget::class.java, SpendingWidget::class.java, GoalsWidget::class.java, QuickExpenseWidget::class.java).forEach { provider ->
        val intent = Intent(context, provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider)))
        context.sendBroadcast(intent)
    }
}
