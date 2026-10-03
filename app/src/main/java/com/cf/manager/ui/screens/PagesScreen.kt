package com.cf.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.local.AccountStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen() {
    val context = LocalContext.current
    val storage = remember { AccountStorage(context) }
    val accounts = remember { storage.getAccounts() }
    val activeIdx = remember { storage.getActiveIndex() }
    val currAcc = accounts.getOrNull(activeIdx)
    val email = AppConfig.activeEmail.ifBlank { currAcc?.email ?: "" }
    val apiKey = AppConfig.activeApiKey.ifBlank { currAcc?.apiKey ?: "" }

    var projects by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedProject by remember { mutableStateOf("") }
    var isLoadingProjects by remember { mutableStateOf(false) }

    var projectNameInput by remember { mutableStateOf("") }
    var rawUrlInput by remember { mutableStateOf("") }
    var isFetchingRaw by remember { mutableStateOf(false) }

    var rawHtml by remember { mutableStateOf("<!DOCTYPE html>\n<html>\n<head><title>My Pages</title></head>\n<body>\n  <h1>Live from Android!</h1>\n</body>\n</html>") }
    var workerScript by remember { mutableStateOf("export default {\n  async fetch(req, env) {\n    return env.ASSETS.fetch(req);\n  }\n};") }

    var customDomainInput by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }
    var isDeploying by remember { mutableStateOf(false) }
    var isCheckingDomain by remember { mutableStateOf(false) }
    var domainAvailableMsg by remember { mutableStateOf("") }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                if (content.isNotBlank()) {
                    rawHtml = content
                    statusMsg = "📄 File HTML berhasil dimuat ke editor!"
                }
            } catch (e: Exception) {
                statusMsg = "Gagal membaca file: ${e.message}"
            }
        }
    }

    fun loadProjects() {
        if (email.isBlank() || apiKey.isBlank()) {
            statusMsg = "⚠️ Isi Email & API Key di tab Akun terlebih dahulu!"
            return
        }
        scope.launch {
            isLoadingProjects = true
            try {
                val res = ApiClient.api.listPagesProjects(email, apiKey)
                if (res.isSuccessful) {
                    val body = res.body()
                    val resultList = body?.getAsJsonArray("result")
                    val list = mutableListOf<String>()
                    resultList?.forEach {
                        val name = it.asJsonObject.get("name")?.asString
                        if (!name.isNullOrBlank()) list.add(name)
                    }
                    projects = list
                    if (list.isNotEmpty() && selectedProject.isEmpty()) {
                        selectedProject = list[0]
                        projectNameInput = list[0]
                    }
                } else {
                    statusMsg = "Gagal memuat project: HTTP ${res.code()}"
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            } finally {
                isLoadingProjects = false
            }
        }
    }

    LaunchedEffect(email, apiKey) {
        if (email.isNotEmpty() && apiKey.isNotEmpty()) {
            loadProjects()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HEADER & REFRESH
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("📄 Cloudflare Pages Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Kelola project statis & SSR Functions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = { loadProjects() }, enabled = !isLoadingProjects) {
                    Text(if (isLoadingProjects) "⏳" else "🔄")
                }
            }
        }

        // 2. LIST PROJECT PAGES
        item {
            Text("Daftar Proyek Pages (${projects.size}):", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))

            if (projects.isEmpty()) {
                Text(
                    text = if (isLoadingProjects) "Sedang mengambil data..." else "Belum ada project Pages. Buat baru di bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    projects.forEach { pName ->
                        FilterChip(
                            selected = (selectedProject == pName),
                            onClick = {
                                selectedProject = pName
                                projectNameInput = pName
                            },
                            label = { Text(pName) }
                        )
                    }
                }
            }
        }

        // 3. FORM PROYEK & TARIK URL RAW
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⚙️ Konfigurasi Proyek", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = projectNameInput,
                            onValueChange = {
                                projectNameInput = it.lowercase().trim()
                                domainAvailableMsg = ""
                            },
                            label = { Text("Nama Project (.pages.dev)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                if (projectNameInput.isBlank()) return@OutlinedButton
                                scope.launch {
                                    isCheckingDomain = true
                                    domainAvailableMsg = "Memeriksa..."
                                    try {
                                        val res = ApiClient.api.checkSubdomain(projectNameInput)
                                        if (res.isSuccessful) {
                                            val avail = res.body()?.get("available")?.asBoolean ?: false
                                            domainAvailableMsg = if (avail) "✅ Tersedia!" else "❌ Terpakai!"
                                        } else {
                                            domainAvailableMsg = "Gagal cek"
                                        }
                                    } catch (e: Exception) {
                                        domainAvailableMsg = "Error: ${e.message}"
                                    } finally {
                                        isCheckingDomain = false
                                    }
                                }
                            },
                            enabled = !isCheckingDomain && projectNameInput.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Cek DoH")
                        }
                    }

                    if (domainAvailableMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(domainAvailableMsg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // BARIS BARU: TARIK TEMPLATE RAW DARI URL (GITHUB / RAW URL)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = rawUrlInput,
                            onValueChange = { rawUrlInput = it.trim() },
                            label = { Text("URL Raw HTML (GitHub / Link)") },
                            placeholder = { Text("https://raw.githubusercontent.com/.../index.html") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (rawUrlInput.isBlank()) return@Button
                                scope.launch {
                                    isFetchingRaw = true
                                    statusMsg = "Mengunduh template dari URL..."
                                    try {
                                        val fetched = withContext(Dispatchers.IO) { URL(rawUrlInput).readText() }
                                        rawHtml = fetched
                                        statusMsg = "✅ Berhasil memuat HTML dari URL RAW!"
                                    } catch (e: Exception) {
                                        statusMsg = "Gagal fetch RAW: ${e.message}"
                                    } finally {
                                        isFetchingRaw = false
                                    }
                                }
                            },
                            enabled = !isFetchingRaw && rawUrlInput.isNotBlank(),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(if (isFetchingRaw) "..." else "Tarik")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Opsi Pilih File HTML dari HP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Isi index.html:", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { filePickerLauncher.launch("text/html") }) {
                            Text("📂 Pilih File HP")
                        }
                    }

                    OutlinedTextField(
                        value = rawHtml,
                        onValueChange = { rawHtml = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("_worker.js Function (Opsional / SSR):", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = workerScript,
                        onValueChange = { workerScript = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val target = projectNameInput.trim().lowercase()
                                if (target.isBlank()) {
                                    statusMsg = "Nama project tidak boleh kosong!"
                                    return@Button
                                }
                                scope.launch {
                                    isDeploying = true
                                    statusMsg = "Mendeploy asset & binding JWT via Worker..."
                                    try {
                                        val payload = mapOf("html" to rawHtml, "workerCode" to workerScript)
                                        val res = ApiClient.api.quickDeployPages(target, email, apiKey, payload)
                                        if (res.isSuccessful && res.body()?.success == true) {
                                            statusMsg = "🚀 Deploy sukses! URL: https://$target.pages.dev"
                                            loadProjects()
                                        } else {
                                            statusMsg = "Gagal deploy: HTTP ${res.code()}"
                                        }
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    } finally {
                                        isDeploying = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isDeploying && projectNameInput.isNotBlank()
                        ) {
                            Text(if (isDeploying) "Mendeploy..." else "🚀 Deploy ke Pages")
                        }

                        if (selectedProject.isNotBlank()) {
                            Button(
                                onClick = {
                                    projectToDelete = selectedProject
                                    showDeleteConfirm = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("🗑")
                            }
                        }
                    }
                }
            }
        }

        // 4. CUSTOM DOMAIN PAGES
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🌐 Custom Domain untuk: ${selectedProject.ifBlank { "(Pilih project di atas)" }}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customDomainInput,
                            onValueChange = { customDomainInput = it.lowercase().trim() },
                            label = { Text("Domain (cth: blog.domain.com)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (selectedProject.isBlank() || customDomainInput.isBlank()) {
                                    statusMsg = "Pilih project dan masukkan domain!"
                                    return@Button
                                }
                                scope.launch {
                                    statusMsg = "Menghubungkan custom domain..."
                                    try {
                                        val res = ApiClient.api.addPagesCustomDomain(selectedProject, email, apiKey, mapOf("domain" to customDomainInput))
                                        if (res.isSuccessful) {
                                            statusMsg = "✅ Custom domain $customDomainInput ditambahkan!"
                                            customDomainInput = ""
                                        } else {
                                            statusMsg = "Gagal: HTTP ${res.code()}"
                                        }
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    }
                                }
                            },
                            enabled = selectedProject.isNotBlank() && customDomainInput.isNotBlank()
                        ) {
                            Text("Hubungkan")
                        }
                    }
                }
            }
        }

        // 5. STATUS BOX
        if (statusMsg.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }

    // DIALOG KONFIRMASI HAPUS PROJECT
    if (showDeleteConfirm && projectToDelete.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Project Pages?") },
            text = { Text("Yakin ingin menghapus project '$projectToDelete' secara permanen?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        statusMsg = "Menghapus project '$projectToDelete'..."
                        try {
                            val res = ApiClient.api.deletePagesProject(projectToDelete, email, apiKey)
                            if (res.isSuccessful) {
                                statusMsg = "🗑 Project '$projectToDelete' berhasil dihapus!"
                                if (selectedProject == projectToDelete) {
                                    selectedProject = ""
                                    projectNameInput = ""
                                }
                                loadProjects()
                            } else {
                                statusMsg = "Gagal menghapus: HTTP ${res.code()}"
                            }
                        } catch (e: Exception) {
                            statusMsg = "Error: ${e.message}"
                        }
                    }
                }) {
                    Text("Ya, Hapus!", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
