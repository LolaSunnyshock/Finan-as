package com.example.data.util

import com.google.firebase.auth.FirebaseUser
import java.util.Locale

object UserNameFormatter {

    fun extractFirstName(
        displayName: String?,
        email: String? = null,
        fallback: String = "Usuário"
    ): String {
        val rawName = displayName?.trim().orEmpty()
        if (rawName.isNotBlank() && !rawName.contains("@")) {
            val firstWord = rawName.split(Regex("\\s+")).firstOrNull()?.trim().orEmpty()
            if (firstWord.isNotBlank()) {
                return firstWord.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
        }

        val emailSource = when {
            rawName.contains("@") -> rawName
            !email.isNullOrBlank() -> email.trim()
            else -> ""
        }

        if (emailSource.isNotBlank()) {
            val localPart = emailSource.substringBefore("@").trim()
            val token = localPart.split('.', '_', '-', ' ')
                .firstOrNull { it.isNotBlank() }
                ?: localPart
            if (token.isNotBlank()) {
                return token.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
        }

        return fallback
    }

    fun fromFirebaseUser(user: FirebaseUser?, fallback: String = "Visitante"): String {
        if (user == null) return fallback
        return extractFirstName(
            displayName = user.displayName,
            email = user.email,
            fallback = "Usuário"
        )
    }

    fun sanitizeStoredName(storedName: String?, fallback: String = "Usuário"): String {
        val raw = storedName?.trim().orEmpty()
        if (raw.isBlank()) return fallback
        return extractFirstName(
            displayName = raw,
            email = if (raw.contains("@")) raw else null,
            fallback = fallback
        )
    }
}
