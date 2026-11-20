package com.example

import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.repository.UpdateRepository
import com.example.data.util.MonthDateHelper
import com.example.data.util.UserNameFormatter
import com.example.ui.finance.DEFAULT_CATEGORIES
import com.example.ui.finance.DEFAULT_CATEGORY_TARGETS
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun userNameFormatter_extractsFirstNameInsteadOfEmail() {
    assertEquals("Guilherme", UserNameFormatter.extractFirstName("Guilherme Arcini", "guilherme@gmail.com"))
    assertEquals("Guilherme", UserNameFormatter.extractFirstName("guilherme.arcini@gmail.com"))
    assertEquals("Maria", UserNameFormatter.extractFirstName(null, "maria.silva@test.com"))
    assertEquals("Ana", UserNameFormatter.sanitizeStoredName("Ana Paula"))
    assertEquals("Carlos", UserNameFormatter.sanitizeStoredName("carlos@empresa.com"))
  }

  @Test
  fun financeCategories_includeInvestimentoAndTargetsSum100() {
    assertTrue(DEFAULT_CATEGORIES.contains("Investimento"))
    assertTrue(DEFAULT_CATEGORY_TARGETS.containsKey("Investimento"))
    assertEquals(100f, DEFAULT_CATEGORY_TARGETS.values.sum(), 0.01f)
  }

  @Test
  fun monthDateHelper_shiftsMonthsAndClampsDays() {
    assertEquals("2026-12", MonthDateHelper.shiftYearMonth("2026-11", 1))
    assertEquals("2027-01", MonthDateHelper.shiftYearMonth("2026-11", 2))
    assertEquals("2025-12", MonthDateHelper.shiftYearMonth("2026-01", -1))

    assertEquals("2026-02-28", MonthDateHelper.buildDateForMonth("2026-02", 31))
    assertEquals("2024-02-29", MonthDateHelper.buildDateForMonth("2024-02", 31))
    assertEquals("2026-11-30", MonthDateHelper.buildDateForMonth("2026-11", 31))
  }

  @Test
  fun monthDateHelper_splitsInstallmentsAccurately() {
    val splitTotal = MonthDateHelper.splitInstallmentAmounts(100.0, 3, amountIsTotal = true)
    assertEquals(3, splitTotal.size)
    assertEquals(33.34, splitTotal[0], 0.001)
    assertEquals(33.33, splitTotal[1], 0.001)
    assertEquals(33.33, splitTotal[2], 0.001)
    assertEquals(100.0, splitTotal.sum(), 0.001)

    val perParcel = MonthDateHelper.splitInstallmentAmounts(150.5, 4, amountIsTotal = false)
    assertEquals(4, perParcel.size)
    assertTrue(perParcel.all { it == 150.5 })

    assertEquals("Celular (2/10)", MonthDateHelper.buildInstallmentDescription("Celular", 2, 10))
    assertEquals("Celular", MonthDateHelper.stripInstallmentSuffix("Celular (1/3)"))
    assertEquals("Celular (2/6)", MonthDateHelper.buildInstallmentDescription("Celular (1/3)", 2, 6))
    assertTrue(MonthDateHelper.buildInstallmentDescription("A".repeat(200), 12, 12).length <= 150)
  }

  @Test
  fun monthDateHelper_projectsFixedTransactionsWithinBounds() {
    val fixedInternet = TransactionItem(
      id = "fix1",
      description = "Internet Fibra",
      amount = 120.0,
      type = TransactionType.EXPENSE.name,
      category = "Contas",
      date = "2026-11-31",
      isFixed = true,
      fixedEndMonth = "2027-02"
    )

    assertFalse(MonthDateHelper.isFixedActiveInMonth(fixedInternet, "2026-10"))
    assertTrue(MonthDateHelper.isFixedActiveInMonth(fixedInternet, "2026-11"))
    assertTrue(MonthDateHelper.isFixedActiveInMonth(fixedInternet, "2027-01"))
    assertTrue(MonthDateHelper.isFixedActiveInMonth(fixedInternet, "2027-02"))
    assertFalse(MonthDateHelper.isFixedActiveInMonth(fixedInternet, "2027-03"))

    val febProjected = MonthDateHelper.projectFixedTransactionForMonth(fixedInternet, "2027-02")
    assertEquals("2027-02-28", febProjected.date)
    assertEquals("fix1", febProjected.id)
  }

  @Test
  fun monthDateHelper_findsInstallmentSeriesByGroupIdAndLegacySuffix() {
    val groupSeries = listOf(
      TransactionItem(id = "a1", description = "TV (1/3)", type = "EXPENSE", category = "Lazer", installmentGroupId = "grp_tv"),
      TransactionItem(id = "a2", description = "TV (2/3)", type = "EXPENSE", category = "Lazer", installmentGroupId = "grp_tv"),
      TransactionItem(id = "a3", description = "TV (3/3)", type = "EXPENSE", category = "Lazer", installmentGroupId = "grp_tv"),
      TransactionItem(id = "other", description = "TV (1/3)", type = "EXPENSE", category = "Lazer", installmentGroupId = "grp_other")
    )
    val foundByGroup = MonthDateHelper.findInstallmentSeries(groupSeries[1], groupSeries)
    assertEquals(listOf("a1", "a2", "a3"), foundByGroup.map { it.id })

    val legacySeries = listOf(
      TransactionItem(id = "b1", description = "Curso (1/2)", type = "EXPENSE", category = "Educação"),
      TransactionItem(id = "b2", description = "Curso (2/2)", type = "EXPENSE", category = "Educação"),
      TransactionItem(id = "b3", description = "Curso (1/3)", type = "EXPENSE", category = "Educação")
    )
    val foundLegacy = MonthDateHelper.findInstallmentSeries(legacySeries[0], legacySeries)
    assertEquals(listOf("b1", "b2"), foundLegacy.map { it.id })
  }

  @Test
  fun updateRepository_normalizesGithubRepoAndBuildsUrls() {
    assertEquals("guilherme/financas", UpdateRepository.normalizeGithubRepo("guilherme/financas"))
    assertEquals("guilherme/financas", UpdateRepository.normalizeGithubRepo("https://github.com/guilherme/financas.git"))
    assertEquals("guilherme/financas", UpdateRepository.normalizeGithubRepo("https://github.com/guilherme/financas/tree/main"))
    assertEquals("", UpdateRepository.normalizeGithubRepo("apenas-uma-palavra"))

    assertEquals(
      "https://raw.githubusercontent.com/guilherme/financas/main/app-version.json",
      UpdateRepository.buildRawManifestUrl("guilherme/financas", "main")
    )
    assertEquals(
      "https://github.com/guilherme/financas/raw/main/app-debug.apk",
      UpdateRepository.buildApkDownloadUrl("guilherme/financas", "main", "app-debug.apk")
    )
    assertEquals(
      "https://api.github.com/repos/guilherme/financas/git/ref/heads/main",
      UpdateRepository.buildGitRefApiUrl("guilherme/financas", "main")
    )
    assertEquals(
      "https://api.github.com/repos/guilherme/financas/contents/app-version.json?ref=main",
      UpdateRepository.buildContentsApiManifestUrl("guilherme/financas", "main")
    )
    assertEquals(
      "aa218f56b14c9653891f9e74264a383fa43fefbd",
      UpdateRepository.parseGitRefSha("""{"ref":"refs/heads/main","object":{"sha":"aa218f56b14c9653891f9e74264a383fa43fefbd","type":"commit"}}""")
    )
  }
}
