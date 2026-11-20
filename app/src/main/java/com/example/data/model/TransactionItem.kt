package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType {
    INCOME,
    EXPENSE
}

@IgnoreExtraProperties
data class TransactionItem(
    @DocumentId val id: String = "",
    val spaceId: String = "",
    val userId: String = "",
    val userName: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val type: String = TransactionType.EXPENSE.name,
    val category: String = "Outros",
    val date: String = "",
    val isFixed: Boolean = false,
    val fixedEndMonth: String = "",
    val installmentGroupId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
