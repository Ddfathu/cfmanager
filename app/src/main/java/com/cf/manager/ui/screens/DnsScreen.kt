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
import com.cf.manager.data.model.DnsRecordItem
import com.cf.manager.data.model.ZoneItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen() {
    val context = LocalContext.current
    val storage = remember { AccountStorage(context) }
    val accounts = remember { storage.getAccounts() }
    val activeIdx = remember { storage.getActiveIndex() }
    val currAcc = accounts.getOrNull(activeIdx)
    val email = AppConfig.activeEmail.ifBlank { currAcc?.email ?: "" }
    val apiKey = AppConfig.activeApiKey.ifBlank { currAcc?.apiKey ?: "" }

    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var zoneExpanded by remember { mutableStateOf(false) }

    var dnsList by remember { mutableStateOf<List<DnsRecordItem>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(false) }

    // State Form Input
    val recordTypes = listOf("A", "AAAA", "CNAME", "TXT", "NS", "MX", "SRV", "CAA")
    var selectedType by remember { mutableStateOf("A") }
    var typeExpanded by remember { mutableStateOf(false) }

    var nameInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
    var proxied by remember { mutableStateOf(true) }
    var ttlInput by remember { mutableStateOf(1) } // 1 = Auto

    // State Edit Mode
    var editingRecordId by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    // State Dialog Hapus
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<DnsRecordItem?>(null) }

    val scope = rememberCoroutineScope()

    fun loadRecords(zoneId: String) {
        scope.launch {
            isLoadingList = true
            try {
                val res = ApiClient.api.listDns(email, apiKey, zoneId)
                if (res.isSuccessful) {
                    dnsList = res.body() ?: emptyList()
                } else {
                    statusMsg = "Gagal memuat DNS: HTTP ${res.code()}"
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            } finally {
                isLoadingList = false
            }
        }
    }

    fun loadZones() {
        if (email.isBlank() || apiKey.isBlank()) {
            statusMsg = "⚠️ Isi Email & API Key di tab Akun terlebih dahulu!"
            return
        }
        scope.launch {
            try {
                val res = ApiClient.api.listZones(email, apiKey)
                if (res.isSuccessful) {
                    val list = res.body() ?: emptyList()
                    zones = list
                    if (list.isNotEmpty()) {
                        if (selectedZone == null) {
                            selectedZone = list[0]
                            loadRecords(list[0].id)
                        }
                    } else {
                        statusMsg = "Tidak ditemukan domain (zone) pada akun ini."
                    }
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            }
        }
    }

    LaunchedEffect(email, apiKey) {
        if (email.isNotEmpty() && apiKey.isNotEmpty()) {
            loadZones()
        }
    }

    fun resetForm() {
        editingRecordId = null
        selectedType = "A"
        nameInput = ""
        contentInput = ""
        proxied = true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. HEADER & ZONE SELECTOR ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("🌐 Kelola DNS Record", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Domain: ${selectedZone?.name ?: "(Pilih Zone)"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { selectedZone?.let { loadRecords(it.id) } }, enabled = !isLoadingList && selectedZone != null) {
                    Text(if (isLoadingList) "⏳" else "🔄")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dropdown Zone Selector (Jika akun punya banyak domain)
            if (zones.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = zoneExpanded,
                    onExpandedChange = { zoneExpanded = !zoneExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedZone?.name ?: "Pilih Domain",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pilih Domain Aktif") },
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
                                    resetForm()
                                    loadRecords(z.id)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // --- 2. FORM TAMBAH / EDIT DNS RECORD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (editingRecordId != null) "✏️ Edit Record: $nameInput" else "➕ Tambah DNS Record",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Dropdown Tipe Record (A, CNAME, TXT, dll)
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded },
                            modifier = Modifier.width(115.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipe") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                recordTypes.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            selectedType = t
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Input Name / Subdomain
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it.trim() },
                            label = { Text("Name / Sub") },
                            placeholder = { Text("@ atau sub") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input Target Content
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it.trim() },
                        label = { Text("Content / Target IP / Hostname") },
                        placeholder = { Text("192.0.2.1 atau domain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Toggle Proxy Cloudflare (Orange Cloud)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = proxied,
                                onCheckedChange = { proxied = it }
                            )
                            Text(
                                text = if (proxied) "☁️ Proxy Aktif (Orange Cloud)" else "⚪ DNS Only (Bypass)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tombol Simpan / Update & Batal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val zone = selectedZone
                                if (zone == null || nameInput.isBlank() || contentInput.isBlank()) {
                                    statusMsg = "⚠️ Domain, Name, dan Content wajib diisi!"
                                    return@Button
                                }
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val isEdit = (editingRecordId != null)
                                        statusMsg = if (isEdit) "Memperbarui DNS record..." else "Menambahkan DNS record..."
                                        
                                        val res = if (isEdit) {
                                            ApiClient.api.updateDns(
                                                email, apiKey, zone.id, editingRecordId!!,
                                                selectedType, nameInput, contentInput, ttlInput, proxied.toString()
                                            )
                                        } else {
                                            ApiClient.api.addDns(
                                                email, apiKey, zone.id,
                                                selectedType, nameInput, contentInput, ttlInput, proxied.toString()
                                            )
                                        }

                                        if (res.isSuccessful && res.body()?.success == true) {
                                            statusMsg = res.body()?.msg ?: "Berhasil disimpan!"
                                            resetForm()
                                            loadRecords(zone.id)
                                        } else {
                                            statusMsg = "Gagal: ${res.body()?.msg ?: res.message()}"
                                        }
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving && selectedZone != null && nameInput.isNotBlank() && contentInput.isNotBlank()
                        ) {
                            Text(if (isSaving) "Menyimpan..." else if (editingRecordId != null) "💾 Update Record" else "➕ Simpan DNS Record")
                        }

                        if (editingRecordId != null) {
                            OutlinedButton(onClick = { resetForm() }) {
                                Text("Batal")
                            }
                        }
                    }
                }
            }
        }

        // --- 3. STATUS MESSAGE ---
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

        // --- 4. LIST DNS RECORDS ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daftar Record (${dnsList.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(selectedZone?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        if (dnsList.isEmpty() && !isLoadingList) {
            item {
                Text("Belum ada DNS record atau data belum dimuat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        items(dnsList) { r ->
            val isBeingEdited = (editingRecordId == r.id)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBeingEdited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "[${r.type}] ",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = r.name,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = r.content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (r.proxied) "☁️ Proxied" else "⚪ DNS Only",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (r.proxied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tombol Edit Record
                        IconButton(onClick = {
                            editingRecordId = r.id
                            selectedType = r.type
                            nameInput = r.name
                            contentInput = r.content
                            proxied = r.proxied
                            ttlInput = r.ttl
                        }) {
                            Text("✏️")
                        }

                        // Tombol Hapus Record
                        IconButton(onClick = {
                            recordToDelete = r
                            showDeleteDialog = true
                        }) {
                            Text("🗑")
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG KONFIRMASI HAPUS RECORD ---
    if (showDeleteDialog && recordToDelete != null) {
        val target = recordToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus DNS Record?") },
            text = { Text("Yakin ingin menghapus record [${target.type}] ${target.name} (${target.content})?") },
            confirmButton = {
                TextButton(onClick = {
                    val zone = selectedZone
                    showDeleteDialog = false
                    if (zone != null) {
                        scope.launch {
                            statusMsg = "Menghapus record ${target.name}..."
                            try {
                                val res = ApiClient.api.deleteDns(email, apiKey, zone.id, target.id)
                                if (res.isSuccessful && res.body()?.success == true) {
                                    statusMsg = "🗑 Record berhasil dihapus!"
                                    if (editingRecordId == target.id) resetForm()
                                    loadRecords(zone.id)
                                } else {
                                    statusMsg = "Gagal: ${res.body()?.msg ?: res.message()}"
                                }
                            } catch (e: Exception) {
                                statusMsg = "Error: ${e.message}"
                            }
                        }
                    }
                }) {
                    Text("Ya, Hapus!", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
