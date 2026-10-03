package com.cf.manager.ui.screens

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
import com.cf.manager.data.model.ZoneItem
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class WorkerDomainItem(
    val id: String,
    val hostname: String,
    val service: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerEditorScreen() {
    val context = LocalContext.current
    val storage = remember { AccountStorage(context) }
    val accounts = remember { storage.getAccounts() }
    val activeIdx = remember { storage.getActiveIndex() }
    val currAcc = accounts.getOrNull(activeIdx)
    val email = AppConfig.activeEmail.ifBlank { currAcc?.email ?: "" }
    val apiKey = AppConfig.activeApiKey.ifBlank { currAcc?.apiKey ?: "" }

    var workerList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedWorker by remember { mutableStateOf("") }
    var workerNameInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("export default {\n  async fetch(request, env) {\n    return new Response(\"Hello from CF Worker!\");\n  }\n};") }
    var rawUrlInput by remember { mutableStateOf("https://raw.ddfathu.cc.cd/raw/4qmj2d/nauticamodv8") }

    // State Domain Rute Worker
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var zoneExpanded by remember { mutableStateOf(false) }
    var customSubdomainInput by remember { mutableStateOf("") }
    var workerDomains by remember { mutableStateOf<List<WorkerDomainItem>>(emptyList()) }
    var isAddingDomain by remember { mutableStateOf(false) }

    var statusMsg by remember { mutableStateOf("") }
    var isLoadingList by remember { mutableStateOf(false) }
    var isDeploying by remember { mutableStateOf(false) }
    var isFetchingCode by remember { mutableStateOf(false) }
    var isFetchingRaw by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadWorkerDomains() {
        scope.launch {
            try {
                val res = ApiClient.api.listWorkerDomains(email, apiKey)
                if (res.isSuccessful) {
                    val rawList = res.body() ?: emptyList()
                    val parsed = rawList.mapNotNull { item ->
                        val m = item as? Map<*, *>
                        val id = m?.get("id")?.toString() ?: return@mapNotNull null
                        val host = m["hostname"]?.toString() ?: return@mapNotNull null
                        val srv = m["service"]?.toString() ?: ""
                        WorkerDomainItem(id, host, srv)
                    }
                    workerDomains = parsed
                }
            } catch (_: Exception) {}
        }
    }

    fun loadWorkers() {
        if (email.isBlank() || apiKey.isBlank()) {
            statusMsg = "⚠️ Isi Email & API Key di tab Akun terlebih dahulu!"
            return
        }
        scope.launch {
            isLoadingList = true
            statusMsg = "Memuat data worker & zone..."
            try {
                val res = ApiClient.api.listWorkers(email, apiKey)
                if (res.isSuccessful) {
                    val list = res.body() ?: emptyList()
                    workerList = list
                    if (list.isNotEmpty()) {
                        statusMsg = "✅ Ditemukan ${list.size} worker."
                        if (selectedWorker.isEmpty()) {
                            selectedWorker = list[0]
                            workerNameInput = list[0]
                        }
                    } else {
                        statusMsg = "Belum ada worker di akun ini. Buat baru di bawah!"
                    }
                }

                val zRes = ApiClient.api.listZones(email, apiKey)
                if (zRes.isSuccessful) {
                    zones = zRes.body() ?: emptyList()
                    if (zones.isNotEmpty() && selectedZone == null) {
                        selectedZone = zones[0]
                    }
                }
                loadWorkerDomains()
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            } finally {
                isLoadingList = false
            }
        }
    }

    LaunchedEffect(email, apiKey) {
        if (email.isNotEmpty() && apiKey.isNotEmpty()) {
            loadWorkers()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. HEADER & REFRESH ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("⚡ Worker Manager & Editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Kelola script worker, routing domain & deploy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = { loadWorkers() }, enabled = !isLoadingList) {
                    Text(if (isLoadingList) "⏳" else "🔄")
                }
            }
        }

        // --- 2. LIST WORKER (CHIPS) ---
        item {
            Text("Daftar Worker Tersedia (${workerList.size}):", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))

            if (workerList.isEmpty()) {
                Text(
                    text = if (isLoadingList) "Sedang mengambil data..." else "Tidak ada worker / belum dimuat.",
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
                    workerList.forEach { wName ->
                        FilterChip(
                            selected = (selectedWorker == wName),
                            onClick = {
                                selectedWorker = wName
                                workerNameInput = wName
                            },
                            label = { Text(wName) }
                        )
                    }
                }
            }
        }

        // --- 3. EDITOR & AKSI WORKER ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🛠 Konfigurasi Worker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = workerNameInput,
                        onValueChange = {
                            workerNameInput = it.lowercase().trim()
                            if (it != selectedWorker) selectedWorker = ""
                        },
                        label = { Text("Nama Worker Target") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val target = workerNameInput.trim().lowercase()
                                if (target.isBlank()) return@Button
                                scope.launch {
                                    isFetchingCode = true
                                    statusMsg = "Mengambil kode worker '$target'..."
                                    try {
                                        val call = ApiClient.api.getWorkerCode(email, apiKey, target)
                                        if (call.isSuccessful) {
                                            val fetchedCode = call.body()?.string() ?: ""
                                            if (fetchedCode.isNotBlank()) {
                                                codeInput = fetchedCode
                                                statusMsg = "✅ Kode worker '$target' berhasil ditarik!"
                                            }
                                        } else {
                                            statusMsg = "Gagal HTTP ${call.code()} saat tarik kode."
                                        }
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    } finally {
                                        isFetchingCode = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isFetchingCode && workerNameInput.isNotBlank()
                        ) {
                            Text(if (isFetchingCode) "..." else "📥 Tarik Kode")
                        }

                        Button(
                            onClick = {
                                if (workerNameInput.isBlank()) return@Button
                                showDeleteConfirm = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            enabled = workerNameInput.isNotBlank()
                        ) {
                            Text("🗑 Hapus")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = rawUrlInput,
                            onValueChange = { rawUrlInput = it.trim() },
                            label = { Text("URL Raw GitHub / Preset") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (rawUrlInput.isBlank()) return@Button
                                scope.launch {
                                    isFetchingRaw = true
                                    statusMsg = "Mengunduh script raw..."
                                    try {
                                        val content = withContext(Dispatchers.IO) { URL(rawUrlInput).readText() }
                                        codeInput = content
                                        statusMsg = "✅ Berhasil memuat script RAW!"
                                    } catch (e: Exception) {
                                        statusMsg = "Gagal fetch RAW: ${e.message}"
                                    } finally {
                                        isFetchingRaw = false
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 4.dp),
                            enabled = !isFetchingRaw && rawUrlInput.isNotBlank()
                        ) {
                            Text(if (isFetchingRaw) "..." else "Tarik")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Script JavaScript (Worker Engine):", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val target = workerNameInput.trim().lowercase()
                            if (target.isBlank()) {
                                statusMsg = "Nama target worker tidak boleh kosong!"
                                return@Button
                            }
                            scope.launch {
                                isDeploying = true
                                statusMsg = "Mendeploy ke Cloudflare..."
                                try {
                                    val res = ApiClient.api.deployWorker(email, apiKey, target, codeInput)
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        statusMsg = "🎉 Berhasil deploy: ${res.body()?.worker_url ?: target}"
                                        loadWorkers()
                                    } else {
                                        statusMsg = "Gagal deploy: ${res.body()?.msg ?: res.message()}"
                                    }
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isDeploying = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDeploying && workerNameInput.isNotBlank()
                    ) {
                        Text(if (isDeploying) "Mendeploy..." else "🚀 Deploy / Simpan Worker")
                    }
                }
            }
        }

        // --- 4. SECTION FITUR BARU: DOMAIN & ROUTE WORKER ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🌐 Custom Domain & Rute Worker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Hubungkan Worker '${workerNameInput.ifBlank { "(Pilih Worker)" }}' ke Domain Kustom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(10.dp))

                    if (zones.size > 1) {
                        ExposedDropdownMenuBox(
                            expanded = zoneExpanded,
                            onExpandedChange = { zoneExpanded = !zoneExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedZone?.name ?: "Pilih Domain",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Pilih Domain Cloudflare") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = zoneExpanded,
                                onDismissRequest = { zoneExpanded = false }
                            ) {
                                zones.forEach { z ->
                                    DropdownMenuItem(
                                        text = { Text(z.name) },
                                        onClick = {
                                            selectedZone = z
                                            zoneExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customSubdomainInput,
                            onValueChange = { customSubdomainInput = it.lowercase().trim() },
                            label = { Text("Subdomain") },
                            placeholder = { Text("cth: api / vpn / @" ) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ".${selectedZone?.name ?: "domain.com"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val target = workerNameInput.trim().lowercase()
                            val z = selectedZone
                            if (target.isBlank() || z == null) {
                                statusMsg = "Pilih worker dan domain terlebih dahulu!"
                                return@Button
                            }
                            scope.launch {
                                isAddingDomain = true
                                statusMsg = "Menghubungkan domain ke Worker..."
                                try {
                                    val sub = if (customSubdomainInput == "@") "" else customSubdomainInput
                                    val res = ApiClient.api.addWorkerDomain(email, apiKey, target, sub, z.name, z.id)
                                    statusMsg = res.body()?.msg ?: "Domain rute berhasil dipasang!"
                                    customSubdomainInput = ""
                                    loadWorkerDomains()
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isAddingDomain = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAddingDomain && workerNameInput.isNotBlank() && selectedZone != null
                    ) {
                        Text(if (isAddingDomain) "Menghubungkan..." else "🔗 Pasang Domain Rute")
                    }
                }
            }
        }

        // --- 5. DAFTAR DOMAIN WORKER AKTIF ---
        val matchedDomains = workerDomains.filter { it.service == workerNameInput }
        if (matchedDomains.isNotEmpty()) {
            item {
                Text("Domain Terhubung ke '${workerNameInput}':", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            items(matchedDomains) { d ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("🔗 ${d.hostname}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Service: ${d.service}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                statusMsg = "Mencopot domain rute..."
                                try {
                                    val res = ApiClient.api.deleteWorkerDomain(d.id, email, apiKey)
                                    if (res.isSuccessful) {
                                        statusMsg = "🗑 Domain rute dicopot!"
                                        loadWorkerDomains()
                                    }
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                }
                            }
                        }) {
                            Text("🗑")
                        }
                    }
                }
            }
        }

        // --- 6. STATUS BOX ---
        if (statusMsg.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
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

    // --- DIALOG KONFIRMASI HAPUS WORKER ---
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Yakin ingin menghapus worker '${workerNameInput}' secara permanen?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        statusMsg = "Menghapus worker '${workerNameInput}'..."
                        try {
                            val res = ApiClient.api.deleteWorker(email, apiKey, workerNameInput)
                            if (res.isSuccessful && res.body()?.success == true) {
                                statusMsg = "🗑 Worker '${workerNameInput}' berhasil dihapus!"
                                workerNameInput = ""
                                selectedWorker = ""
                                loadWorkers()
                            } else {
                                statusMsg = "Gagal menghapus: ${res.body()?.msg ?: res.message()}"
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
