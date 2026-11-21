package com.lumafinance.data

import androidx.room.*
import kotlinx.serialization.Serializable

@Entity(tableName = "accounts")
@Serializable data class Account(@PrimaryKey val id: String, val name: String, val type: String = "cash", val currency: String = "USD", val openingBalanceCents: Long = 0, val color: Long = 0xFF7166E8, val archived: Boolean = false) {
    init { require(id.isNotBlank() && name.isNotBlank()); require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be an ISO-style three-letter code" } }
}

@Entity(tableName = "projects")
@Serializable data class Project(@PrimaryKey val id: String, val name: String, val budgetCents: Long = 0, val color: Long = 0xFF39A887, val archived: Boolean = false, val currency: String = "USD") {
    init { require(id.isNotBlank() && name.isNotBlank()); require(budgetCents >= 0); require(currency.matches(Regex("[A-Z]{3}"))) }
}

@Entity(tableName = "goals")
@Serializable data class Goal(@PrimaryKey val id: String, val name: String, val targetCents: Long, val savedCents: Long = 0, val deadlineEpochDay: Long? = null, val color: Long = 0xFFEF9D59) {
    init { require(id.isNotBlank() && name.isNotBlank()); require(targetCents > 0 && savedCents >= 0) }
}

@Entity(tableName = "transactions", indices = [Index("dateEpochDay"), Index("accountId"), Index("projectId")])
@Serializable data class LedgerEntry(
    @PrimaryKey val id: String,
    val amountCents: Long,
    val type: String,
    val category: String,
    val note: String = "",
    val dateEpochDay: Long,
    val accountId: String,
    val targetAccountId: String? = null,
    val projectId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    init {
        require(amountCents > 0) { "Amounts must be positive integer cents" }
        require(type in setOf("income", "expense", "transfer"))
        require(type != "transfer" || (!targetAccountId.isNullOrBlank() && targetAccountId != accountId)) { "A transfer needs a different destination account" }
    }
}

@Dao interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY archived, name") fun accounts(): kotlinx.coroutines.flow.Flow<List<Account>>
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY name") suspend fun activeAccounts(): List<Account>
    @Query("SELECT * FROM projects ORDER BY archived, name") fun projects(): kotlinx.coroutines.flow.Flow<List<Project>>
    @Query("SELECT * FROM goals ORDER BY deadlineEpochDay") fun goals(): kotlinx.coroutines.flow.Flow<List<Goal>>
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAtMillis DESC") fun entries(): kotlinx.coroutines.flow.Flow<List<LedgerEntry>>
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAtMillis DESC") suspend fun allEntries(): List<LedgerEntry>
    @Query("SELECT * FROM goals ORDER BY deadlineEpochDay") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM goals WHERE id = :goalId") suspend fun goalById(goalId: String): Goal?
    @Query("UPDATE goals SET savedCents = savedCents + :cents WHERE id = :goalId") suspend fun updateGoalSavings(goalId: String, cents: Long)
    @Transaction suspend fun addGoalSavings(goalId: String, cents: Long) {
        val goal = requireNotNull(goalById(goalId)) { "Savings goal no longer exists" }
        Math.addExact(goal.savedCents, cents)
        updateGoalSavings(goalId, cents)
    }
    @Query("SELECT * FROM projects ORDER BY name") suspend fun allProjects(): List<Project>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccount(account: Account)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProject(project: Project)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoal(goal: Goal)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEntry(entry: LedgerEntry)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteEntry(id: String)
    @Query("DELETE FROM transactions") suspend fun clearEntries()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM projects") suspend fun clearProjects()
    @Transaction suspend fun replaceAll(a: List<Account>, p: List<Project>, g: List<Goal>, e: List<LedgerEntry>) {
        clearEntries(); clearGoals(); clearProjects(); clearAccounts()
        a.forEach { putAccount(it) }; p.forEach { putProject(it) }; g.forEach { putGoal(it) }; e.forEach { putEntry(it) }
    }
}

@Database(entities = [Account::class, Project::class, Goal::class, LedgerEntry::class], version = 1, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() { abstract fun financeDao(): FinanceDao
    companion object {
        @Volatile private var INSTANCE: FinanceDatabase? = null
        fun get(context: android.content.Context) = INSTANCE ?: synchronized(this) { INSTANCE ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "luma-finance.db").build().also { INSTANCE = it } }
    }
}

@Serializable data class Backup(val version: Int = 1, val accounts: List<Account>, val projects: List<Project>, val goals: List<Goal>, val transactions: List<LedgerEntry>)
