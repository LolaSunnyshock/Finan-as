package com.example.ui.finance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.util.MonthDateHelper
import com.example.data.util.UserNameFormatter
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyPrimary
import java.util.Locale

val DEFAULT_CATEGORIES = listOf(
    "Mercado", "Contas", "Salário", "Investimento", "Lazer", "Transporte", "Saúde", "Educação", "Moradia", "Outros"
)

@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()

    var isDashboardFlipped by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionItem?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<TransactionItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val flipRotation by animateFloatAsState(
        targetValue = if (isDashboardFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "finance_page_flip"
    )

    BackHandler(enabled = isDashboardFlipped) {
        isDashboardFlipped = false
    }

    val isFilterActive = selectedCategory != "Todas" || selectedTypeFilter != "ALL"
    val periodLabel = remember(uiState.selectedMonth) {
        uiState.selectedMonth?.let { MonthDateHelper.formatMonthYearLabel(it) } ?: "Todos os meses"
    }

    LaunchedEffect(operationStatus) {
        operationStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val filteredTransactions = uiState.transactions.filter { tx ->
        val matchesCategory = selectedCategory == "Todas" || tx.category == selectedCategory
        val matchesType = when (selectedTypeFilter) {
            "INCOME" -> tx.type == TransactionType.INCOME.name
            "EXPENSE" -> tx.type == TransactionType.EXPENSE.name
            else -> true
        }
        matchesCategory && matchesType
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (flipRotation <= 90f) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = NavyPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_transaction_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar Transação")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .graphicsLayer {
                    rotationY = flipRotation
                    cameraDistance = 12f * density
                }
        ) {
            if (flipRotation <= 90f) {
                // FRONT PAGE: Extrato & Transações
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // Dashboard Summary Header with Page Flip Button
                    item {
                        FinanceDashboardHeader(
                            periodLabel = periodLabel,
                            currentBalance = uiState.currentBalance,
                            totalIncome = uiState.totalIncome,
                            totalExpense = uiState.totalExpense,
                            onFlipToDashboard = { isDashboardFlipped = true }
                        )
                    }

                    // Month Selector Bar (< Mês Atual > | Todos)
                    item {
                        MonthSelectorBar(
                            selectedMonth = uiState.selectedMonth,
                            onPrevMonth = { viewModel.shiftSelectedMonth(-1) },
                            onNextMonth = { viewModel.shiftSelectedMonth(+1) },
                            onSelectCurrentMonth = { viewModel.selectMonth(MonthDateHelper.currentYearMonth()) },
                            onToggleAllMonths = {
                                if (uiState.selectedMonth == null) {
                                    viewModel.selectMonth(MonthDateHelper.currentYearMonth())
                                } else {
                                    viewModel.selectMonth(null)
                                }
                            }
                        )
                    }

                    // Section Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Transações",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${filteredTransactions.size} registros",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                IconButton(
                                    onClick = { showFilterDialog = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("filter_options_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Opções de Filtro",
                                        tint = if (isFilterActive) NavyPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (isFilterActive) {
                                    IconButton(
                                        onClick = {
                                            viewModel.setCategoryFilter("Todas")
                                            viewModel.setTypeFilter("ALL")
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Limpar Filtros",
                                            tint = CoralRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Loading or Empty State
                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NavyPrimary)
                            }
                        }
                    } else if (filteredTransactions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = when {
                                            isFilterActive -> "Nenhuma transação no filtro aplicado"
                                            uiState.selectedMonth != null -> "Sem registros em ${MonthDateHelper.formatShortMonthYear(uiState.selectedMonth!!)}"
                                            else -> "Nenhuma transação encontrada"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Toque no botão '+' abaixo para cadastrar transações à vista ou parceladas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    if (uiState.selectedMonth != null && uiState.allTransactionsCount > 0) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        TextButton(onClick = { viewModel.selectMonth(null) }) {
                                            Text("Ver todos os meses (${uiState.allTransactionsCount})", fontWeight = FontWeight.Bold, color = BrandBlue)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredTransactions,
                            key = { it.id.ifBlank { it.hashCode().toString() } }
                        ) { tx ->
                            TransactionCardItem(
                                transaction = tx,
                                onEditClick = { transactionToEdit = tx },
                                onDeleteClick = { transactionToDelete = tx }
                            )
                        }
                    }
                }
            } else {
                // BACK PAGE: Round Dashboards & Investment Base Comparison (Rotated 180° back so text reads cleanly)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    FinanceDashboardView(
                        transactions = uiState.transactions,
                        totalIncome = uiState.totalIncome,
                        investmentBase = uiState.investmentBase,
                        periodLabel = periodLabel,
                        onUpdateInvestmentBase = { strategy, notes, monthlyBase, targets ->
                            viewModel.updateInvestmentBase(strategy, notes, monthlyBase, targets)
                        },
                        onResetInvestmentBase = { viewModel.resetInvestmentBaseToDefault() },
                        onFlipBack = { isDashboardFlipped = false }
                    )
                }
            }
        }
    }

    // Add Transaction Dialog (with Month picker, Conta Fixa & Installments)
    if (showAddDialog) {
        TransactionFormDialog(
            editingTransaction = null,
            viewingYearMonth = uiState.selectedMonth,
            defaultYearMonth = uiState.selectedMonth ?: MonthDateHelper.currentYearMonth(),
            onDismiss = { showAddDialog = false },
            onSave = { desc, amount, type, category, date, installments, amountIsTotal, isFixed, fixedEndMonth ->
                viewModel.addTransaction(
                    description = desc,
                    amount = amount,
                    type = type,
                    category = category,
                    date = date,
                    installments = installments,
                    amountIsTotal = amountIsTotal,
                    isFixed = isFixed,
                    fixedEndMonth = fixedEndMonth
                ) { success ->
                    if (success) showAddDialog = false
                }
            }
        )
    }

    // Edit Transaction Dialog (always passes the raw/origin transaction so fixed bill startDate is preserved)
    if (transactionToEdit != null) {
        val rawEditing = uiState.allRawTransactions.find { it.id == transactionToEdit!!.id } ?: transactionToEdit!!
        val seriesCount = remember(rawEditing, uiState.allRawTransactions) {
            MonthDateHelper.findInstallmentSeries(rawEditing, uiState.allRawTransactions).size
        }
        TransactionFormDialog(
            editingTransaction = rawEditing,
            viewingYearMonth = uiState.selectedMonth,
            defaultYearMonth = MonthDateHelper.extractYearMonth(rawEditing.date),
            installmentSeriesCount = seriesCount,
            onDeleteInstallmentPlan = {
                viewModel.deleteInstallmentPlan(rawEditing) { success ->
                    if (success) transactionToEdit = null
                }
            },
            onDismiss = { transactionToEdit = null },
            onSave = { desc, amount, type, category, date, installments, amountIsTotal, isFixed, fixedEndMonth ->
                viewModel.updateTransaction(
                    original = rawEditing,
                    description = desc,
                    amount = amount,
                    type = type,
                    category = category,
                    date = date,
                    installments = installments,
                    amountIsTotal = amountIsTotal,
                    isFixed = isFixed,
                    fixedEndMonth = fixedEndMonth
                ) { success ->
                    if (success) transactionToEdit = null
                }
            }
        )
    }

    // Filter Dialog
    if (showFilterDialog) {
        FinanceFilterDialog(
            selectedCategory = selectedCategory,
            selectedType = selectedTypeFilter,
            onDismiss = { showFilterDialog = false },
            onApply = { cat, type ->
                viewModel.setCategoryFilter(cat)
                viewModel.setTypeFilter(type)
                showFilterDialog = false
            },
            onReset = {
                viewModel.setCategoryFilter("Todas")
                viewModel.setTypeFilter("ALL")
                showFilterDialog = false
            }
        )
    }

    // Confirm Delete Dialog (supports Excluir Parcelamento Completo vs Excluir Apenas Este Mês)
    if (transactionToDelete != null) {
        val tx = transactionToDelete!!
        val installmentSeries = remember(tx, uiState.allRawTransactions) {
            MonthDateHelper.findInstallmentSeries(tx, uiState.allRawTransactions)
        }
        val hasFullPlan = installmentSeries.size > 1

        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = {
                Text(
                    text = if (hasFullPlan) "Excluir Parcelamento" else "Excluir Transação",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = when {
                            hasFullPlan -> "\"${tx.description}\" faz parte de um parcelamento (${installmentSeries.size} parcelas em diferentes meses). Como deseja excluir?"
                            tx.isFixed -> "\"${tx.description}\" é uma conta fixa. Excluir removerá de todos os meses.\n\nDica: para encerrar apenas a partir de um mês específico, edite e desmarque a opção Conta Fixa."
                            else -> "Deseja realmente excluir \"${tx.description}\" (${FinanceViewModel.formatBrl(tx.amount)})?"
                        }
                    )
                    if (hasFullPlan) {
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.deleteInstallmentPlan(tx)
                                transactionToDelete = null
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = CoralRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_all_installments_button")
                        ) {
                            Text(
                                text = "Excluir parcelamento completo (${installmentSeries.size} meses)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                viewModel.deleteTransaction(tx.id)
                                transactionToDelete = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_delete_transaction_button")
                        ) {
                            Text(
                                text = "Excluir apenas este mês",
                                color = CoralRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!hasFullPlan) {
                    TextButton(
                        onClick = {
                            viewModel.deleteTransaction(tx.id)
                            transactionToDelete = null
                        },
                        modifier = Modifier.testTag("confirm_delete_transaction_button")
                    ) {
                        Text("Excluir", color = CoralRed, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun MonthSelectorBar(
    selectedMonth: String?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectCurrentMonth: () -> Unit,
    onToggleAllMonths: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPrevMonth,
                    modifier = Modifier.testTag("prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Mês anterior",
                        tint = NavyPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSelectCurrentMonth)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedMonth?.let { MonthDateHelper.formatMonthYearLabel(it) } ?: "Todos os meses",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = NavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.testTag("next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Próximo mês",
                        tint = NavyPrimary
                    )
                }
            }
        }

        FilterChip(
            selected = selectedMonth == null,
            onClick = onToggleAllMonths,
            label = {
                Text(
                    text = "Todos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NavyPrimary,
                selectedLabelColor = Color.White
            ),
            modifier = Modifier.testTag("toggle_all_months_chip")
        )
    }
}

@Composable
private fun FinanceDashboardHeader(
    periodLabel: String,
    currentBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    onFlipToDashboard: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saldo • $periodLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Botão para virar a página na mesma tela
                Surface(
                    onClick = onFlipToDashboard,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.testTag("flip_to_dashboard_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DonutLarge,
                            contentDescription = "Ver Dashboard Redondo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Dashboard & Metas",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = FinanceViewModel.formatBrl(currentBalance),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (currentBalance >= 0) Color.White else CoralRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Income Card
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Receitas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = FinanceViewModel.formatBrl(totalIncome),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Expense Card
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CoralRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = CoralRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Despesas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = FinanceViewModel.formatBrl(totalExpense),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCardItem(
    transaction: TransactionItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isIncome = transaction.type == TransactionType.INCOME.name
    val isInvestment = transaction.category.equals("Investimento", ignoreCase = true)
    val icon = getCategoryIcon(transaction.category)
    val color = when {
        isIncome -> EmeraldGreen
        isInvestment -> Color(0xFF10B981)
        else -> CoralRed
    }
    val txYearMonth = MonthDateHelper.extractYearMonth(transaction.date)
    val shortMonthTag = MonthDateHelper.formatShortMonthYear(txYearMonth)
    val displayDate = MonthDateHelper.formatDisplayDate(transaction.date)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onEditClick)
            .testTag("transaction_item_${transaction.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BrandBlue.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = shortMonthTag,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = BrandBlue,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            maxLines = 1
                        )
                    }
                    val firstName = UserNameFormatter.sanitizeStoredName(transaction.userName, "")
                    if (firstName.isNotBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = firstName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NavyPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                    if (transaction.isFixed) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldGreen.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Conta Fixa",
                                    tint = EmeraldDark,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (transaction.fixedEndMonth.isNotBlank()) {
                                        "Fixa até ${MonthDateHelper.formatShortMonthYear(transaction.fixedEndMonth)}"
                                    } else {
                                        "Conta Fixa"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldDark,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncome) "+" else "-"}${FinanceViewModel.formatBrl(transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("edit_transaction_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar transação",
                            tint = NavyPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("delete_transaction_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir transação",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase(Locale.ROOT)) {
        "mercado", "supermercado", "compras", "alimentação" -> Icons.Default.ShoppingCart
        "contas", "luz", "água", "internet", "boletos" -> Icons.Default.Receipt
        "salário", "trabalho", "freelance", "bônus" -> Icons.Default.Payments
        "investimento", "investimentos", "ações", "tesouro", "poupança" -> Icons.AutoMirrored.Filled.TrendingUp
        "lazer", "jogos", "viagem", "cinema" -> Icons.Default.SportsEsports
        "transporte", "combustível", "uber", "carro" -> Icons.Default.DirectionsCar
        "saúde", "farmácia", "médico" -> Icons.Default.LocalHospital
        "educação", "cursos", "livros" -> Icons.Default.School
        "moradia", "aluguel", "condomínio" -> Icons.Default.Home
        else -> Icons.Default.Category
    }
}
