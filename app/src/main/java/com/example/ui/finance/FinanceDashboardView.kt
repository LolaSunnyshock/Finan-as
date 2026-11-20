package com.example.ui.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CoralDark
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyPrimary
import java.util.Locale
import kotlin.math.roundToInt

data class DonutSlice(
    val label: String,
    val amount: Double,
    val percent: Float,
    val color: Color
)

@Composable
fun FinanceDashboardView(
    transactions: List<TransactionItem>,
    totalIncome: Double,
    investmentBase: InvestmentBaseConfig,
    periodLabel: String = "Todos os meses",
    onUpdateInvestmentBase: (strategyName: String, notes: String, monthlyBase: Double, targets: Map<String, Float>) -> Unit,
    onResetInvestmentBase: () -> Unit,
    onFlipBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showInvestmentBaseSection by remember { mutableStateOf(true) }
    var showEditBaseDialog by remember { mutableStateOf(false) }

    // Actual allocations: all EXPENSE transactions + any transaction categorized as "Investimento"
    val relevantTransactions = remember(transactions) {
        transactions.filter {
            it.type == TransactionType.EXPENSE.name || it.category.equals("Investimento", ignoreCase = true)
        }
    }
    val totalAllocated = remember(relevantTransactions) {
        relevantTransactions.sumOf { it.amount }
    }

    val actualByCategory = remember(relevantTransactions) {
        relevantTransactions
            .groupBy { it.category.ifBlank { "Outros" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val actualSlices = remember(actualByCategory, totalAllocated) {
        if (totalAllocated <= 0.0) {
            emptyList()
        } else {
            actualByCategory.entries
                .sortedByDescending { it.value }
                .map { (cat, amt) ->
                    DonutSlice(
                        label = cat,
                        amount = amt,
                        percent = ((amt / totalAllocated) * 100.0).toFloat(),
                        color = getCategoryChartColor(cat)
                    )
                }
        }
    }

    val targetTotal = investmentBase.totalTargetPercent.coerceAtLeast(1f)
    val effectiveMonthlyBase = remember(investmentBase.monthlyBaseAmount, totalIncome, totalAllocated) {
        when {
            investmentBase.monthlyBaseAmount > 0.0 -> investmentBase.monthlyBaseAmount
            totalIncome > 0.0 -> totalIncome
            else -> totalAllocated
        }
    }

    val targetSlices = remember(investmentBase.categoryTargets, targetTotal, effectiveMonthlyBase) {
        investmentBase.categoryTargets.entries
            .filter { it.value > 0f }
            .sortedByDescending { it.value }
            .map { (cat, pct) ->
                DonutSlice(
                    label = cat,
                    amount = effectiveMonthlyBase * (pct / 100.0),
                    percent = pct,
                    color = getCategoryChartColor(cat)
                )
            }
    }

    // Comparison insights: What to stop spending vs What to start investing
    val comparisonRows = remember(actualByCategory, totalAllocated, investmentBase.categoryTargets, effectiveMonthlyBase) {
        val allCategories = (actualByCategory.keys + investmentBase.categoryTargets.keys)
            .distinct()
            .filter { !it.equals("Salário", ignoreCase = true) }

        allCategories.map { cat ->
            val actualAmt = actualByCategory[cat] ?: 0.0
            val actualPct = if (totalAllocated > 0.0) ((actualAmt / totalAllocated) * 100.0).toFloat() else 0f
            val targetPct = investmentBase.categoryTargets[cat] ?: 0f
            val targetAmt = effectiveMonthlyBase * (targetPct / 100.0)
            ComparisonItem(
                category = cat,
                actualAmount = actualAmt,
                actualPercent = actualPct,
                targetAmount = targetAmt,
                targetPercent = targetPct,
                color = getCategoryChartColor(cat)
            )
        }.sortedByDescending { kotlin.math.max(it.actualPercent, it.targetPercent) }
    }

    val overspendingItems = remember(comparisonRows) {
        comparisonRows
            .filter { !it.category.equals("Investimento", ignoreCase = true) && (it.actualPercent - it.targetPercent) > 1.0f }
            .sortedByDescending { it.actualPercent - it.targetPercent }
    }

    val underInvestedItems = remember(comparisonRows) {
        comparisonRows
            .filter {
                (it.category.equals("Investimento", ignoreCase = true) || it.category.equals("Educação", ignoreCase = true) || it.category.equals("Saúde", ignoreCase = true)) &&
                (it.targetPercent - it.actualPercent) > 1.0f
            }
            .sortedWith(
                compareByDescending<ComparisonItem> { it.category.equals("Investimento", ignoreCase = true) }
                    .thenByDescending { it.targetPercent - it.actualPercent }
            )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Flip Back Header Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DonutLarge,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Dashboard Financeiro",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "$periodLabel • Gráfico redondo & metas",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Surface(
                        onClick = onFlipBack,
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.testTag("flip_to_extract_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = "Voltar para Extrato",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Extrato",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // DASHBOARD 1: Atual (Gráfico Redondo de Categorias Usadas)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Distribuição Atual por Categoria",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${actualSlices.size} categorias",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = BrandBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (actualSlices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Adicione despesas ou aportes na categoria Investimento no botão '+' do extrato para visualizar o gráfico redondo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        DonutChart(
                            slices = actualSlices,
                            centerTitle = FinanceViewModel.formatBrl(totalAllocated),
                            centerSubtitle = "Total Aplicado",
                            modifier = Modifier.size(196.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        actualSlices.forEach { slice ->
                            SliceLegendRow(
                                slice = slice,
                                showAmount = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // Option Switch to display Second Dashboard (Investment Base) below
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = EmeraldDark,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Base de Investimento & Metas",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Exibir 2º dashboard editável e comparativo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = showInvestmentBaseSection,
                        onCheckedChange = { showInvestmentBaseSection = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldDark),
                        modifier = Modifier.testTag("toggle_investment_base_switch")
                    )
                }
            }
        }

        // DASHBOARD 2 & COMPARISON (Below)
        if (showInvestmentBaseSection) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Base Ideal & Investimento",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = investmentBase.strategyName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = { showEditBaseDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("edit_investment_base_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar Base",
                                    modifier = Modifier.size(16.dp),
                                    tint = NavyPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editar % e Info", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Editable Investment Info Note Box
                        if (investmentBase.investmentNotes.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldGreen.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = EmeraldDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Informações da Base de Investimento",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = EmeraldDark
                                        )
                                        Text(
                                            text = investmentBase.investmentNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (effectiveMonthlyBase > 0.0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Referência mensal: ${FinanceViewModel.formatBrl(effectiveMonthlyBase)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = NavyPrimary
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Round Donut Chart #2 (Target Base)
                        val totalPctInt = investmentBase.totalTargetPercent.roundToInt()
                        DonutChart(
                            slices = targetSlices,
                            centerTitle = "$totalPctInt%",
                            centerSubtitle = if (totalPctInt == 100) "Base Completa" else "Soma Atual",
                            modifier = Modifier.size(196.dp)
                        )

                        if (totalPctInt != 100) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Dica: A soma das metas está em $totalPctInt%. Edite para fechar 100%.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        targetSlices.forEach { slice ->
                            SliceLegendRow(
                                slice = slice,
                                showAmount = effectiveMonthlyBase > 0.0
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // COMPARISON & ACTIONABLE INSIGHTS CARD
            item {
                ComparisonInsightsCard(
                    overspendingItems = overspendingItems,
                    underInvestedItems = underInvestedItems,
                    comparisonRows = comparisonRows,
                    investmentNotes = investmentBase.investmentNotes,
                    totalAllocated = totalAllocated
                )
            }
        }
    }

    if (showEditBaseDialog) {
        EditInvestmentBaseDialog(
            currentConfig = investmentBase,
            onDismiss = { showEditBaseDialog = false },
            onReset = {
                onResetInvestmentBase()
                showEditBaseDialog = false
            },
            onSave = { strategy, notes, monthlyBase, targets ->
                onUpdateInvestmentBase(strategy, notes, monthlyBase, targets)
                showEditBaseDialog = false
            }
        )
    }
}

@Composable
private fun DonutChart(
    slices: List<DonutSlice>,
    centerTitle: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier
) {
    val totalPercent = slices.sumOf { it.percent.toDouble() }.toFloat().coerceAtLeast(1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 32.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            if (slices.isEmpty()) {
                drawArc(
                    color = Color(0xFFE2E8F0),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                var startAngle = -90f
                val gapDegrees = if (slices.size > 1) 2.2f else 0f
                slices.forEach { slice ->
                    val sweep = ((slice.percent / totalPercent) * 360f) - gapDegrees
                    if (sweep > 0.5f) {
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle + (gapDegrees / 2f),
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += (slice.percent / totalPercent) * 360f
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {
            Text(
                text = centerSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SliceLegendRow(
    slice: DonutSlice,
    showAmount: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(slice.color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = slice.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showAmount) {
                Text(
                    text = FinanceViewModel.formatBrl(slice.amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = slice.color.copy(alpha = 0.14f)
            ) {
                Text(
                    text = String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", slice.percent),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = slice.color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private data class ComparisonItem(
    val category: String,
    val actualAmount: Double,
    val actualPercent: Float,
    val targetAmount: Double,
    val targetPercent: Float,
    val color: Color
)

@Composable
private fun ComparisonInsightsCard(
    overspendingItems: List<ComparisonItem>,
    underInvestedItems: List<ComparisonItem>,
    comparisonRows: List<ComparisonItem>,
    investmentNotes: String,
    totalAllocated: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = NavyPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Comparativo & Plano de Ação",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 1. Onde Parar / Reduzir Gastos
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = CoralRed.copy(alpha = 0.08f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CoralDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "O que você precisa parar / reduzir de gastar",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = CoralDark
                        )
                    }

                    if (totalAllocated <= 0.0) {
                        Text(
                            text = "Cadastre seus gastos no extrato para descobrir onde há excesso em relação à sua base.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (overspendingItems.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ótimo controle! Nenhuma categoria de gasto ultrapassou sua porcentagem ideal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDark
                            )
                        }
                    } else {
                        overspendingItems.forEach { item ->
                            val excessPct = item.actualPercent - item.targetPercent
                            val excessBrl = (totalAllocated * (excessPct / 100.0)).coerceAtLeast(0.0)
                            Text(
                                text = "• ${item.category}: está em ${formatPct(item.actualPercent)} vs meta de ${formatPct(item.targetPercent)} — reduza ${formatPct(excessPct)} (${FinanceViewModel.formatBrl(excessBrl)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 2. Onde Começar / Aumentar Investimentos
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = EmeraldGreen.copy(alpha = 0.1f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = EmeraldDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "O que você precisa começar / aumentar a investir",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldDark
                        )
                    }

                    if (underInvestedItems.isEmpty()) {
                        Text(
                            text = "Você já está atingindo ou superando sua meta de Investimento configurada!",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = EmeraldDark
                        )
                    } else {
                        underInvestedItems.forEach { item ->
                            val missingPct = item.targetPercent - item.actualPercent
                            val missingBrl = (item.targetAmount - item.actualAmount).coerceAtLeast(0.0)
                            val extraMsg = if (missingBrl > 0.0) " (falta ~${FinanceViewModel.formatBrl(missingBrl)})" else ""
                            Text(
                                text = "• ${item.category}: atual em ${formatPct(item.actualPercent)} vs meta de ${formatPct(item.targetPercent)} — aumente +${formatPct(missingPct)}$extraMsg",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (investmentNotes.isNotBlank()) {
                        HorizontalDivider(color = EmeraldDark.copy(alpha = 0.2f))
                        Text(
                            text = "Sugestão da sua base: $investmentNotes",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. Visual Bar Comparison (Atual vs Base Ideal)
            Text(
                text = "Comparação por Categoria (Atual vs Base)",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            comparisonRows.filter { it.actualPercent > 0f || it.targetPercent > 0f }.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = row.category,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Atual ${formatPct(row.actualPercent)}  |  Meta ${formatPct(row.targetPercent)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Dual mini bars
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((row.actualPercent / 100f).coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(row.color)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((row.targetPercent / 100f).coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(row.color.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditInvestmentBaseDialog(
    currentConfig: InvestmentBaseConfig,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSave: (strategyName: String, notes: String, monthlyBase: Double, targets: Map<String, Float>) -> Unit
) {
    var strategyName by remember { mutableStateOf(currentConfig.strategyName) }
    var investmentNotes by remember { mutableStateOf(currentConfig.investmentNotes) }
    var monthlyBaseText by remember {
        mutableStateOf(
            if (currentConfig.monthlyBaseAmount > 0.0) currentConfig.monthlyBaseAmount.toInt().toString() else ""
        )
    }

    val editableCategories = remember {
        DEFAULT_CATEGORIES.filter { !it.equals("Salário", ignoreCase = true) }
    }
    val targetsState = remember {
        mutableStateMapOf<String, Float>().apply {
            editableCategories.forEach { cat ->
                put(cat, currentConfig.categoryTargets[cat] ?: 0f)
            }
        }
    }

    val totalPercent = targetsState.values.sum().roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Editar Base & Metas", fontWeight = FontWeight.Bold)
                IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Restaurar padrão",
                        tint = CoralRed
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = strategyName,
                    onValueChange = { strategyName = it },
                    label = { Text("Nome da Base (ex: Foco Investimento)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = investmentNotes,
                    onValueChange = { investmentNotes = it },
                    label = { Text("Informações / Onde investir") },
                    placeholder = { Text("Ex: Tesouro Selic, CDB 100% CDI, FIIs...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = monthlyBaseText,
                    onValueChange = { input ->
                        val clean = input.replace(',', '.')
                        if (clean.isEmpty() || clean.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                            monthlyBaseText = input
                        }
                    },
                    label = { Text("Renda Base Mensal R$ (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            targetsState["Investimento"] = 20f
                            targetsState["Moradia"] = 25f
                            targetsState["Mercado"] = 15f
                            targetsState["Contas"] = 15f
                            targetsState["Transporte"] = 10f
                            targetsState["Saúde"] = 5f
                            targetsState["Lazer"] = 5f
                            targetsState["Educação"] = 5f
                            targetsState["Outros"] = 0f
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Base 50/30/20", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            targetsState["Investimento"] = 35f
                            targetsState["Moradia"] = 20f
                            targetsState["Mercado"] = 12f
                            targetsState["Contas"] = 12f
                            targetsState["Transporte"] = 8f
                            targetsState["Saúde"] = 5f
                            targetsState["Lazer"] = 4f
                            targetsState["Educação"] = 4f
                            targetsState["Outros"] = 0f
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Foco Investir 35%", fontSize = 11.sp)
                    }
                }

                // Total Percentage Indicator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (totalPercent == 100) EmeraldGreen.copy(alpha = 0.14f) else AmberGold.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Soma das porcentagens:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$totalPercent%",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (totalPercent == 100) EmeraldDark else Color(0xFFB45309)
                        )
                    }
                }

                // Category percentage sliders
                editableCategories.forEach { cat ->
                    val currentVal = targetsState[cat] ?: 0f
                    val catColor = getCategoryChartColor(cat)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { targetsState[cat] = (currentVal - 1f).coerceAtLeast(0f) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = "${currentVal.roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = catColor,
                                    modifier = Modifier.width(38.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { targetsState[cat] = (currentVal + 1f).coerceAtMost(100f) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Slider(
                            value = currentVal,
                            onValueChange = { targetsState[cat] = it.roundToInt().toFloat() },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = catColor,
                                activeTrackColor = catColor
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedBase = monthlyBaseText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onSave(strategyName, investmentNotes, parsedBase, targetsState.toMap())
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Salvar Base", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun getCategoryChartColor(category: String): Color {
    return when (category.lowercase(Locale.ROOT)) {
        "investimento", "investimentos" -> Color(0xFF10B981) // Emerald
        "moradia" -> Color(0xFF3B82F6) // Blue
        "mercado" -> Color(0xFFF59E0B) // Amber
        "contas" -> Color(0xFF8B5CF6) // Violet
        "transporte" -> Color(0xFF06B6D4) // Cyan
        "lazer" -> Color(0xFFEF4444) // Coral/Red
        "saúde" -> Color(0xFF14B8A6) // Teal
        "educação" -> Color(0xFF6366F1) // Indigo
        "salário" -> Color(0xFF22C55E) // Green
        else -> Color(0xFF64748B) // Slate
    }
}

private fun formatPct(value: Float): String {
    return String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", value)
}
