package com.cf.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class WorkerDomainItem(
    val id: String = "",
    val hostname: String = "",
    val service: String = ""
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

    // Sub-tab: 0 = Daftar & Edit Worker, 1 = Buat Worker Baru
    var selectedWorkerTab by remember { mutableStateOf(0) }

    var workerList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    // State untuk Form Edit Worker
    var editingWorkerName by remember { mutableStateOf<String?>(null) }
    var editCodeInput by remember { mutableStateOf("") }
    var isFetchingEditCode by remember { mutableStateOf(false) }
    var isUpdatingWorker by remember { mutableStateOf(false) }

    // State untuk Form Buat Worker Baru
    var newWorkerName by remember { mutableStateOf("") }
    var newWorkerCode by remember { mutableStateOf("export default {\n  async fetch(request, env) {\n    return new Response(\"Hello from new CF Worker!\");\n  }\n};") }
    var newRawUrl by remember { mutableStateOf("https://raw.ddfathu.cc.cd/raw/4qmj2d/nauticamodv8") }
    var isFetchingNewRaw by remember { mutableStateOf(false) }
    var isCreatingWorker by remember { mutableStateOf(false) }

    // State Domain / Rute Worker
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var zoneExpanded by remember { mutableStateOf(false) }
    var customSubdomainInput by remember { mutableStateOf("") }
    var workerDomains by remember { mutableStateOf<List<WorkerDomainItem>>(emptyList()) }
    var isAddingDomain by remember { mutableStateOf(false) }
    var activeDomainWorkerTarget by remember { mutableStateOf("") }

    // State Dialog Konfirmasi Hapus
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var workerToDelete by remember { mutableStateOf("") }

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
            try {
                val res = ApiClient.api.listWorkers(email, apiKey)
                if (res.isSuccessful) {
                    workerList = res.body() ?: emptyList()
                } else {
                    statusMsg = "Gagal memuat worker: HTTP ${res.code()}"
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
                    Text("⚡ Cloudflare Workers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Kelola script worker dan rute domain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = { loadWorkers() }, enabled = !isLoadingList) {
                    Text(if (isLoadingList) "⏳" else "🔄")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SUB-TAB SWITCHER MANDIRI (Daftar & Edit VS Buat Baru)
            PrimaryTabRow(selectedTabIndex = selectedWorkerTab) {
                Tab(
                    selected = selectedWorkerTab == 0,
                    onClick = { selectedWorkerTab = 0 },
                    text = { Text("📋 Daftar & Edit (${workerList.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedWorkerTab == 1,
                    onClick = {
                        selectedWorkerTab = 1
                        editingWorkerName = null
                    },
                    text = { Text("➕ Buat Worker Baru", fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        // --- SUB-TAB 0: DAFTAR & EDIT WORKER YANG SUDAH ADA ---
        if (selectedWorkerTab == 0) {
            // MODE SEDANG EDIT SATU WORKER
            if (editingWorkerName != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✏️ Edit Worker: $editingWorkerName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { editingWorkerName = null }) {
                                    Text("✖ Tutup Editor")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Script JavaScript (Worker Engine):", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = editCodeInput,
                                onValueChange = { editCodeInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val target = editingWorkerName ?: return@Button
                                        scope.launch {
                                            isUpdatingWorker = true
                                            statusMsg = "Memperbarui kode worker '$target'..."
                                            try {
                                                val res = ApiClient.api.deployWorker(email, apiKey, target, editCodeInput)
                                                if (res.isSuccessful && res.body()?.success == true) {
                                                    statusMsg = "🎉 Berhasil memperbarui worker: $target"
                                                    editingWorkerName = null
                                                    loadWorkers()
                                                } else {
                                                    statusMsg = "Gagal: ${res.body()?.msg ?: res.message()}"
                                                }
                                            } catch (e: Exception) {
                                                statusMsg = "Error: ${e.message}"
                                            } finally {
                                                isUpdatingWorker = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isUpdatingWorker
                                ) {
                                    Text(if (isUpdatingWorker) "Menyimpan..." else "💾 Simpan Pembaruan")
                                }

                                OutlinedButton(onClick = { editingWorkerName = null }) {
                                    Text("Batal")
                                }
                            }
                        }
                    }
                }
            }

            // SECTION PASANG DOMAIN KE WORKER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🌐 Hubungkan Worker ke Custom Domain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = activeDomainWorkerTarget,
                            onValueChange = { activeDomainWorkerTarget = it.lowercase().trim() },
                            label = { Text("Worker Target (Ketik / Klik nama worker di bawah)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (zones.size > 1) {
                            ExposedDropdownMenuBox(
                                expanded = zoneExpanded,
                                onExpandedChange = { zoneExpanded = !zoneExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedZone?.name ?: "Pilih Domain",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Domain Utama") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
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
                                label = { Text("Subdomain (cth: vpn / api / @)") },
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
                                val target = activeDomainWorkerTarget.trim().lowercase()
                                val z = selectedZone
                                if (target.isBlank() || z == null) {
                                    statusMsg = "Isi nama worker target dan pilih domain!"
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
                            enabled = !isAddingDomain && activeDomainWorkerTarget.isNotBlank() && selectedZone != null
                        ) {
                            Text(if (isAddingDomain) "Menghubungkan..." else "🔗 Pasang Domain Rute")
                        }
                    }
                }
            }

            // LIST KARTU WORKER YANG SUDAH ADA
            item {
                Text("Daftar Worker Aktif (${workerList.size}):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (workerList.isEmpty() && !isLoadingList) {
                item {
                    Text("Belum ada worker di akun ini. Pindah ke tab '➕ Buat Worker Baru' di atas untuk membuat!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            items(items = workerList, key = { it }) { wName ->
                val linkedRoutes = workerDomains.filter { it.service == wName }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⚡ $wName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (linkedRoutes.isNotEmpty()) {
                                    Text(
                                        text = "🌐 ${linkedRoutes.joinToString { it.hostname }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text("Belum terhubung custom domain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tombol Edit Script
                            Button(
                                onClick = {
                                    scope.launch {
                                        statusMsg = "Mengambil kode worker '$wName'..."
                                        try {
                                            val call = ApiClient.api.getWorkerCode(email, apiKey, wName)
                                            if (call.isSuccessful) {
                                                editCodeInput = call.body()?.string() ?: ""
                                                editingWorkerName = wName
                                                statusMsg = "Kode worker '$wName' siap diedit di atas!"
                                            } else {
                                                statusMsg = "Gagal mengambil kode: HTTP ${call.code()}"
                                            }
                                        } catch (e: Exception) {
                                            statusMsg = "Error: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("✏️ Edit Script")
                            }

                            // Tombol Pilih untuk Rute Domain
                            OutlinedButton(
                                onClick = {
                                    activeDomainWorkerTarget = wName
                                    statusMsg = "Worker '$wName' dipilih untuk pasang domain."
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("🌐 Rute")
                            }

                            // Tombol Hapus Worker
                            Button(
                                onClick = {
                                    workerToDelete = wName
                                    showDeleteConfirm = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("🗑")
                            }
                        }
                    }
                }
            }
        }

        // --- SUB-TAB 1: FORM BUAT WORKER BARU DARI NOL ---
        if (selectedWorkerTab == 1) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🚀 Formulir Buat Worker Baru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Isi nama dan script worker yang ingin kamu pasang", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newWorkerName,
                            onValueChange = { newWorkerName = it.lowercase().trim() },
                            label = { Text("Nama Worker Baru (cth: proxy-v2ray / test-api)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Ambil template RAW dari URL
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newRawUrl,
                                onValueChange = { newRawUrl = it.trim() },
                                label = { Text("URL Raw GitHub (Template / Preset)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newRawUrl.isBlank()) return@Button
                                    scope.launch {
                                        isFetchingNewRaw = true
                                        statusMsg = "Mengunduh script dari URL..."
                                        try {
                                            val content = withContext(Dispatchers.IO) { URL(newRawUrl).readText() }
                                            newWorkerCode = content
                                            statusMsg = "✅ Template RAW berhasil dimasukkan ke editor!"
                                        } catch (e: Exception) {
                                            statusMsg = "Gagal ambil RAW: ${e.message}"
                                        } finally {
                                            isFetchingNewRaw = false
                                        }
                                    }
                                },
                                modifier = Modifier.padding(top = 4.dp),
                                enabled = !isFetchingNewRaw && newRawUrl.isNotBlank()
                            ) {
                                Text(if (isFetchingNewRaw) "..." else "Tarik")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Script JavaScript Worker Engine:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newWorkerCode,
                            onValueChange = { newWorkerCode = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val target = newWorkerName.trim().lowercase()
                                if (target.isBlank()) {
                                    statusMsg = "⚠️ Berikan nama untuk worker barumu!"
                                    return@Button
                                }
                                scope.launch {
                                    isCreatingWorker = true
                                    statusMsg = "Mendeploy worker baru '$target' ke Cloudflare..."
                                    try {
                                        val res = ApiClient.api.deployWorker(email, apiKey, target, newWorkerCode)
                                        if (res.isSuccessful && res.body()?.success == true) {
                                            statusMsg = "🎉 Berhasil membuat worker baru: $target!"
                                            newWorkerName = ""
                                            selectedWorkerTab = 0 // Pindah ke tab daftar worker
                                            loadWorkers()
                                        } else {
                                            statusMsg = "Gagal deploy: ${res.body()?.msg ?: res.message()}"
                                        }
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    } finally {
                                        isCreatingWorker = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCreatingWorker && newWorkerName.isNotBlank()
                        ) {
                            Text(if (isCreatingWorker) "Sedang Membuat..." else "🚀 Deploy Worker Baru Sekarang")
                        }
                    }
                }
            }
        }

        // --- STATUS BOX ---
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
    if (showDeleteConfirm && workerToDelete.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Yakin ingin menghapus worker '$workerToDelete' secara permanen dari akun Cloudflare?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        statusMsg = "Menghapus worker '$workerToDelete'..."
                        try {
                            val res = ApiClient.api.deleteWorker(email, apiKey, workerToDelete)
                            if (res.isSuccessful && res.body()?.success == true) {
                                statusMsg = "🗑 Worker '$workerToDelete' berhasil dihapus!"
                                if (editingWorkerName == workerToDelete) editingWorkerName = null
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
