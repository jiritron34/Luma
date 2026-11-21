package com.lumafinance.domain

import com.lumafinance.data.Account
import com.lumafinance.data.Goal
import com.lumafinance.data.LedgerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceEngineTest {
    private val checking = Account("checking", "Checking", openingBalanceCents = 100_000)
    private val savings = Account("savings", "Savings", openingBalanceCents = 25_000)
    private fun entry(id:String, cents:Long, type:String, date:LocalDate, account:String="checking", target:String?=null) = LedgerEntry(id,cents,type,"test",dateEpochDay=date.toEpochDay(),accountId=account,targetAccountId=target)

    @Test fun transfersMoveMoneyBetweenAccountsWithoutChangingCombinedBalance() {
        val result=FinanceEngine.accountBalances(listOf(checking,savings),listOf(entry("move",15_000,"transfer",LocalDate.of(2026,1,2),"checking","savings")))
        assertEquals(85_000L,result["checking"]);assertEquals(40_000L,result["savings"]);assertEquals(125_000L,result.values.sum())
    }
    @Test fun balancesAndCashFlowStaySeparatedByCurrency() {
        val euro=Account("euro","Euro cash",currency="EUR",openingBalanceCents=500)
        val entries=listOf(entry("usd",1000,"expense",LocalDate.of(2026,1,3),"checking"),entry("eur",250,"income",LocalDate.of(2026,1,4),"euro"))
        assertEquals(mapOf("USD" to 99_000L,"EUR" to 750L),FinanceEngine.balancesByCurrency(listOf(checking,euro),entries))
        assertEquals(1000L,FinanceEngine.summariesByCurrency(entries,listOf(checking,euro),LocalDate.of(2026,1,1).toEpochDay(),LocalDate.of(2026,2,1).toEpochDay())["USD"]?.expenses)
        assertEquals(250L,FinanceEngine.summariesByCurrency(entries,listOf(checking,euro),LocalDate.of(2026,1,1).toEpochDay(),LocalDate.of(2026,2,1).toEpochDay())["EUR"]?.income)
    }
    @Test fun crossCurrencyTransfersMustSpecifyAnExchangeAmount() {
        val euro=Account("euro","Euro cash",currency="EUR")
        val transfer=entry("fx",100,"transfer",LocalDate.of(2026,1,3),"checking","euro")
        assertThrows(IllegalArgumentException::class.java){FinanceEngine.accountBalances(listOf(checking,euro),listOf(transfer))}
    }
    @Test fun monthTotalsIncludeStartAndExcludeNextMonth() {
        val data=listOf(entry("first",10_000,"income",LocalDate.of(2026,1,1)),entry("last",2_500,"expense",LocalDate.of(2026,1,31)),entry("next",99_000,"expense",LocalDate.of(2026,2,1)))
        assertEquals(FinanceEngine.Summary(10_000,2_500,7_500),FinanceEngine.monthSummary(data,YearMonth.of(2026,1)))
    }
    @Test fun yearTotalsIgnoreAdjacentYears() {
        val data=listOf(entry("prev",50,"income",LocalDate.of(2025,12,31)),entry("curr",250,"expense",LocalDate.of(2026,5,5)),entry("next",99,"income",LocalDate.of(2027,1,1)))
        assertEquals(FinanceEngine.Summary(0,250,-250),FinanceEngine.yearSummary(data,2026))
    }
    @Test fun categorySpendAggregatesOnlyExpenses() {
        val data=listOf(entry("a",100,"expense",LocalDate.of(2026,1,2)),entry("b",250,"expense",LocalDate.of(2026,1,3)).copy(category="Food"),entry("c",999,"income",LocalDate.of(2026,1,4)))
        assertEquals(350L,FinanceEngine.categorySpend(data,LocalDate.of(2026,1,1).toEpochDay(),LocalDate.of(2026,2,1).toEpochDay())["test"])
        assertEquals(250L,FinanceEngine.categorySpend(data,LocalDate.of(2026,1,1).toEpochDay(),LocalDate.of(2026,2,1).toEpochDay())["Food"])
    }
    @Test fun goalProgressIsClampedAndZeroTargetIsSafe() {
        assertEquals(0.0,FinanceEngine.goalProgress(Goal("a","A",100,0)),0.0)
        assertEquals(1.0,FinanceEngine.goalProgress(Goal("b","B",100,150)),0.0)
        assertEquals(0.4,FinanceEngine.goalProgress(Goal("c","C",100,40)),0.0001)
    }
    @Test fun smallExpenseUsesIntegerCentsAndDetectsOverflow() {
        assertEquals(875L,FinanceEngine.SmallExpense(125,7).totalCents)
        assertThrows(ArithmeticException::class.java){FinanceEngine.SmallExpense(Long.MAX_VALUE,2).totalCents}
    }
}
