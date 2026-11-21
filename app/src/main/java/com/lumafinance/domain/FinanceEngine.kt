package com.lumafinance.domain

import com.lumafinance.data.*
import java.time.LocalDate
import java.time.YearMonth

object FinanceEngine {
    data class Summary(val income: Long, val expenses: Long, val net: Long)
    fun accountBalances(accounts: List<Account>, entries: List<LedgerEntry>): Map<String, Long> {
        val result = accounts.associate { it.id to it.openingBalanceCents }.toMutableMap()
        val currencyById = accounts.associate { it.id to it.currency.uppercase() }
        entries.forEach { e -> when (e.type) {
            "income" -> { require(e.accountId in currencyById) { "Transaction references a missing account" }; result[e.accountId] = Math.addExact(result[e.accountId] ?: 0L, e.amountCents) }
            "expense" -> { require(e.accountId in currencyById) { "Transaction references a missing account" }; result[e.accountId] = Math.subtractExact(result[e.accountId] ?: 0L, e.amountCents) }
            "transfer" -> {
                require(e.accountId in currencyById) { "Transaction references a missing source account" }
                val target = requireNotNull(e.targetAccountId) { "A transfer needs a destination account" }
                require(target in currencyById) { "Transaction references a missing destination account" }
                require(currencyById[e.accountId] == currencyById[target]) { "Cross-currency transfers need an explicit exchange amount" }
                result[e.accountId] = Math.subtractExact(result[e.accountId] ?: 0L, e.amountCents)
                result[target] = Math.addExact(result[target] ?: 0L, e.amountCents)
            }
        } }
        return result
    }
    fun balancesByCurrency(accounts: List<Account>, entries: List<LedgerEntry>): Map<String, Long> {
        val balances = accountBalances(accounts, entries)
        return accounts.groupBy { it.currency.uppercase() }.mapValues { (_, group) -> group.fold(0L) { total, account -> Math.addExact(total, balances[account.id] ?: 0L) } }
    }
    fun summary(entries: List<LedgerEntry>, fromDay: Long, toDayExclusive: Long): Summary {
        val selected = entries.filter { it.dateEpochDay in fromDay until toDayExclusive }
        val income = checkedSum(selected.filter { it.type == "income" })
        val expense = checkedSum(selected.filter { it.type == "expense" })
        return Summary(income, expense, Math.subtractExact(income, expense))
    }
    fun monthSummary(entries: List<LedgerEntry>, month: YearMonth): Summary = summary(entries, month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay())
    fun yearSummary(entries: List<LedgerEntry>, year: Int): Summary = summary(entries, LocalDate.of(year,1,1).toEpochDay(), LocalDate.of(year+1,1,1).toEpochDay())
    fun summariesByCurrency(entries: List<LedgerEntry>, accounts: List<Account>, fromDay: Long, toDayExclusive: Long): Map<String, Summary> {
        val currencyByAccount = accounts.associate { it.id to it.currency.uppercase() }
        val currencies = accounts.map { it.currency.uppercase() }.distinct()
        return currencies.associateWith { currency -> summary(entries.filter { currencyByAccount[it.accountId] == currency }, fromDay, toDayExclusive) }
    }
    fun categorySpend(entries: List<LedgerEntry>, fromDay: Long, toDayExclusive: Long): Map<String, Long> = entries.asSequence().filter { it.type == "expense" && it.dateEpochDay in fromDay until toDayExclusive }.groupBy { it.category }.mapValues { (_, list) -> checkedSum(list) }
    fun goalProgress(goal: Goal): Double = if (goal.targetCents <= 0) 0.0 else (goal.savedCents.toDouble() / goal.targetCents).coerceIn(0.0, 1.0)
    data class SmallExpense(val amountCents: Long, val count: Int) {
        init { require(amountCents > 0); require(count > 0) }
        val totalCents: Long get() = Math.multiplyExact(amountCents, count.toLong())
    }
    private fun checkedSum(entries: List<LedgerEntry>): Long = entries.fold(0L) { total, entry -> Math.addExact(total, entry.amountCents) }
}
