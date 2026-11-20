package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppUpdateInfo
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VersionCheckStatus(
    val installedVersionCode: Long,
    val installedVersionName: String,
    val remoteVersionCode: Long?,
    val remoteVersionName: String?,
    val downloadUrl: String,
    val releaseNotes: String,
    val updateInfo: AppUpdateInfo?,
    val isSignedIn: Boolean,
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val checkSource: String = "Firestore",
    val firestoreVersionCode: Long? = null,
    val firestoreVersionName: String? = null,
    val firestoreSynced: Boolean = false,
    val errorMessage: String? = null
)

data class ParsedVersionManifest(
    val latestVersionCode: Long,
    val latestVersionName: String,
    val releaseNotes: String,
    val apkPath: String,
    val forceUpdate: Boolean
)

data class GithubManifestResolution(
    val manifest: ParsedVersionManifest,
    val resolvedBranch: String,
    val commitSha: String? = null
)

class UpdateRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        Firebase.firestore(context.getString(R.string.firestore_database_id)),
        Firebase.auth,
        context.applicationContext
    )

    private val prefs by lazy {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedGithubRepo(): String =
        prefs?.getString(KEY_GITHUB_REPO, "").orEmpty().trim()

    fun getSavedGithubBranch(): String =
        prefs?.getString(KEY_GITHUB_BRANCH, DEFAULT_BRANCH).orEmpty().trim().ifBlank { DEFAULT_BRANCH }

    private fun saveGithubConfigLocally(repo: String, branch: String) {
        prefs?.edit()
            ?.putString(KEY_GITHUB_REPO, repo)
            ?.putString(KEY_GITHUB_BRANCH, branch.ifBlank { DEFAULT_BRANCH })
            ?.apply()
    }

    suspend fun saveGithubConfig(rawRepoInput: String, rawBranchInput: String): Result<Pair<String, String>> {
        val normalizedRepo = normalizeGithubRepo(rawRepoInput)
        val normalizedBranch = rawBranchInput.trim().ifBlank { DEFAULT_BRANCH }

        if (rawRepoInput.isNotBlank() && normalizedRepo.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Formato inválido. Use usuario/repositorio (ex: joao/financas-notas) ou cole o link do GitHub.")
            )
        }

        saveGithubConfigLocally(normalizedRepo, normalizedBranch)

        // Also sync to Firestore so reinstalling or switching devices remembers the repo
        if (auth.currentUser != null) {
            try {
                val data = hashMapOf<String, Any>(
                    "githubRepo" to normalizedRepo,
                    "githubBranch" to normalizedBranch,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("app_config")
                    .document("version")
                    .set(data, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "app_config/version")
            }
        }

        return Result.success(normalizedRepo to normalizedBranch)
    }

    suspend fun checkForUpdate(): Result<AppUpdateInfo?> {
        val status = checkVersionStatus()
        return if (status.errorMessage != null && status.updateInfo == null) {
            Result.failure(IllegalStateException(status.errorMessage))
        } else {
            Result.success(status.updateInfo)
        }
    }

    suspend fun checkVersionStatus(): VersionCheckStatus {
        val currentCode = BuildConfig.VERSION_CODE.toLong()
        val currentName = BuildConfig.VERSION_NAME
        val signedIn = auth.currentUser != null

        var configuredRepo = getSavedGithubRepo()
        var configuredBranch = getSavedGithubBranch()

        // Try reading Firestore config to discover a cloud-saved GitHub repo or fallback version
        var firestoreDocExists = false
        var fsCode: Long? = null
        var fsName: String? = null
        var fsUrl = ""
        var fsNotes = ""
        var fsForce = false
        var fsError: String? = null

        try {
            val doc = firestore.collection("app_config")
                .document("version")
                .get()
                .await()

            if (doc.exists()) {
                firestoreDocExists = true
                fsCode = doc.getLong("latestVersionCode")
                fsName = doc.getString("latestVersionName")
                fsUrl = doc.getString("downloadUrl").orEmpty().trim()
                fsNotes = doc.getString("releaseNotes").orEmpty()
                fsForce = doc.getBoolean("forceUpdate") ?: false

                val cloudRepo = normalizeGithubRepo(doc.getString("githubRepo").orEmpty())
                val cloudBranch = doc.getString("githubBranch").orEmpty().trim().ifBlank { DEFAULT_BRANCH }
                if (configuredRepo.isBlank() && cloudRepo.isNotBlank()) {
                    configuredRepo = cloudRepo
                    configuredBranch = cloudBranch
                    saveGithubConfigLocally(cloudRepo, cloudBranch)
                }
            }
        } catch (e: Exception) {
            fsError = e.message
            if (signedIn) {
                handleFirestoreError(e, OperationType.GET, "app_config/version")
            }
        }

        // Priority 1: Check GitHub repository app-version.json directly (resolving live commit SHA to bypass CDN cache)
        if (configuredRepo.isNotBlank()) {
            val githubResult = fetchGithubVersionManifest(configuredRepo, configuredBranch)
            if (githubResult.isSuccess) {
                val resolution = githubResult.getOrThrow()
                val manifest = resolution.manifest
                val resolvedBranch = resolution.resolvedBranch
                val downloadRef = resolution.commitSha?.takeIf { it.isNotBlank() } ?: resolvedBranch
                if (resolvedBranch != configuredBranch) {
                    configuredBranch = resolvedBranch
                    saveGithubConfigLocally(configuredRepo, resolvedBranch)
                }
                val apkDownloadUrl = buildApkDownloadUrl(configuredRepo, downloadRef, manifest.apkPath)

                // Automatically mirror the latest GitHub release metadata into Firestore app_config/version
                var syncedFsCode = fsCode
                var syncedFsName = fsName
                var wasFirestoreSynced = false
                if (signedIn) {
                    if (manifest.latestVersionCode >= currentCode) {
                        val needsFirestoreUpdate = fsCode == null ||
                            manifest.latestVersionCode > fsCode ||
                            (manifest.latestVersionCode == fsCode && (
                                fsUrl != apkDownloadUrl ||
                                    fsNotes != manifest.releaseNotes ||
                                    fsName != manifest.latestVersionName
                                ))
                        if (needsFirestoreUpdate) {
                            val syncRes = syncManifestToCloud(
                                manifest = manifest,
                                repo = configuredRepo,
                                branch = resolvedBranch,
                                downloadUrl = apkDownloadUrl
                            )
                            if (syncRes.isSuccess) {
                                syncedFsCode = manifest.latestVersionCode
                                syncedFsName = manifest.latestVersionName
                                wasFirestoreSynced = true
                            }
                        } else {
                            wasFirestoreSynced = true
                        }
                    } else if (currentCode > (fsCode ?: 0L)) {
                        val syncRes = syncCurrentBuildToCloud(preserveExistingUrl = apkDownloadUrl)
                        if (syncRes.isSuccess) {
                            syncedFsCode = currentCode
                            syncedFsName = currentName
                            wasFirestoreSynced = true
                        }
                    } else {
                        wasFirestoreSynced = (fsCode != null && fsCode >= currentCode)
                    }
                }

                val updateInfo = if (manifest.latestVersionCode > currentCode) {
                    AppUpdateInfo(
                        latestVersionCode = manifest.latestVersionCode,
                        latestVersionName = manifest.latestVersionName,
                        downloadUrl = apkDownloadUrl,
                        releaseNotes = manifest.releaseNotes,
                        forceUpdate = manifest.forceUpdate,
                        source = "GitHub + Firestore"
                    )
                } else {
                    null
                }
                return VersionCheckStatus(
                    installedVersionCode = currentCode,
                    installedVersionName = currentName,
                    remoteVersionCode = manifest.latestVersionCode,
                    remoteVersionName = manifest.latestVersionName,
                    downloadUrl = apkDownloadUrl,
                    releaseNotes = manifest.releaseNotes,
                    updateInfo = updateInfo,
                    isSignedIn = signedIn,
                    githubRepo = configuredRepo,
                    githubBranch = resolvedBranch,
                    checkSource = "GitHub",
                    firestoreVersionCode = syncedFsCode,
                    firestoreVersionName = syncedFsName,
                    firestoreSynced = wasFirestoreSynced
                )
            } else {
                val errMsg = githubResult.exceptionOrNull()?.message
                    ?: "Não foi possível ler app-version.json no GitHub."
                val fallbackUpdate = if (fsCode != null && fsCode > currentCode) {
                    AppUpdateInfo(
                        latestVersionCode = fsCode,
                        latestVersionName = fsName ?: "1.0",
                        downloadUrl = fsUrl,
                        releaseNotes = fsNotes.ifBlank { "Nova versão disponível." },
                        forceUpdate = fsForce,
                        source = "Firestore"
                    )
                } else {
                    null
                }
                return VersionCheckStatus(
                    installedVersionCode = currentCode,
                    installedVersionName = currentName,
                    remoteVersionCode = fsCode,
                    remoteVersionName = fsName,
                    downloadUrl = fsUrl,
                    releaseNotes = fsNotes,
                    updateInfo = fallbackUpdate,
                    isSignedIn = signedIn,
                    githubRepo = configuredRepo,
                    githubBranch = configuredBranch,
                    checkSource = "GitHub",
                    firestoreVersionCode = fsCode,
                    firestoreVersionName = fsName,
                    firestoreSynced = false,
                    errorMessage = errMsg
                )
            }
        }

        // Priority 2: Fallback to Cloud Firestore version document
        if (!firestoreDocExists) {
            if (signedIn && fsError == null) {
                syncCurrentBuildToCloud()
            }
            return VersionCheckStatus(
                installedVersionCode = currentCode,
                installedVersionName = currentName,
                remoteVersionCode = if (signedIn && fsError == null) currentCode else null,
                remoteVersionName = if (signedIn && fsError == null) currentName else null,
                downloadUrl = "",
                releaseNotes = "",
                updateInfo = null,
                isSignedIn = signedIn,
                githubRepo = "",
                githubBranch = configuredBranch,
                checkSource = "Firestore",
                errorMessage = fsError
            )
        }

        val remoteCode = fsCode ?: 0L
        val remoteName = fsName ?: "1.0"
        val releaseNotes = fsNotes.ifBlank { "Melhorias e novos recursos no app." }

        if (currentCode > remoteCode && signedIn) {
            syncCurrentBuildToCloud(preserveExistingUrl = fsUrl)
            return VersionCheckStatus(
                installedVersionCode = currentCode,
                installedVersionName = currentName,
                remoteVersionCode = currentCode,
                remoteVersionName = currentName,
                downloadUrl = fsUrl,
                releaseNotes = releaseNotes,
                updateInfo = null,
                isSignedIn = true,
                githubRepo = "",
                githubBranch = configuredBranch,
                checkSource = "Firestore"
            )
        }

        val updateInfo = if (remoteCode > currentCode) {
            AppUpdateInfo(
                latestVersionCode = remoteCode,
                latestVersionName = remoteName,
                downloadUrl = fsUrl,
                releaseNotes = releaseNotes,
                forceUpdate = fsForce,
                source = "Firestore"
            )
        } else {
            null
        }

        return VersionCheckStatus(
            installedVersionCode = currentCode,
            installedVersionName = currentName,
            remoteVersionCode = remoteCode,
            remoteVersionName = remoteName,
            downloadUrl = fsUrl,
            releaseNotes = releaseNotes,
            updateInfo = updateInfo,
            isSignedIn = signedIn,
            githubRepo = "",
            githubBranch = configuredBranch,
            checkSource = "Firestore"
        )
    }

    private suspend fun fetchGithubVersionManifest(
        repo: String,
        preferredBranch: String
    ): Result<GithubManifestResolution> = withContext(Dispatchers.IO) {
        val branchesToTry = linkedSetOf(preferredBranch.ifBlank { DEFAULT_BRANCH })
        if (preferredBranch.equals("main", ignoreCase = true)) {
            branchesToTry.add("master")
        }

        var lastError: Exception? = null
        for (branch in branchesToTry) {
            // Step 1: Resolve the real-time HEAD commit SHA via GitHub REST API (zero 5-min Fastly CDN cache)
            val liveSha = fetchLiveBranchCommitSha(repo, branch)
            if (!liveSha.isNullOrBlank()) {
                val shaRawUrl = buildRawManifestUrl(repo, liveSha)
                val shaFetch = httpGetText(shaRawUrl, acceptHeader = "application/json")
                if (shaFetch.isSuccess) {
                    val parsed = parseVersionJson(shaFetch.getOrThrow())
                    if (parsed != null) {
                        return@withContext Result.success(
                            GithubManifestResolution(
                                manifest = parsed,
                                resolvedBranch = branch,
                                commitSha = liveSha
                            )
                        )
                    } else {
                        return@withContext Result.failure(
                            IllegalStateException("Arquivo app-version.json inválido no repositório $repo.")
                        )
                    }
                }
            }

            // Step 2: Direct GitHub Contents API raw fetch (also avoids raw.githubusercontent.com 5-min cache)
            val contentsApiUrl = buildContentsApiManifestUrl(repo, branch)
            val apiFetch = httpGetText(contentsApiUrl, acceptHeader = "application/vnd.github.raw+json")
            if (apiFetch.isSuccess) {
                val parsed = parseVersionJson(apiFetch.getOrThrow())
                if (parsed != null) {
                    return@withContext Result.success(
                        GithubManifestResolution(
                            manifest = parsed,
                            resolvedBranch = branch,
                            commitSha = liveSha
                        )
                    )
                }
            }

            // Step 3: Fallback to standard raw.githubusercontent.com URL (e.g. if API rate limit is reached)
            val rawUrl = buildRawManifestUrl(repo, branch, System.currentTimeMillis())
            val rawFetch = httpGetText(rawUrl, acceptHeader = "application/json")
            if (rawFetch.isSuccess) {
                val parsed = parseVersionJson(rawFetch.getOrThrow())
                    ?: return@withContext Result.failure(
                        IllegalStateException("Arquivo app-version.json inválido no repositório $repo.")
                    )
                return@withContext Result.success(
                    GithubManifestResolution(
                        manifest = parsed,
                        resolvedBranch = branch,
                        commitSha = liveSha
                    )
                )
            } else {
                lastError = rawFetch.exceptionOrNull() as? Exception
                    ?: IllegalStateException("Não foi possível consultar $repo ($branch).")
            }
        }
        Result.failure(lastError ?: IllegalStateException("Não foi possível consultar o GitHub."))
    }

    private fun fetchLiveBranchCommitSha(repo: String, branch: String): String? {
        val refUrl = buildGitRefApiUrl(repo, branch)
        val res = httpGetText(refUrl, acceptHeader = "application/vnd.github+json")
        return res.getOrNull()?.let { parseGitRefSha(it) }
    }

    private fun httpGetText(urlString: String, acceptHeader: String): Result<String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("User-Agent", "FinancasNotasApp/${BuildConfig.VERSION_NAME}")
                if (urlString.startsWith("https://api.github.com/")) {
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(body)
            } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                Result.failure(
                    IllegalStateException(
                        "Arquivo app-version.json não encontrado no GitHub. Verifique se o repositório é público e se o Push to GitHub foi concluído."
                    )
                )
            } else {
                Result.failure(IllegalStateException("GitHub respondeu HTTP $code ao consultar atualizações."))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Erro de conexão ao consultar GitHub: ${e.message}"))
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun syncManifestToCloud(
        manifest: ParsedVersionManifest,
        repo: String,
        branch: String,
        downloadUrl: String
    ): Result<Unit> {
        if (auth.currentUser == null) {
            return Result.failure(IllegalStateException("Conecte sua conta Google para sincronizar a versão no Firestore."))
        }
        return try {
            val data = hashMapOf<String, Any>(
                "latestVersionCode" to manifest.latestVersionCode,
                "latestVersionName" to manifest.latestVersionName,
                "downloadUrl" to downloadUrl.trim(),
                "releaseNotes" to manifest.releaseNotes.ifBlank { "Nova versão v${manifest.latestVersionName} disponível." },
                "forceUpdate" to manifest.forceUpdate,
                "githubRepo" to repo.trim(),
                "githubBranch" to branch.trim().ifBlank { DEFAULT_BRANCH },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("app_config")
                .document("version")
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "app_config/version")
            Result.failure(e)
        }
    }

    suspend fun syncCurrentBuildToCloud(
        preserveExistingUrl: String? = null,
        customReleaseNotes: String? = null
    ): Result<Unit> {
        if (auth.currentUser == null) {
            return Result.failure(IllegalStateException("Conecte sua conta Google para publicar a versão na nuvem."))
        }
        return try {
            val targetUrl = preserveExistingUrl?.trim().orEmpty()
            val savedRepo = getSavedGithubRepo()
            val savedBranch = getSavedGithubBranch()
            val data = hashMapOf<String, Any>(
                "latestVersionCode" to BuildConfig.VERSION_CODE.toLong(),
                "latestVersionName" to BuildConfig.VERSION_NAME,
                "releaseNotes" to (customReleaseNotes?.trim()?.takeIf { it.isNotBlank() }
                    ?: "v${BuildConfig.VERSION_NAME}: Sincronização automática de atualizações entre GitHub e Firestore."),
                "forceUpdate" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (targetUrl.isNotBlank()) {
                data["downloadUrl"] = targetUrl
            }
            if (savedRepo.isNotBlank()) {
                data["githubRepo"] = savedRepo
                data["githubBranch"] = savedBranch
            }
            firestore.collection("app_config")
                .document("version")
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "app_config/version")
            Result.failure(e)
        }
    }

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_GITHUB_REPO = "github_repo"
        private const val KEY_GITHUB_BRANCH = "github_branch"
        private const val DEFAULT_BRANCH = "main"
        private const val DEFAULT_APK_PATH = "app-debug.apk"

        fun normalizeGithubRepo(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return ""

            var cleaned = trimmed
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .removePrefix("github.com/")
                .removePrefix("raw.githubusercontent.com/")
                .trim('/')
                .removeSuffix(".git")

            val segments = cleaned.split('/').filter { it.isNotBlank() }
            if (segments.size < 2) return ""
            val owner = segments[0]
            val repo = segments[1]
            val validToken = Regex("^[A-Za-z0-9._-]+$")
            return if (validToken.matches(owner) && validToken.matches(repo)) {
                "$owner/$repo"
            } else {
                ""
            }
        }

        fun buildGitRefApiUrl(repo: String, branch: String = DEFAULT_BRANCH): String {
            return "https://api.github.com/repos/$repo/git/ref/heads/$branch"
        }

        fun buildContentsApiManifestUrl(repo: String, branch: String = DEFAULT_BRANCH): String {
            return "https://api.github.com/repos/$repo/contents/app-version.json?ref=$branch"
        }

        fun buildRawManifestUrl(repo: String, ref: String = DEFAULT_BRANCH, cacheBuster: Long = 0L): String {
            val base = "https://raw.githubusercontent.com/$repo/$ref/app-version.json"
            return if (cacheBuster > 0L) "$base?t=$cacheBuster" else base
        }

        fun buildApkDownloadUrl(repo: String, ref: String = DEFAULT_BRANCH, apkPath: String = DEFAULT_APK_PATH): String {
            val cleanPath = apkPath.trim().trimStart('/').ifBlank { DEFAULT_APK_PATH }
            return "https://github.com/$repo/raw/$ref/$cleanPath"
        }

        fun parseGitRefSha(jsonText: String): String? {
            val fromJson = try {
                val obj = JSONObject(jsonText)
                obj.optJSONObject("object")?.optString("sha", "").orEmpty().trim()
            } catch (_: Exception) {
                ""
            }
            if (fromJson.matches(Regex("^[0-9a-fA-F]{7,40}$"))) return fromJson
            val match = Regex(""""sha"\s*:\s*"([0-9a-fA-F]{7,40})"""").find(jsonText)
            return match?.groupValues?.getOrNull(1)
        }

        fun parseVersionJson(jsonText: String): ParsedVersionManifest? {
            return try {
                val obj = JSONObject(jsonText)
                val code = obj.optLong("latestVersionCode", -1L)
                if (code <= 0L) return null
                val name = obj.optString("latestVersionName", "1.0").ifBlank { "1.0" }
                val notes = obj.optString("releaseNotes", "Nova versão disponível.")
                val apkPath = obj.optString("apkPath", DEFAULT_APK_PATH).ifBlank { DEFAULT_APK_PATH }
                val force = obj.optBoolean("forceUpdate", false)
                ParsedVersionManifest(
                    latestVersionCode = code,
                    latestVersionName = name,
                    releaseNotes = notes,
                    apkPath = apkPath,
                    forceUpdate = force
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
