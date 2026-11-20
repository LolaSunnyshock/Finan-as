package com.example.ui.finance

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SpaceItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.repository.FinanceRepository
import com.example.data.repository.SpaceRepository
import com.example.data.util.MonthDateHelper
import com.example.data.util.UserNameFormatter
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

data class InvestmentBaseConfig(
    val strategyName: String = "Base Equilibrada 50/30/20",
    val investmentNotes: String = "Reserva de Emergência (Tesouro Selic / CDB 100% CDI) e Fundos Imobiliários",
    val monthlyBaseAmount: Double = 0.0,
    val categoryTargets: Map<String, Float> = DEFAULT_CATEGORY_TARGETS
) {
    val totalTargetPercent: Float
        get() = categoryTargets.values.sum()
}

val DEFAULT_CATEGORY_TARGETS: Map<String, Float> = mapOf(
    "Investimento" to 20f,
    "Moradia" to 25f,
    "Mercado" to 15f,
    "Contas" to 15f,
    "Transporte" to 10f,
    "Saúde" to 5f,
    "Lazer" to 5f,
    "Educação" to 5f,
    "Outros" to 0f
)

data class FinanceUiState(
    val currentSpace: SpaceItem? = null,
    val transactions: List<TransactionItem> = emptyList(),
    val allRawTransactions: List<TransactionItem> = emptyList(),
    val allTransactionsCount: Int = 0,
    val selectedMonth: String? = MonthDateHelper.currentYearMonth(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val currentBalance: Double = 0.0,
    val selectedCategory: String = "Todas",
    val selectedTypeFilter: String = "ALL", // "ALL", "INCOME", "EXPENSE"
    val investmentBase: InvestmentBaseConfig = InvestmentBaseConfig(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel @JvmOverloads constructor(
    application: Application,
    private val financeRepository: FinanceRepository = FinanceRepository(application.applicationContext),
    private val spaceRepository: SpaceRepository = SpaceRepository(application.applicationContext)
) : AndroidViewModel(application) {

    private val prefs = application.applicationContext.getSharedPreferences(
        "finance_investment_base_prefs",
        Context.MODE_PRIVATE
    )

    private val isGuest: Boolean
        get() = Firebase.auth.currentUser == null

    private val _currentSpace = MutableStateFlow<SpaceItem?>(null)
    val currentSpace: StateFlow<SpaceItem?> = _currentSpace.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("ALL")
    val selectedTypeFilter: StateFlow<String> = _selectedTypeFilter.asStateFlow()

    private val _selectedMonth = MutableStateFlow<String?>(MonthDateHelper.currentYearMonth())
    val selectedMonth: StateFlow<String?> = _selectedMonth.asStateFlow()

    private val _investmentBase = MutableStateFlow(loadInvestmentBaseFromPrefs())
    val investmentBase: StateFlow<InvestmentBaseConfig> = _investmentBase.asStateFlow()

    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus.asStateFlow()

    private val defaultGuestMonth = MonthDateHelper.currentYearMonth()

    // In-memory guest state for rapid interactive testing without Google Sign-In
    private val _guestTransactions = MutableStateFlow<List<TransactionItem>>(
        listOf(
            TransactionItem(
                id = "guest_tx_1",
                spaceId = "guest_space",
                userId = "guest_user",
                userName = "Visitante",
                description = "Salário / Rendimentos",
                amount = 4500.0,
                type = TransactionType.INCOME.name,
                category = "Salário",
                date = MonthDateHelper.buildDateForMonth(defaultGuestMonth, 10)
            ),
            TransactionItem(
                id = "guest_tx_2",
                spaceId = "guest_space",
                userId = "guest_user",
                userName = "Visitante",
                description = "Supermercado Semanal",
                amount = 520.0,
                type = TransactionType.EXPENSE.name,
                category = "Mercado",
                date = MonthDateHelper.buildDateForMonth(defaultGuestMonth, 10)
            ),
            TransactionItem(
                id = "guest_tx_3",
                spaceId = "guest_space",
                userId = "guest_user",
                userName = "Visitante",
                description = "Conta de Luz & Internet",
                amount = 340.0,
                type = TransactionType.EXPENSE.name,
                category = "Contas",
                date = MonthDateHelper.buildDateForMonth(defaultGuestMonth, 8)
            ),
            TransactionItem(
                id = "guest_tx_4",
                spaceId = "guest_space",
                userId = "guest_user",
                userName = "Visitante",
                description = "Restaurante & Cinema",
                amount = 420.0,
                type = TransactionType.EXPENSE.name,
                category = "Lazer",
                date = MonthDateHelper.buildDateForMonth(defaultGuestMonth, 6)
            ),
            TransactionItem(
                id = "guest_tx_5",
                spaceId = "guest_space",
                userId = "guest_user",
                userName = "Visitante",
                description = "Aporte Tesouro Selic",
                amount = 200.0,
                type = TransactionType.EXPENSE.name,
                category = "Investimento",
                date = MonthDateHelper.buildDateForMonth(defaultGuestMonth, 5)
            )
        )
    )

    init {
        loadSpace()
    }

    private fun loadInvestmentBaseFromPrefs(): InvestmentBaseConfig {
        return try {
            val strategy = prefs.getString("strategy_name", null)
                ?: "Base Equilibrada 50/30/20"
            val notes = prefs.getString("investment_notes", null)
                ?: "Reserva de Emergência (Tesouro Selic / CDB 100% CDI) e Fundos Imobiliários"
            val monthlyBase = java.lang.Double.longBitsToDouble(
                prefs.getLong("monthly_base_bits", java.lang.Double.doubleToRawLongBits(0.0))
            )
            val targetsJson = prefs.getString("category_targets_json", null)
            val targets = if (targetsJson != null) {
                val obj = JSONObject(targetsJson)
                val map = linkedMapOf<String, Float>()
                DEFAULT_CATEGORY_TARGETS.keys.forEach { key ->
                    map[key] = if (obj.has(key)) obj.optDouble(key, 0.0).toFloat() else (DEFAULT_CATEGORY_TARGETS[key] ?: 0f)
                }
                obj.keys().forEach { key ->
                    if (!map.containsKey(key)) {
                        map[key] = obj.optDouble(key, 0.0).toFloat()
                    }
                }
                map
            } else {
                DEFAULT_CATEGORY_TARGETS
            }
            InvestmentBaseConfig(
                strategyName = strategy,
                investmentNotes = notes,
                monthlyBaseAmount = monthlyBase,
                categoryTargets = targets
            )
        } catch (e: Exception) {
            InvestmentBaseConfig()
        }
    }

    fun updateInvestmentBase(
        strategyName: String,
        investmentNotes: String,
        monthlyBaseAmount: Double,
        categoryTargets: Map<String, Float>
    ) {
        val cleanStrategy = strategyName.trim().ifBlank { "Minha Base de Investimento" }
        val cleanNotes = investmentNotes.trim()
        val updated = InvestmentBaseConfig(
            strategyName = cleanStrategy,
            investmentNotes = cleanNotes,
            monthlyBaseAmount = monthlyBaseAmount.coerceAtLeast(0.0),
            categoryTargets = categoryTargets
        )
        _investmentBase.value = updated

        try {
            val json = JSONObject()
            categoryTargets.forEach { (k, v) -> json.put(k, v.toDouble()) }
            prefs.edit()
                .putString("strategy_name", cleanStrategy)
                .putString("investment_notes", cleanNotes)
                .putLong("monthly_base_bits", java.lang.Double.doubleToRawLongBits(updated.monthlyBaseAmount))
                .putString("category_targets_json", json.toString())
                .apply()
        } catch (_: Exception) {
        }
        _operationStatus.value = "Base de investimento atualizada!"
    }

    fun resetInvestmentBaseToDefault() {
        val defaultConfig = InvestmentBaseConfig()
        _investmentBase.value = defaultConfig
        prefs.edit().clear().apply()
        _operationStatus.value = "Base de investimento restaurada para o padrão."
    }

    fun loadSpace() {
        viewModelScope.launch {
            if (isGuest) {
                _currentSpace.value = SpaceItem(
                    id = "guest_space",
                    name = "Espaço Local (Visitante)",
                    inviteCode = "DEMO99",
                    ownerId = "guest_user",
                    memberUids = listOf("guest_user"),
                    memberNames = listOf("Visitante")
                )
            } else {
                try {
                    val space = spaceRepository.getOrCreateUserDefaultSpace()
                    _currentSpace.value = space
                } catch (e: Exception) {
                    _operationStatus.value = "Erro ao carregar espaço: ${e.message}"
                }
            }
        }
    }

    val uiState: StateFlow<FinanceUiState> = _currentSpace
        .filterNotNull()
        .flatMapLatest { space ->
            val transactionsFlow = if (isGuest) {
                _guestTransactions
            } else {
                financeRepository.observeTransactions(space.id)
            }
            combine(transactionsFlow, _investmentBase, _selectedMonth) { list, baseConfig, monthFilter ->
                val sortedAll = list.sortedByDescending { it.date }
                val monthTransactions = if (monthFilter == null) {
                    sortedAll
                } else {
                    sortedAll.mapNotNull { tx ->
                        if (tx.isFixed) {
                            if (MonthDateHelper.isFixedActiveInMonth(tx, monthFilter)) {
                                MonthDateHelper.projectFixedTransactionForMonth(tx, monthFilter)
                            } else {
                                null
                            }
                        } else {
                            val txYm = MonthDateHelper.parseYearMonth(tx.date)?.let { (y, m) ->
                                String.format(Locale.US, "%04d-%02d", y, m)
                            }
                            if (txYm == monthFilter) tx else null
                        }
                    }.sortedByDescending { it.date }
                }
                val income = monthTransactions.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
                val expense = monthTransactions.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
                FinanceUiState(
                    currentSpace = space,
                    transactions = monthTransactions,
                    allRawTransactions = sortedAll,
                    allTransactionsCount = sortedAll.size,
                    selectedMonth = monthFilter,
                    totalIncome = income,
                    totalExpense = expense,
                    currentBalance = income - expense,
                    investmentBase = baseConfig,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(
                FinanceUiState(
                    currentSpace = _currentSpace.value,
                    selectedMonth = _selectedMonth.value,
                    investmentBase = _investmentBase.value,
                    isLoading = false,
                    errorMessage = rootError.message ?: "Erro ao carregar transações financeiras"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = FinanceUiState(isLoading = true)
        )

    fun joinSpaceByCode(code: String, onComplete: (Boolean) -> Unit) {
        if (isGuest) {
            _operationStatus.value = "Conecte sua conta Google para participar de um espaço compartilhado na nuvem."
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = spaceRepository.joinSpaceByCode(code)
            if (result.isSuccess) {
                val space = result.getOrNull()
                _currentSpace.value = space
                _operationStatus.value = "Conectado ao espaço compartilhado com sucesso!"
                onComplete(true)
            } else {
                _operationStatus.value = result.exceptionOrNull()?.message ?: "Falha ao conectar ao espaço"
                onComplete(false)
            }
        }
    }

    fun selectMonth(yearMonth: String?) {
        _selectedMonth.value = yearMonth
    }

    fun shiftSelectedMonth(offsetMonths: Int) {
        val base = _selectedMonth.value ?: MonthDateHelper.currentYearMonth()
        _selectedMonth.value = MonthDateHelper.shiftYearMonth(base, offsetMonths)
    }

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = category
    }

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun addTransaction(
        description: String,
        amount: Double,
        type: TransactionType,
        category: String,
        date: String,
        installments: Int = 1,
        amountIsTotal: Boolean = true,
        isFixed: Boolean = false,
        fixedEndMonth: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val space = _currentSpace.value ?: return
        val safeInstallments = if (isFixed) 1 else installments.coerceIn(1, 48)
        val startYearMonth = MonthDateHelper.extractYearMonth(date)
        val preferredDay = MonthDateHelper.extractDayOfMonth(date)
        val installmentAmounts = MonthDateHelper.splitInstallmentAmounts(
            inputAmount = amount,
            installments = safeInstallments,
            amountIsTotal = amountIsTotal
        )
        val currentUserName = if (isGuest) {
            "Visitante"
        } else {
            UserNameFormatter.fromFirebaseUser(Firebase.auth.currentUser, "Usuário")
        }
        val uid = if (isGuest) "guest_user" else (Firebase.auth.currentUser?.uid ?: "")
        val nowMillis = System.currentTimeMillis()
        val groupId = if (safeInstallments > 1) UUID.randomUUID().toString() else ""

        val itemsToCreate = installmentAmounts.mapIndexed { index, parcelAmount ->
            val targetYm = MonthDateHelper.shiftYearMonth(startYearMonth, index)
            val targetDate = MonthDateHelper.buildDateForMonth(targetYm, preferredDay)
            val parcelDesc = MonthDateHelper.buildInstallmentDescription(
                baseDescription = description,
                installmentNumber = index + 1,
                totalInstallments = safeInstallments
            )
            TransactionItem(
                id = if (isGuest) "guest_${nowMillis}_$index" else "",
                spaceId = space.id,
                userId = uid,
                userName = currentUserName,
                description = parcelDesc,
                amount = parcelAmount,
                type = type.name,
                category = category.trim(),
                date = targetDate,
                isFixed = isFixed,
                fixedEndMonth = if (isFixed) fixedEndMonth else "",
                installmentGroupId = groupId
            )
        }

        viewModelScope.launch {
            if (isGuest) {
                _guestTransactions.value = itemsToCreate + _guestTransactions.value
                if (_selectedMonth.value != null && _selectedMonth.value != startYearMonth) {
                    _selectedMonth.value = startYearMonth
                }
                _operationStatus.value = when {
                    isFixed -> "Conta fixa adicionada a partir de ${MonthDateHelper.formatShortMonthYear(startYearMonth)}!"
                    safeInstallments > 1 -> {
                        val endYm = MonthDateHelper.shiftYearMonth(startYearMonth, safeInstallments - 1)
                        "$safeInstallments parcelas criadas (${MonthDateHelper.formatShortMonthYear(startYearMonth)} a ${MonthDateHelper.formatShortMonthYear(endYm)})!"
                    }
                    else -> "Transação adicionada em ${MonthDateHelper.formatShortMonthYear(startYearMonth)}!"
                }
                onComplete(true)
            } else {
                val resultSuccess: Result<*> = if (itemsToCreate.size == 1) {
                    financeRepository.addTransaction(space.id, itemsToCreate.first())
                } else {
                    financeRepository.addTransactionsBatch(space.id, itemsToCreate)
                }
                if (resultSuccess.isSuccess) {
                    if (_selectedMonth.value != null && _selectedMonth.value != startYearMonth) {
                        _selectedMonth.value = startYearMonth
                    }
                    _operationStatus.value = when {
                        isFixed -> "Conta fixa adicionada para os próximos meses!"
                        safeInstallments > 1 -> {
                            val endYm = MonthDateHelper.shiftYearMonth(startYearMonth, safeInstallments - 1)
                            "$safeInstallments parcelas lançadas (${MonthDateHelper.formatShortMonthYear(startYearMonth)} a ${MonthDateHelper.formatShortMonthYear(endYm)})!"
                        }
                        else -> "Transação adicionada com sucesso!"
                    }
                    onComplete(true)
                } else {
                    _operationStatus.value = "Falha ao adicionar: ${resultSuccess.exceptionOrNull()?.message}"
                    onComplete(false)
                }
            }
        }
    }

    fun updateTransaction(
        original: TransactionItem,
        description: String,
        amount: Double,
        type: TransactionType,
        category: String,
        date: String,
        installments: Int = 1,
        amountIsTotal: Boolean = true,
        isFixed: Boolean = original.isFixed,
        fixedEndMonth: String = original.fixedEndMonth,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val space = _currentSpace.value ?: return
        val safeInstallments = if (isFixed) 1 else installments.coerceIn(1, 48)
        val startYearMonth = MonthDateHelper.extractYearMonth(date)
        val preferredDay = MonthDateHelper.extractDayOfMonth(date)
        val cleanName = if (isGuest) {
            "Visitante"
        } else {
            UserNameFormatter.fromFirebaseUser(Firebase.auth.currentUser, "Usuário")
        }
        val uid = if (isGuest) "guest_user" else (Firebase.auth.currentUser?.uid ?: original.userId)
        val nowMillis = System.currentTimeMillis()
        val groupId = when {
            safeInstallments > 1 -> UUID.randomUUID().toString()
            else -> original.installmentGroupId
        }

        val installmentAmounts = MonthDateHelper.splitInstallmentAmounts(
            inputAmount = amount,
            installments = safeInstallments,
            amountIsTotal = amountIsTotal
        )

        val updatedFirst = original.copy(
            description = MonthDateHelper.buildInstallmentDescription(description, 1, safeInstallments),
            amount = installmentAmounts.first(),
            type = type.name,
            category = category.trim(),
            date = MonthDateHelper.buildDateForMonth(startYearMonth, preferredDay),
            userName = cleanName,
            isFixed = isFixed,
            fixedEndMonth = if (isFixed) fixedEndMonth else "",
            installmentGroupId = if (isFixed) "" else groupId
        )

        val extraInstallments = if (safeInstallments > 1) {
            installmentAmounts.drop(1).mapIndexed { offsetIdx, parcelAmount ->
                val parcelNumber = offsetIdx + 2
                val targetYm = MonthDateHelper.shiftYearMonth(startYearMonth, parcelNumber - 1)
                TransactionItem(
                    id = if (isGuest) "guest_${nowMillis}_$parcelNumber" else "",
                    spaceId = space.id,
                    userId = uid,
                    userName = cleanName,
                    description = MonthDateHelper.buildInstallmentDescription(description, parcelNumber, safeInstallments),
                    amount = parcelAmount,
                    type = type.name,
                    category = category.trim(),
                    date = MonthDateHelper.buildDateForMonth(targetYm, preferredDay),
                    isFixed = false,
                    fixedEndMonth = "",
                    installmentGroupId = groupId
                )
            }
        } else {
            emptyList()
        }

        viewModelScope.launch {
            if (isGuest) {
                val updatedList = _guestTransactions.value.map {
                    if (it.id == original.id) updatedFirst else it
                }
                _guestTransactions.value = if (extraInstallments.isNotEmpty()) {
                    extraInstallments + updatedList
                } else {
                    updatedList
                }
                _operationStatus.value = when {
                    isFixed && fixedEndMonth.isNotBlank() ->
                        "Conta fixa mantida até ${MonthDateHelper.formatShortMonthYear(fixedEndMonth)}!"
                    isFixed -> "Conta fixa atualizada!"
                    safeInstallments > 1 -> {
                        val endYm = MonthDateHelper.shiftYearMonth(startYearMonth, safeInstallments - 1)
                        "Parcelado em ${safeInstallments}x (${MonthDateHelper.formatShortMonthYear(startYearMonth)} a ${MonthDateHelper.formatShortMonthYear(endYm)})!"
                    }
                    else -> "Transação atualizada!"
                }
                onComplete(true)
            } else {
                val result: Result<*> = if (extraInstallments.isEmpty()) {
                    financeRepository.updateTransaction(space.id, updatedFirst)
                } else {
                    financeRepository.updateTransactionWithInstallments(space.id, updatedFirst, extraInstallments)
                }
                if (result.isSuccess) {
                    _operationStatus.value = when {
                        isFixed && fixedEndMonth.isNotBlank() ->
                            "Conta fixa mantida até ${MonthDateHelper.formatShortMonthYear(fixedEndMonth)}!"
                        isFixed -> "Conta fixa atualizada!"
                        safeInstallments > 1 -> {
                            val endYm = MonthDateHelper.shiftYearMonth(startYearMonth, safeInstallments - 1)
                            "Parcelado em ${safeInstallments}x (${MonthDateHelper.formatShortMonthYear(startYearMonth)} a ${MonthDateHelper.formatShortMonthYear(endYm)})!"
                        }
                        else -> "Transação atualizada com sucesso!"
                    }
                    onComplete(true)
                } else {
                    _operationStatus.value = "Falha ao atualizar: ${result.exceptionOrNull()?.message}"
                    onComplete(false)
                }
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        if (isGuest) {
            _guestTransactions.value = _guestTransactions.value.filterNot { it.id == transactionId }
            _operationStatus.value = "Transação excluída"
        } else {
            val space = _currentSpace.value ?: return
            viewModelScope.launch {
                val result = financeRepository.deleteTransaction(space.id, transactionId)
                if (result.isSuccess) {
                    _operationStatus.value = "Transação excluída"
                } else {
                    _operationStatus.value = "Falha ao excluir: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun deleteInstallmentPlan(
        target: TransactionItem,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val rawAll = if (isGuest) _guestTransactions.value else uiState.value.allRawTransactions
        val series = MonthDateHelper.findInstallmentSeries(target, rawAll)
        val idsToDelete = series.map { it.id }.filter { it.isNotBlank() }.ifEmpty { listOf(target.id) }

        if (isGuest) {
            val idSet = idsToDelete.toSet()
            _guestTransactions.value = _guestTransactions.value.filterNot { it.id in idSet }
            _operationStatus.value = "Parcelamento completo excluído (${idsToDelete.size} parcelas)!"
            onComplete(true)
        } else {
            val space = _currentSpace.value ?: return
            viewModelScope.launch {
                val result = financeRepository.deleteTransactionsBatch(space.id, idsToDelete)
                if (result.isSuccess) {
                    _operationStatus.value = "Parcelamento completo excluído (${idsToDelete.size} parcelas)!"
                    onComplete(true)
                } else {
                    _operationStatus.value = "Falha ao excluir parcelamento: ${result.exceptionOrNull()?.message}"
                    onComplete(false)
                }
            }
        }
    }

    fun clearStatusMessage() {
        _operationStatus.value = null
    }

    companion object {
        fun formatBrl(value: Double): String {
            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            return format.format(value)
        }
    }
}
