package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class SpaceItem(
    @DocumentId val id: String = "",
    val name: String = "Meu Espaço",
    val inviteCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
