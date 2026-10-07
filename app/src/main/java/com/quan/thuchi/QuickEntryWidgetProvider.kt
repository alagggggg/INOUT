package com.quan.thuchi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class QuickEntryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { id ->
            (1..4).forEach { index ->
                editor.remove(key(id, "income", index))
                editor.remove(key(id, "expense", index))
            }
        }
        editor.apply()
    }

    companion object {
        const val PREFS = "quick_widget_prefs"
        const val EXTRA_KIND = "quick_kind"
        const val EXTRA_CATEGORY_ID = "quick_category_id"

        private val defaultIncome = arrayOf("salary", "bonus", "business", "used_sale")
        private val defaultExpense = arrayOf("groceries", "transport", "family_shop", "unexpected")
        private val incomeCells = intArrayOf(R.id.income_cell_1, R.id.income_cell_2, R.id.income_cell_3, R.id.income_cell_4)
        private val expenseCells = intArrayOf(R.id.expense_cell_1, R.id.expense_cell_2, R.id.expense_cell_3, R.id.expense_cell_4)
        private val incomeIcons = intArrayOf(R.id.income_icon_1, R.id.income_icon_2, R.id.income_icon_3, R.id.income_icon_4)
        private val expenseIcons = intArrayOf(R.id.expense_icon_1, R.id.expense_icon_2, R.id.expense_icon_3, R.id.expense_icon_4)
        private val incomeNames = intArrayOf(R.id.income_name_1, R.id.income_name_2, R.id.income_name_3, R.id.income_name_4)
        private val expenseNames = intArrayOf(R.id.expense_name_1, R.id.expense_name_2, R.id.expense_name_3, R.id.expense_name_4)

        fun key(id: Int, kind: String, index: Int) = "widget_${id}_${kind}_$index"
        fun defaults(kind: String) = if (kind == "income") defaultIncome else defaultExpense

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            val views = RemoteViews(context.packageName, R.layout.widget_quick_entry)

            for (index in 1..4) {
                val incomeKey = key(widgetId, "income", index)
                val incomeId = WidgetCategoryCatalog.normalizeStoredId(
                    preferences.getString(incomeKey, defaultIncome[index - 1]),
                    "income",
                    defaultIncome[index - 1]
                )
                editor.putString(incomeKey, incomeId)
                bind(context, views, widgetId, index, "income", incomeId, incomeCells[index - 1], incomeIcons[index - 1], incomeNames[index - 1])

                val expenseKey = key(widgetId, "expense", index)
                val expenseId = WidgetCategoryCatalog.normalizeStoredId(
                    preferences.getString(expenseKey, defaultExpense[index - 1]),
                    "expense",
                    defaultExpense[index - 1]
                )
                editor.putString(expenseKey, expenseId)
                bind(context, views, widgetId, index + 4, "expense", expenseId, expenseCells[index - 1], expenseIcons[index - 1], expenseNames[index - 1])
            }
            editor.apply()

            views.setOnClickPendingIntent(
                R.id.widget_open_app,
                PendingIntent.getActivity(
                    context,
                    widgetId * 20,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(widgetId, views)
        }

        private fun bind(
            context: Context,
            views: RemoteViews,
            widgetId: Int,
            slot: Int,
            kind: String,
            categoryId: String,
            cellView: Int,
            iconView: Int,
            nameView: Int
        ) {
            val category = WidgetCategoryCatalog.find(categoryId)
                ?.takeIf { it.kind == kind }
                ?: WidgetCategoryCatalog.byKind(kind).first()
            views.setTextViewText(iconView, WidgetCategoryCatalog.cleanText(category.icon))
            views.setTextViewText(nameView, WidgetCategoryCatalog.cleanText(category.name))
            val intent = Intent(context, QuickEntryActivity::class.java)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_CATEGORY_ID, category.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                cellView,
                PendingIntent.getActivity(
                    context,
                    widgetId * 20 + slot,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
    }
}
