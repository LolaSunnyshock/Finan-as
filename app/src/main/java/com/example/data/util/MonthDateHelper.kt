package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

object MonthDateHelper {

    private val monthNamesPtBr = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    private val shortMonthNamesPtBr = listOf(
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )

    fun currentYearMonth(calendar: Calendar = Calendar.getInstance()): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun currentDayOfMonth(calendar: Calendar = Calendar.getInstance()): Int {
        return calendar.get(Calendar.DAY_OF_MONTH).coerceIn(1, 31)
    }

    fun currentDateString(calendar: Calendar = Calendar.getInstance()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(calendar.time)
    }

    fun parseYearMonth(yearMonth: String): Pair<Int, Int>? {
        val match = Regex("""^(\d{4})-(\d{1,2})""").find(yearMonth.trim()) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return year to month
    }

    fun extractYearMonth(dateStr: String): String {
        val parsed = parseYearMonth(dateStr)
        if (parsed != null) {
            return String.format(Locale.US, "%04d-%02d", parsed.first, parsed.second)
        }
        return currentYearMonth()
    }

    fun extractDayOfMonth(dateStr: String, fallbackDay: Int = currentDayOfMonth()): Int {
        val match = Regex("""^\d{4}-\d{1,2}-(\d{1,2})""").find(dateStr.trim()) ?: return fallbackDay.coerceIn(1, 31)
        val day = match.groupValues[1].toIntOrNull() ?: return fallbackDay.coerceIn(1, 31)
        return day.coerceIn(1, 31)
    }

    fun shiftYearMonth(yearMonth: String, offsetMonths: Int): String {
        val (year, month) = parseYearMonth(yearMonth) ?: parseYearMonth(currentYearMonth())!!
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, offsetMonths)
        }
        return currentYearMonth(cal)
    }

    fun maxDayInMonth(yearMonth: String): Int {
        val (year, month) = parseYearMonth(yearMonth) ?: return 28
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun buildDateForMonth(yearMonth: String, preferredDay: Int): String {
        val (year, month) = parseYearMonth(yearMonth) ?: parseYearMonth(currentYearMonth())!!
        val maxDay = maxDayInMonth(String.format(Locale.US, "%04d-%02d", year, month))
        val clampedDay = preferredDay.coerceIn(1, maxDay)
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, clampedDay)
    }

    fun formatMonthYearLabel(yearMonth: String): String {
        val (year, month) = parseYearMonth(yearMonth) ?: return yearMonth
        val name = monthNamesPtBr.getOrElse(month - 1) { "Mês" }
        return "$name $year"
    }

    fun formatShortMonthYear(yearMonth: String): String {
        val (year, month) = parseYearMonth(yearMonth) ?: return yearMonth
        val name = shortMonthNamesPtBr.getOrElse(month - 1) { "Mês" }
        return "$name/$year"
    }

    fun formatDisplayDate(dateStr: String): String {
        val match = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").find(dateStr.trim()) ?: return dateStr
        val year = match.groupValues[1]
        val month = match.groupValues[2].padStart(2, '0')
        val day = match.groupValues[3].padStart(2, '0')
        return "$day/$month/$year"
    }

    fun splitInstallmentAmounts(
        inputAmount: Double,
        installments: Int,
        amountIsTotal: Boolean
    ): List<Double> {
        val safeInstallments = installments.coerceAtLeast(1)
        val totalCents = (inputAmount * 100.0).roundToLong().coerceAtLeast(1L)
        if (safeInstallments == 1) {
            return listOf(totalCents / 100.0)
        }
        return if (amountIsTotal) {
            val baseCents = totalCents / safeInstallments
            val remainder = (totalCents % safeInstallments).toInt()
            List(safeInstallments) { index ->
                val cents = (baseCents + if (index < remainder) 1L else 0L).coerceAtLeast(1L)
                cents / 100.0
            }
        } else {
            val single = totalCents / 100.0
            List(safeInstallments) { single }
        }
    }

    fun stripInstallmentSuffix(description: String): String {
        return description.trim()
            .replace(Regex("""\s*\(\d+/\d+\)$"""), "")
            .trim()
    }

    fun buildInstallmentDescription(
        baseDescription: String,
        installmentNumber: Int,
        totalInstallments: Int
    ): String {
        val clean = if (totalInstallments > 1) {
            stripInstallmentSuffix(baseDescription)
        } else {
            baseDescription.trim()
        }
        if (totalInstallments <= 1) {
            return clean.take(150)
        }
        val suffix = " ($installmentNumber/$totalInstallments)"
        val maxBaseLength = (150 - suffix.length).coerceAtLeast(1)
        return clean.take(maxBaseLength) + suffix
    }

    fun parseInstallmentInfo(description: String): Triple<String, Int, Int>? {
        val match = Regex("""^(.+?)\s*\((\d+)/(\d+)\)$""").find(description.trim()) ?: return null
        val baseDesc = match.groupValues[1].trim()
        val index = match.groupValues[2].toIntOrNull() ?: return null
        val total = match.groupValues[3].toIntOrNull() ?: return null
        if (total <= 1 || index !in 1..total) return null
        return Triple(baseDesc, index, total)
    }

    fun findInstallmentSeries(
        target: com.example.data.model.TransactionItem,
        allTransactions: List<com.example.data.model.TransactionItem>
    ): List<com.example.data.model.TransactionItem> {
        if (target.installmentGroupId.isNotBlank()) {
            val byGroup = allTransactions.filter { it.installmentGroupId == target.installmentGroupId }
            if (byGroup.isNotEmpty()) return byGroup
        }
        val parsedTarget = parseInstallmentInfo(target.description) ?: return listOf(target)
        val (targetBase, _, targetTotal) = parsedTarget
        val byLabel = allTransactions.filter { item ->
            val parsed = parseInstallmentInfo(item.description) ?: return@filter false
            parsed.first.equals(targetBase, ignoreCase = true) &&
                parsed.third == targetTotal &&
                item.type == target.type &&
                item.category.equals(target.category, ignoreCase = true)
        }
        return if (byLabel.isNotEmpty()) byLabel else listOf(target)
    }

    fun isFixedActiveInMonth(
        tx: com.example.data.model.TransactionItem,
        targetYearMonth: String
    ): Boolean {
        if (!tx.isFixed) return false
        val startTuple = parseYearMonth(tx.date) ?: return false
        val startYm = String.format(Locale.US, "%04d-%02d", startTuple.first, startTuple.second)
        if (targetYearMonth < startYm) return false
        if (tx.fixedEndMonth.isNotBlank()) {
            val endTuple = parseYearMonth(tx.fixedEndMonth)
            if (endTuple != null) {
                val endYm = String.format(Locale.US, "%04d-%02d", endTuple.first, endTuple.second)
                if (targetYearMonth > endYm) return false
            }
        }
        return true
    }

    fun projectFixedTransactionForMonth(
        tx: com.example.data.model.TransactionItem,
        targetYearMonth: String
    ): com.example.data.model.TransactionItem {
        val day = extractDayOfMonth(tx.date)
        return tx.copy(date = buildDateForMonth(targetYearMonth, day))
    }
}
