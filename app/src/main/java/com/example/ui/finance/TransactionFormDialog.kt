package com.example.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.util.MonthDateHelper
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CoralDark
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyPrimary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormDialog(
    editingTransaction: TransactionItem? = null,
    viewingYearMonth: String? = null,
    defaultYearMonth: String = MonthDateHelper.currentYearMonth(),
    installmentSeriesCount: Int = 1,
    onDeleteInstallmentPlan: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        description: String,
        amount: Double,
        type: TransactionType,
        category: String,
        date: String,
        installments: Int,
        amountIsTotal: Boolean,
        isFixed: Boolean,
        fixedEndMonth: String
    ) -> Unit
) {
    val isEditing = editingTransaction != null
    val originYearMonth = remember(editingTransaction, defaultYearMonth) {
        if (editingTransaction != null) {
            MonthDateHelper.extractYearMonth(editingTransaction.date)
        } else {
            defaultYearMonth
        }
    }
    val activeViewingYm = viewingYearMonth ?: originYearMonth
    val isEditingFutureFixedOccurrence =
        editingTransaction?.isFixed == true && activeViewingYm > originYearMonth

    var description by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.description ?: "")
    }
    var amountText by remember(editingTransaction) {
        mutableStateOf(
            if (editingTransaction != null) {
                String.format(Locale.US, "%.2f", editingTransaction.amount)
            } else {
                ""
            }
        )
    }
    var selectedType by remember(editingTransaction) {
        mutableStateOf(
            if (editingTransaction?.type == TransactionType.INCOME.name) {
                TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            }
        )
    }
    var selectedCategory by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.category?.ifBlank { DEFAULT_CATEGORIES[0] } ?: DEFAULT_CATEGORIES[0])
    }
    var selectedYearMonth by remember(editingTransaction, defaultYearMonth) {
        mutableStateOf(originYearMonth)
    }
    var dayText by remember(editingTransaction) {
        mutableStateOf(
            if (editingTransaction != null) {
                MonthDateHelper.extractDayOfMonth(editingTransaction.date).toString()
            } else {
                MonthDateHelper.currentDayOfMonth().toString()
            }
        )
    }

    // Fixed monthly bill (Conta Fixa) state
    var isFixedEnabled by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.isFixed == true)
    }
    // When unchecking Conta Fixa in a later month (e.g., Mar/2027), default keeps it up through Mar/2027 ("fica até o mês que desmarcado")
    var keepFixedThroughViewingMonth by remember(editingTransaction, activeViewingYm) {
        mutableStateOf(true)
    }

    // Installments (Parcelamento) state — available for both new and existing transactions
    var isInstallmentEnabled by remember(editingTransaction) { mutableStateOf(false) }
    var installmentsCount by remember(editingTransaction) { mutableIntStateOf(2) }
    var amountIsTotal by remember(editingTransaction) { mutableStateOf(true) }

    var hasError by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val parsedAmount = amountText.replace(',', '.').toDoubleOrNull()
    val effectiveInstallments = if (!isFixedEnabled && isInstallmentEnabled) installmentsCount.coerceIn(2, 48) else 1
    val parsedDay = (dayText.toIntOrNull() ?: MonthDateHelper.currentDayOfMonth()).coerceIn(1, 31)
    val computedDate = MonthDateHelper.buildDateForMonth(selectedYearMonth, parsedDay)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Editar Transação" else "Nova Transação",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type selector (Entrada / Saída)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("Entrada", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldDark,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_income_chip")
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        label = { Text("Saída", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralDark,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_expense_chip")
                    )
                }

                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição (ex: Aluguel / Internet / Celular)") },
                    singleLine = true,
                    isError = hasError && description.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_description_input")
                )

                // Amount field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        val clean = it.replace(',', '.')
                        if (clean.isEmpty() || clean.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                            amountText = it
                        }
                    },
                    label = {
                        Text(
                            when {
                                isFixedEnabled -> "Valor Mensal da Conta Fixa (R$)"
                                effectiveInstallments <= 1 -> "Valor (R$)"
                                amountIsTotal -> "Valor Total da Compra (R$)"
                                else -> "Valor de Cada Parcela (R$)"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = hasError && (parsedAmount == null || parsedAmount <= 0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input")
                )

                // Vertical Category Selector
                Column {
                    Text(
                        text = "Categoria",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { categoryDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = getCategoryIcon(selectedCategory),
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("transaction_category_dropdown")
                        )

                        ExposedDropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            DEFAULT_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = getCategoryIcon(cat),
                                            contentDescription = null,
                                            tint = if (selectedCategory == cat) NavyPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = if (selectedCategory == cat) {
                                        {
                                            RadioButton(
                                                selected = true,
                                                onClick = null,
                                                colors = RadioButtonDefaults.colors(selectedColor = NavyPrimary)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        selectedCategory = cat
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Month & Day Selector Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isFixedEnabled -> "Mês de início"
                                        effectiveInstallments > 1 -> "Mês da 1ª parcela"
                                        else -> "Mês da transação"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = {
                                    selectedYearMonth = MonthDateHelper.currentYearMonth()
                                },
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Mês atual", fontSize = 11.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { selectedYearMonth = MonthDateHelper.shiftYearMonth(selectedYearMonth, -1) },
                                modifier = Modifier.testTag("dialog_prev_month_button")
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Mês anterior", tint = NavyPrimary)
                            }

                            Text(
                                text = MonthDateHelper.formatMonthYearLabel(selectedYearMonth),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NavyPrimary
                            )

                            IconButton(
                                onClick = { selectedYearMonth = MonthDateHelper.shiftYearMonth(selectedYearMonth, +1) },
                                modifier = Modifier.testTag("dialog_next_month_button")
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Próximo mês", tint = NavyPrimary)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = dayText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(2)
                                    val num = digits.toIntOrNull()
                                    if (digits.isEmpty() || (num != null && num in 1..31)) {
                                        dayText = digits
                                    }
                                },
                                label = { Text("Dia") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(88.dp)
                            )

                            Text(
                                text = "Data: ${MonthDateHelper.formatDisplayDate(computedDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Fixed Monthly Bill (Conta Fixa) Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isFixedEnabled) EmeraldGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = if (isFixedEnabled) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Conta Fixa (mensal)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when {
                                            isFixedEnabled && !editingTransaction?.fixedEndMonth.isNullOrBlank() ->
                                                "Ativa até ${MonthDateHelper.formatShortMonthYear(editingTransaction!!.fixedEndMonth)}"
                                            isFixedEnabled -> "Repete automaticamente nos próximos meses"
                                            isEditingFutureFixedOccurrence -> "Desmarcada em ${MonthDateHelper.formatShortMonthYear(activeViewingYm)}"
                                            else -> "Não se repete automaticamente"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isFixedEnabled,
                                onCheckedChange = { checked ->
                                    isFixedEnabled = checked
                                    if (checked) {
                                        isInstallmentEnabled = false
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldDark),
                                modifier = Modifier.testTag("fixed_account_toggle_switch")
                            )
                        }

                        // When unchecking Conta Fixa inside a later month, clarify end behavior
                        if (!isFixedEnabled && isEditingFutureFixedOccurrence) {
                            val currentMonthLabel = MonthDateHelper.formatShortMonthYear(activeViewingYm)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = BrandBlue.copy(alpha = 0.08f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Como encerrar esta conta fixa (iniciada em ${MonthDateHelper.formatShortMonthYear(originYearMonth)}):",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = NavyPrimary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = keepFixedThroughViewingMonth,
                                            onClick = { keepFixedThroughViewingMonth = true },
                                            label = { Text("Fica até $currentMonthLabel", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = !keepFixedThroughViewingMonth,
                                            onClick = { keepFixedThroughViewingMonth = false },
                                            label = { Text("Tirar de $currentMonthLabel", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Text(
                                        text = if (keepFixedThroughViewingMonth) {
                                            "Permanece até $currentMonthLabel e não aparece nos meses seguintes."
                                        } else {
                                            val prevYm = MonthDateHelper.shiftYearMonth(activeViewingYm, -1)
                                            if (prevYm < originYearMonth) {
                                                "Ficará apenas no mês de origem (${MonthDateHelper.formatShortMonthYear(originYearMonth)})."
                                            } else {
                                                "Encerra antes de $currentMonthLabel (último mês: ${MonthDateHelper.formatShortMonthYear(prevYm)})."
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Installment (Parcelamento) Card — available for both new and existing transactions (when Conta Fixa is OFF)
                if (!isFixedEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isInstallmentEnabled) BrandBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = if (isInstallmentEnabled) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Parcelamento",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when {
                                                isInstallmentEnabled && isEditing -> "Converte em 1/${installmentsCount} e cria nos próximos meses"
                                                isInstallmentEnabled -> "Lança nos próximos meses automaticamente"
                                                else -> "À vista / lançamento único (1x)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = isInstallmentEnabled,
                                    onCheckedChange = { checked ->
                                        isInstallmentEnabled = checked
                                        if (checked) {
                                            isFixedEnabled = false
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = BrandBlue),
                                    modifier = Modifier.testTag("installment_toggle_switch")
                                )
                            }

                            if (isInstallmentEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Nº de parcelas:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { if (installmentsCount > 2) installmentsCount-- },
                                            enabled = installmentsCount > 2,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Diminuir parcelas")
                                        }
                                        Text(
                                            text = "${installmentsCount}x",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = BrandBlue,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        IconButton(
                                            onClick = { if (installmentsCount < 48) installmentsCount++ },
                                            enabled = installmentsCount < 48,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Aumentar parcelas")
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(2, 3, 6, 10, 12, 24).forEach { qty ->
                                        FilterChip(
                                            selected = installmentsCount == qty,
                                            onClick = { installmentsCount = qty },
                                            label = { Text("${qty}x", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = amountIsTotal,
                                        onClick = { amountIsTotal = true },
                                        label = { Text("Valor total", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = !amountIsTotal,
                                        onClick = { amountIsTotal = false },
                                        label = { Text("Valor da parcela", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                val endYm = MonthDateHelper.shiftYearMonth(selectedYearMonth, installmentsCount - 1)
                                val rangeText = "${MonthDateHelper.formatShortMonthYear(selectedYearMonth)} até ${MonthDateHelper.formatShortMonthYear(endYm)}"
                                val editHint = if (isEditing) "\nEste item vira (1/${installmentsCount}) e gera +${installmentsCount - 1} parcela(s)." else ""
                                val previewLine = if (parsedAmount != null && parsedAmount > 0.0) {
                                    val split = MonthDateHelper.splitInstallmentAmounts(parsedAmount, installmentsCount, amountIsTotal)
                                    val totalAll = split.sum()
                                    "${installmentsCount}x de ${FinanceViewModel.formatBrl(split.first())} (Total: ${FinanceViewModel.formatBrl(totalAll)})\nPeríodo: $rangeText$editHint"
                                } else {
                                    "Serão lançadas ${installmentsCount} parcelas de $rangeText$editHint"
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldGreen.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = previewLine,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = EmeraldDark,
                                        modifier = Modifier.padding(10.dp),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick button inside Edit dialog to delete complete installment plan
                if (isEditing && installmentSeriesCount > 1 && onDeleteInstallmentPlan != null) {
                    OutlinedButton(
                        onClick = onDeleteInstallmentPlan,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_delete_installment_plan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Excluir parcelamento completo ($installmentSeriesCount meses)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (description.isNotBlank() && parsedAmount != null && parsedAmount > 0.0) {
                        val (finalIsFixed, finalFixedEndMonth) = when {
                            isFixedEnabled -> {
                                // If keeping Conta Fixa active: clear any old end bound ONLY if user re-enabled it on or past the old endMonth
                                val existingEnd = editingTransaction?.fixedEndMonth.orEmpty()
                                val preservedEnd = if (existingEnd.isNotBlank() && activeViewingYm < existingEnd) existingEnd else ""
                                true to preservedEnd
                            }
                            isEditingFutureFixedOccurrence -> {
                                if (keepFixedThroughViewingMonth) {
                                    true to activeViewingYm
                                } else {
                                    val prevYm = MonthDateHelper.shiftYearMonth(activeViewingYm, -1)
                                    if (prevYm < originYearMonth) {
                                        false to ""
                                    } else {
                                        true to prevYm
                                    }
                                }
                            }
                            else -> false to ""
                        }

                        onSave(
                            description,
                            parsedAmount,
                            selectedType,
                            selectedCategory,
                            computedDate,
                            if (finalIsFixed) 1 else effectiveInstallments,
                            amountIsTotal,
                            finalIsFixed,
                            finalFixedEndMonth
                        )
                    } else {
                        hasError = true
                    }
                },
                modifier = Modifier.testTag("save_transaction_button")
            ) {
                val buttonLabel = when {
                    !isFixedEnabled && isEditingFutureFixedOccurrence -> "Encerrar Conta Fixa"
                    isEditing && isFixedEnabled -> "Salvar Conta Fixa"
                    isEditing && effectiveInstallments > 1 -> "Parcelar (${effectiveInstallments}x)"
                    isEditing -> "Salvar Alterações"
                    isFixedEnabled -> "Salvar Conta Fixa"
                    effectiveInstallments > 1 -> "Salvar (${effectiveInstallments}x)"
                    else -> "Salvar Transação"
                }
                Text(buttonLabel, fontWeight = FontWeight.Bold, color = NavyPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceFilterDialog(
    selectedCategory: String,
    selectedType: String,
    onDismiss: () -> Unit,
    onApply: (category: String, type: String) -> Unit,
    onReset: () -> Unit
) {
    var tempCategory by remember { mutableStateOf(selectedCategory) }
    var tempType by remember { mutableStateOf(selectedType) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val categoriesWithAll = listOf("Todas") + DEFAULT_CATEGORIES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtrar Transações", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Tipo de Movimentação", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = tempType == "ALL",
                        onClick = { tempType = "ALL" },
                        label = { Text("Todas", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = tempType == "INCOME",
                        onClick = { tempType = "INCOME" },
                        label = { Text("Receitas", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = tempType == "EXPENSE",
                        onClick = { tempType = "EXPENSE" },
                        label = { Text("Despesas", fontSize = 12.sp) }
                    )
                }

                Text("Categoria", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = tempCategory,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                imageVector = if (tempCategory == "Todas") Icons.Default.Category else getCategoryIcon(tempCategory),
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        categoriesWithAll.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (cat == "Todas") Icons.Default.Category else getCategoryIcon(cat),
                                        contentDescription = null,
                                        tint = if (tempCategory == cat) NavyPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    tempCategory = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(tempCategory, tempType) }) {
                Text("Aplicar Filtro", fontWeight = FontWeight.Bold, color = NavyPrimary)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text("Limpar", color = CoralRed)
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar")
                }
            }
        }
    )
}
