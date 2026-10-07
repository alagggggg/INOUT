package com.quan.thuchi

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class WidgetConfigActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val categoryLists = mutableMapOf<String, List<CategoryItem>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.activity_widget_config)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setup("income")
        setup("expense")
        findViewById<Button>(R.id.save_widget).setOnClickListener { save() }
    }

    private fun setup(kind: String) {
        val items = WidgetCategoryCatalog.byKind(kind)
        categoryLists[kind] = items
        val labels = items.map {
            "${WidgetCategoryCatalog.cleanText(it.icon)}  ${WidgetCategoryCatalog.cleanText(it.name)}"
        }
        val preferences = getSharedPreferences(QuickEntryWidgetProvider.PREFS, MODE_PRIVATE)
        val editor = preferences.edit()

        for (index in 1..4) {
            val spinner = findViewById<Spinner>(
                resources.getIdentifier("${kind}_spinner_$index", "id", packageName)
            )
            spinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )
            val key = QuickEntryWidgetProvider.key(widgetId, kind, index)
            val fallback = QuickEntryWidgetProvider.defaults(kind)[index - 1]
            val normalizedId = WidgetCategoryCatalog.normalizeStoredId(
                preferences.getString(key, fallback),
                kind,
                fallback
            )
            editor.putString(key, normalizedId)
            spinner.setSelection(items.indexOfFirst { it.id == normalizedId }.coerceAtLeast(0))
        }
        editor.apply()
    }

    private fun save() {
        val editor = getSharedPreferences(QuickEntryWidgetProvider.PREFS, MODE_PRIVATE).edit()
        for (kind in listOf("income", "expense")) {
            val items = categoryLists.getValue(kind)
            for (index in 1..4) {
                val spinner = findViewById<Spinner>(
                    resources.getIdentifier("${kind}_spinner_$index", "id", packageName)
                )
                val selected = items[spinner.selectedItemPosition.coerceIn(items.indices)]
                editor.putString(QuickEntryWidgetProvider.key(widgetId, kind, index), selected.id)
            }
        }
        editor.apply()
        QuickEntryWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}
