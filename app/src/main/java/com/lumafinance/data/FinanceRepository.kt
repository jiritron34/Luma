package com.lumafinance.data

import android.content.Context
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import com.lumafinance.domain.FinanceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class FinanceRepository(context: Context) {
    private val dao = FinanceDatabase.get(context).financeDao()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    val accounts = dao.accounts(); val projects = dao.projects(); val goals = dao.goals(); val entries = dao.entries()
    val overview = combine(accounts, entries, goals) { a, e, g -> Triple(a, e, g) }
    suspend fun seed() {
        if (dao.activeAccounts().isEmpty()) dao.putAccount(Account("cash", "Everyday", openingBalanceCents = 0))
    }
    suspend fun saveEntry(id: String?, cents: Long, type: String, category: String, note: String, day: Long, account: String, target: String?, project: String?) {
        require(cents > 0)
        dao.putEntry(LedgerEntry(id ?: UUID.randomUUID().toString(), cents, type, category.ifBlank { if(type=="income") "Other income" else "Other" }, note, day, account, target, project))
    }
    suspend fun deleteEntry(id: String) = dao.deleteEntry(id)
    suspend fun saveAccount(name: String, type: String, openingCents: Long, currency: String) = dao.putAccount(Account(UUID.randomUUID().toString(), name, type, currency, openingCents))
    suspend fun saveGoal(name: String, targetCents: Long) = dao.putGoal(Goal(UUID.randomUUID().toString(), name, targetCents))
    suspend fun addGoalSavings(goalId: String, cents: Long) { require(cents > 0); dao.addGoalSavings(goalId, cents) }
    suspend fun saveProject(name: String, budgetCents: Long, currency: String = "USD") = dao.putProject(Project(UUID.randomUUID().toString(), name, budgetCents, currency = currency))
    suspend fun backup(): String = json.encodeToString(Backup.serializer(), Backup(accounts=dao.activeAccounts(), projects=dao.allProjects(), goals=dao.allGoals(), transactions=dao.allEntries()))
    suspend fun restore(source: String) {
        val backup = json.decodeFromString(Backup.serializer(), source)
        require(backup.version == 1) { "Unsupported backup version" }
        FinanceEngine.accountBalances(backup.accounts, backup.transactions)
        dao.replaceAll(backup.accounts, backup.projects, backup.goals, backup.transactions)
    }
    suspend fun csv(): String {
        val accountsById = dao.activeAccounts().associateBy { it.id }
        val rows = dao.allEntries().sortedBy { it.dateEpochDay }
        return withContext(Dispatchers.Default) { buildString {
            appendLine("date,type,category,amount,currency,account,note,project")
            rows.forEach { e ->
                val fields = listOf(LocalDate.ofEpochDay(e.dateEpochDay).toString(), e.type, e.category, "%.2f".format(java.util.Locale.US, e.amountCents/100.0), accountsById[e.accountId]?.currency ?: "USD", accountsById[e.accountId]?.name ?: e.accountId, e.note, e.projectId ?: "")
                appendLine(fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        } }
    }
}
