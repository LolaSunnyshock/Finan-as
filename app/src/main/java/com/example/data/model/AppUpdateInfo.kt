package com.example.data.model

data class AppUpdateInfo(
    val latestVersionCode: Long = 1L,
    val latestVersionName: String = "1.0",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val forceUpdate: Boolean = false,
    val source: String = "Nuvem"
)
