package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class NoteItem(
    @DocumentId val id: String = "",
    val spaceId: String = "",
    val userId: String = "",
    val authorName: String = "",
    val title: String = "",
    val content: String = "",
    val color: String = "#FFFFFF",
    val isPinned: Boolean = false,
    val folder: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
