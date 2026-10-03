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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.local.AccountStorage
import com.cf.manager.data.model.ZoneItem
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

data class DestinationAddress(
    val id: String,
    val email: String,
    val verified: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen() {
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

    var workers by remember { mutableStateOf<List<String>>(emptyList()) }
    var destinations by remember { mutableStateOf<List<DestinationAddress>>(emptyList()) }

    var newForwardEmail by remember { mutableStateOf("") }
    
    // Catch-All Config
    var catchAllEnabled by remember { mutableStateOf(true) }
    var catchAllAction by remember { mutableStateOf("forward") } // "forward" atau "worker"
    var selectedTargetEmail by remember { mutableStateOf("") }
    var selectedTargetWorker by remember { mutableStateOf("") }
    var targetExpanded by remember { mutableStateOf(false) }

    var statusMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadDestinationsAndWorkers() {
        scope.launch {
            try {
                // Ambil daftar worker aktif untuk target catch-all
                val wRes = ApiClient.api.listWorkers(email, apiKey)
                if (wRes.isSuccessful) {
                    workers = wRes.body() ?: emptyList()
                    if (workers.isNotEmpty() && selectedTargetWorker.isEmpty()) {
                        selectedTargetWorker = workers[0]
                    }
                }
                // Ambil daftar email destinasi yang terdaftar di akun
                val dRes = ApiClient.api.listEmailDestinations(email, apiKey)
                if (dRes.isSuccessful) {
                    val rawList = dRes.body() ?: emptyList()
                    val parsed = rawList.mapNotNull {
                        val m = it as? Map<*, *>
                        val id = m?.get("id")?.toString() ?: return@mapNotNull null
                        val mail = m["email"]?.toString() ?: return@mapNotNull null
                        val ver = m["verified"]?.toString()
                        DestinationAddress(id, mail, ver)
                    }
                    destinations = parsed
                    if (parsed.isNotEmpty() && selectedTargetEmail.isEmpty()) {
                        selectedTargetEmail = parsed[0].email
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadCatchAllStatus(zoneId: String) {
        scope.launch {
            try {
                val res = ApiClient.api.getEmailCatchAll(email, apiKey, zoneId)
                if (res.isSuccessful) {
                    val body = res.body()
                    val result = body?.getAsJsonObject("result")
                    if (result != null) {
                        catchAllEnabled = result.get("enabled")?.asBoolean ?: true
                        val actions = result.getAsJsonArray("actions")
                        if (actions != null && actions.size() > 0) {
                            val act = actions.get(0).asJsonObject
                            val type = act.get("type")?.asString ?: "forward"
                            catchAllAction = type
                            val vals = act.getAsJsonArray("value")
                            if (vals != null && vals.size() > 0) {
                                val targetVal = vals.get(0).asString
                                if (type == "worker") selectedTargetWorker = targetVal
                                else selectedTargetEmail = targetVal
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadInitialData() {
        if (email.isBlank() || apiKey.isBlank()) {
            statusMsg = "⚠️ Isi Email & API Key di tab Akun terlebih dahulu!"
            return
        }
        scope.launch {
            isLoading = true
            try {
                val res = ApiClient.api.listZones(email, apiKey)
                if (res.isSuccessful) {
                    zones = res.body() ?: emptyList()
                    if (zones.isNotEmpty() && selectedZone == null) {
                        selectedZone = zones[0]
                        loadCatchAllStatus(zones[0].id)
                    }
                }
                loadDestinationsAndWorkers()
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(email, apiKey) {
        if (email.isNotEmpty() && apiKey.isNotEmpty()) loadInitialData()
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
                    Text("✉️ Email Routing & Catch-All", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Domain: ${selectedZone?.name ?: "(Pilih Domain)"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { loadInitialData() }, enabled = !isLoading) {
                    Text(if (isLoading) "⏳" else "🔄")
                }
            }

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
                                    loadCatchAllStatus(z.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- 2. SETUP MX & SPF OTOMATIS ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⚡ Aktivasi & DNS MX/SPF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Secara otomatis menambahkan MX dan SPF records agar domain siap menerima email.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val z = selectedZone ?: return@Button
                            scope.launch {
                                statusMsg = "Mengaktifkan Email Routing & konfigurasi DNS MX/SPF..."
                                try {
                                    val res = ApiClient.api.enableEmailRouting(email, apiKey, z.id)
                                    statusMsg = res.body()?.msg ?: "✅ DNS MX & SPF Berhasil Dipasang!"
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚡ Pasang MX & SPF Cloudflare Otomatis")
                    }
                }
            }
        }

        // --- 3. KONFIGURASI CATCH-ALL RULE (LENGKAP FORWARD / WORKER) ---
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
                        Text("⚡ Aturan Catch-All", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = catchAllEnabled,
                            onCheckedChange = { catchAllEnabled = it }
                        )
                    }
                    Text("Arahkan semua email tak terdaftar (*@${selectedZone?.name ?: "domain.com"})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Pilih Tipe Aksi:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = (catchAllAction == "forward"),
                            onClick = { catchAllAction = "forward" },
                            label = { Text("📤 Forward ke Email") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = (catchAllAction == "worker"),
                            onClick = { catchAllAction = "worker" },
                            label = { Text("⚙️ Proses via Worker") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // TARGET SELECTOR SESUAI AKSI YANG DIPILIH
                    if (catchAllAction == "worker") {
                        Text("Worker Penerima Email:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (workers.isEmpty()) {
                            OutlinedTextField(
                                value = selectedTargetWorker,
                                onValueChange = { selectedTargetWorker = it.trim().lowercase() },
                                label = { Text("Nama Worker Target") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = targetExpanded,
                                onExpandedChange = { targetExpanded = !targetExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedTargetWorker.ifBlank { "Pilih Worker" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Pilih Worker Cloudflare") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = targetExpanded,
                                    onDismissRequest = { targetExpanded = false }
                                ) {
                                    workers.forEach { w ->
                                        DropdownMenuItem(
                                            text = { Text(w) },
                                            onClick = {
                                                selectedTargetWorker = w
                                                targetExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Alamat Email Tujuan Forwarding:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (destinations.isEmpty()) {
                            OutlinedTextField(
                                value = selectedTargetEmail,
                                onValueChange = { selectedTargetEmail = it.trim() },
                                label = { Text("Email Tujuan (cth: gmail/yahoo)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = targetExpanded,
                                onExpandedChange = { targetExpanded = !targetExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedTargetEmail.ifBlank { "Pilih Email Tujuan" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Pilih Email Tujuan Terverifikasi") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = targetExpanded,
                                    onDismissRequest = { targetExpanded = false }
                                ) {
                                    destinations.forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text(d.email) },
                                            onClick = {
                                                selectedTargetEmail = d.email
                                                targetExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val z = selectedZone ?: return@Button
                            val target = if (catchAllAction == "worker") selectedTargetWorker else selectedTargetEmail
                            if (target.isBlank()) {
                                statusMsg = "Target belum ditentukan!"
                                return@Button
                            }
                            scope.launch {
                                isSaving = true
                                statusMsg = "Menyimpan konfigurasi catch-all..."
                                try {
                                    val res = ApiClient.api.updateEmailCatchAll(
                                        email, apiKey, z.id,
                                        catchAllEnabled.toString(), catchAllAction, target
                                    )
                                    statusMsg = res.body()?.msg ?: "✅ Catch-All Berhasil Disimpan!"
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && selectedZone != null
                    ) {
                        Text(if (isSaving) "Menyimpan..." else "💾 Simpan Catch-All")
                    }
                }
            }
        }

        // --- 4. DAFTARKAN DESTINASI EMAIL BARU ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("➕ Daftarkan Email Tujuan Baru", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Cloudflare akan mengirimkan email konfirmasi verifikasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newForwardEmail,
                            onValueChange = { newForwardEmail = it.trim() },
                            label = { Text("Email Baru (cth: saya@gmail.com)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newForwardEmail.isBlank()) return@Button
                                scope.launch {
                                    statusMsg = "Mendaftarkan destinasi $newForwardEmail..."
                                    try {
                                        val res = ApiClient.api.addEmailDestination(email, apiKey, newForwardEmail)
                                        statusMsg = res.body()?.msg ?: "Email didaftarkan! Cek inbox untuk verifikasi."
                                        newForwardEmail = ""
                                        loadDestinationsAndWorkers()
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    }
                                }
                            },
                            enabled = newForwardEmail.isNotBlank()
                        ) {
                            Text("Daftar")
                        }
                    }
                }
            }
        }

        // --- 5. DAFTAR EMAIL FORWARDING TERDAFTAR ---
        if (destinations.isNotEmpty()) {
            item {
                Text("Daftar Email Forwarding Terdaftar (${destinations.size}):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            items(destinations) { d ->
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
                            Text(d.email, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (d.verified != null) "✅ Terverifikasi" else "⏳ Menunggu Verifikasi Inbox",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (d.verified != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                statusMsg = "Menghapus email destinasi..."
                                try {
                                    val res = ApiClient.api.deleteEmailDestination(d.id, email, apiKey)
                                    if (res.isSuccessful) {
                                        statusMsg = "🗑 Destinasi email berhasil dihapus!"
                                        loadDestinationsAndWorkers()
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
}
