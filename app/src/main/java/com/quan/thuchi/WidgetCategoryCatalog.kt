package com.quan.thuchi

import org.json.JSONObject

data class CategoryItem(
    val id: String,
    val icon: String,
    val kind: String,
    val name: String
)

object WidgetCategoryCatalog {
    val all = listOf(
        CategoryItem("salary", "💼", "income", "Lương cố định"),
        CategoryItem("bonus", "💰", "income", "Thưởng / Phúc lợi"),
        CategoryItem("partner", "💸", "income", "Thu nhập bạn đời"),
        CategoryItem("business", "🏪", "income", "Nghề tay trái"),
        CategoryItem("investment_profit", "📈", "income", "Lợi nhuận đầu tư"),
        CategoryItem("passive", "🏢", "income", "Thu nhập thụ động"),
        CategoryItem("gift_in", "🧧", "income", "Quà tặng / Hiếu hỷ"),
        CategoryItem("debt_recovery", "🔄", "income", "Thu hồi nợ"),
        CategoryItem("refund_tax", "🛡️", "income", "Hoàn tiền"),
        CategoryItem("used_sale", "♻️", "income", "Thanh lý đồ cũ"),
        CategoryItem("housing", "🏡", "expense", "Nhà ở & Tiện ích"),
        CategoryItem("groceries", "🛒", "expense", "Đi chợ & Thực phẩm"),
        CategoryItem("children", "👶", "expense", "Con cái & Giáo dục"),
        CategoryItem("transport", "🚗", "expense", "Di chuyển & Xe cộ"),
        CategoryItem("family_shop", "🛍️", "expense", "Mua sắm gia đình"),
        CategoryItem("health", "🏥", "expense", "Y tế & Bảo hiểm"),
        CategoryItem("social", "☕", "expense", "Giao lưu & Giải trí"),
        CategoryItem("relations", "🤝", "expense", "Hiếu hỷ & Đối nội / ngoại"),
        CategoryItem("debt_payment", "💳", "expense", "Trả nợ & Trả góp"),
        CategoryItem("unexpected", "⚠️", "expense", "Phát sinh bất ngờ")
    )

    fun byKind(kind: String) = all.filter { it.kind == kind }
    fun find(id: String) = all.firstOrNull { it.id == id }

    fun cleanText(value: String): String = value
        .replace(Regex("<[^>]*>"), "")
        .replace("[object Object]", "", ignoreCase = true)
        .replace("ObjectHTML", "", ignoreCase = true)
        .replace("HTMLObjectElement", "", ignoreCase = true)
        .trim()

    fun normalizeStoredId(raw: String?, kind: String, fallback: String): String {
        val value = raw.orEmpty().trim()
        find(value)?.takeIf { it.kind == kind }?.let { return it.id }

        if (value.startsWith("{")) {
            runCatching { JSONObject(value).optString("id") }
                .getOrNull()
                ?.let { find(it) }
                ?.takeIf { it.kind == kind }
                ?.let { return it.id }
        }

        val cleaned = cleanText(value)
        byKind(kind).firstOrNull {
            it.name.equals(cleaned, ignoreCase = true) ||
                "${it.icon} ${it.name}".equals(cleaned, ignoreCase = true)
        }?.let { return it.id }

        return find(fallback)?.takeIf { it.kind == kind }?.id ?: byKind(kind).first().id
    }
}
