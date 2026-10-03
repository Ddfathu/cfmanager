package com.cf.manager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.cf.manager.data.model.KvNamespaceItem
import com.cf.manager.data.model.R2BucketItem
import com.cf.manager.data.model.WorkerVarItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariablesScreen() {
    val context = LocalContext.current
    val storage = remember { AccountStorage(context) }
    val accounts = remember { storage.getAccounts() }
    val activeIdx = remember { storage.getActiveIndex() }
    val currAcc = accounts.getOrNull(activeIdx)
    val email = AppConfig.activeEmail.ifBlank { currAcc?.email ?: "" }
    val apiKey = AppConfig.activeApiKey.ifBlank { currAcc?.apiKey ?: "" }

    var workers by remember { mutableStateOf<List<String>>(emptyList()) }
    var targetWorker by remember { mutableStateOf("") }
    var varList by remember { mutableStateOf<List<WorkerVarItem>>(emptyList()) }

    // State Env Vars Biasa
    var varName by remember { mutableStateOf("") }
    var varValue by remember { mutableStateOf("") }
    var isSecret by remember { mutableStateOf(false) }

    // State Storage Resources untuk Binding
    var kvList by remember { mutableStateOf<List<KvNamespaceItem>>(emptyList()) }
    var r2List by remember { mutableStateOf<List<R2BucketItem>>(emptyList()) }

    // State Form Binding KV
    var kvBindingName by remember { mutableStateOf("MY_KV") }
    var selectedKvNamespace by remember { mutableStateOf<KvNamespaceItem?>(null) }
    var kvDropdownExpanded by remember { mutableStateOf(false) }

    // State Form Binding R2
    var r2BindingName by remember { mutableStateOf("MY_BUCKET") }
    var selectedR2Bucket by remember { mutableStateOf<R2BucketItem?>(null) }
    var r2DropdownExpanded by remember { mutableStateOf(false) }

    var statusMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadVars(w: String) {
        if (w.isBlank()) return
        scope.launch {
            isLoading = true
            try {
                val res = ApiClient.api.listWorkerVars(email, apiKey, w)
                if (res.isSuccessful) {
                    varList = res.body() ?: emptyList()
                } else {
                    statusMsg = "Gagal memuat variabel: HTTP ${res.code()}"
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadInitialData() {
        if (email.isBlank() || apiKey.isBlank()) {
            statusMsg = "⚠️ Isi Email & API Key di tab Akun terlebih dahulu!"
            return
        }
        scope.launch {
            try {
                val resW = ApiClient.api.listWorkers(email, apiKey)
                if (resW.isSuccessful) {
                    val list = resW.body() ?: emptyList()
                    workers = list
                    if (list.isNotEmpty() && targetWorker.isEmpty()) {
                        targetWorker = list[0]
                        loadVars(list[0])
                    }
                }

                // Ambil daftar KV Namespace akun
                val resKv = ApiClient.api.listKvNamespaces(email, apiKey)
                if (resKv.isSuccessful) {
                    val kvs = resKv.body() ?: emptyList()
                    kvList = kvs
                    if (kvs.isNotEmpty()) selectedKvNamespace = kvs[0]
                }

                // Ambil daftar R2 Bucket akun
                val resR2 = ApiClient.api.listR2Buckets(email, apiKey)
                if (resR2.isSuccessful) {
                    val r2s = resR2.body() ?: emptyList()
                    r2List = r2s
                    if (r2s.isNotEmpty()) selectedR2Bucket = r2s[0]
                }
            } catch (_: Exception) {}
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
        // --- 1. HEADER & WORKER SELECTOR ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("🔑 Variables & Bindings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Target: env.* pada '${targetWorker.ifBlank { "Belum dipilih" }}'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { loadVars(targetWorker) }, enabled = !isLoading && targetWorker.isNotBlank()) {
                    Text(if (isLoading) "⏳" else "🔄")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (workers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    workers.forEach { wName ->
                        FilterChip(
                            selected = (targetWorker == wName),
                            onClick = {
                                targetWorker = wName
                                loadVars(wName)
                            },
                            label = { Text(wName) }
                        )
                    }
                }
            }
        }

        // --- 2. ENVIRONMENT VARIABLES (TEXT / SECRET) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📝 Plain Text & Secrets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = varName,
                            onValueChange = { varName = it.trim() },
                            label = { Text("Nama (cth: API_TOKEN)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isSecret, onCheckedChange = { isSecret = it })
                            Text("Secret?", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = varValue,
                        onValueChange = { varValue = it },
                        label = { Text("Nilai Variable") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (targetWorker.isBlank() || varName.isBlank()) return@Button
                            scope.launch {
                                isSaving = true
                                val type = if (isSecret) "secret_text" else "plain_text"
                                try {
                                    val res = ApiClient.api.putWorkerVar(email, apiKey, targetWorker, varName, varValue, type)
                                    statusMsg = res.body()?.msg ?: "Variabel tersimpan!"
                                    varName = ""
                                    varValue = ""
                                    loadVars(targetWorker)
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && targetWorker.isNotBlank() && varName.isNotBlank()
                    ) {
                        Text(if (isSaving) "Menyimpan..." else "💾 Simpan Variable")
                    }
                }
            }
        }

        // --- 3. BINDING KV NAMESPACE ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📦 Binding KV Namespace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Akses KV di worker via env.NAMA_BINDING", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = kvBindingName,
                        onValueChange = { kvBindingName = it.trim().uppercase() },
                        label = { Text("Variable Binding Name") },
                        placeholder = { Text("cth: MY_KV") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = kvDropdownExpanded,
                        onExpandedChange = { kvDropdownExpanded = !kvDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedKvNamespace?.title ?: "Pilih KV Namespace",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih KV Namespace") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kvDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = kvDropdownExpanded,
                            onDismissRequest = { kvDropdownExpanded = false }
                        ) {
                            kvList.forEach { kv ->
                                DropdownMenuItem(
                                    text = { Text("${kv.title} (${kv.id.take(8)}...)") },
                                    onClick = {
                                        selectedKvNamespace = kv
                                        kvDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val kv = selectedKvNamespace ?: return@Button
                            if (targetWorker.isBlank() || kvBindingName.isBlank()) return@Button
                            scope.launch {
                                isSaving = true
                                statusMsg = "Menghubungkan binding KV..."
                                try {
                                    val res = ApiClient.api.putWorkerBinding(
                                        email, apiKey, targetWorker,
                                        "kv_namespace", kvBindingName, kv.id
                                    )
                                    statusMsg = res.body()?.msg ?: "✅ KV Namespace berhasil dibinding!"
                                    loadVars(targetWorker)
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && targetWorker.isNotBlank() && selectedKvNamespace != null && kvBindingName.isNotBlank()
                    ) {
                        Text("🔗 Pasang Binding KV")
                    }
                }
            }
        }

        // --- 4. BINDING R2 BUCKET ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🪣 Binding R2 Bucket", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Akses file storage di worker via env.NAMA_BUCKET", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = r2BindingName,
                        onValueChange = { r2BindingName = it.trim().uppercase() },
                        label = { Text("Variable Binding Name") },
                        placeholder = { Text("cth: MY_BUCKET") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = r2DropdownExpanded,
                        onExpandedChange = { r2DropdownExpanded = !r2DropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedR2Bucket?.name ?: "Pilih R2 Bucket",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih R2 Bucket") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = r2DropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = r2DropdownExpanded,
                            onDismissRequest = { r2DropdownExpanded = false }
                        ) {
                            r2List.forEach { r2 ->
                                DropdownMenuItem(
                                    text = { Text(r2.name) },
                                    onClick = {
                                        selectedR2Bucket = r2
                                        r2DropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val r2 = selectedR2Bucket ?: return@Button
                            if (targetWorker.isBlank() || r2BindingName.isBlank()) return@Button
                            scope.launch {
                                isSaving = true
                                statusMsg = "Menghubungkan binding R2 Bucket..."
                                try {
                                    val res = ApiClient.api.putWorkerBinding(
                                        email, apiKey, targetWorker,
                                        "r2_bucket", r2BindingName, r2.name
                                    )
                                    statusMsg = res.body()?.msg ?: "✅ R2 Bucket berhasil dibinding!"
                                    loadVars(targetWorker)
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && targetWorker.isNotBlank() && selectedR2Bucket != null && r2BindingName.isNotBlank()
                    ) {
                        Text("🔗 Pasang Binding R2")
                    }
                }
            }
        }

        // --- 5. DAFTAR VARIABLES & BINDINGS AKTIF ---
        item {
            Text("Daftar Binding & Variables Aktif (${varList.size}):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (varList.isEmpty() && !isLoading) {
            item {
                Text("Belum ada variabel atau binding di worker ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        items(varList) { v ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                            Text("env.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.titleSmall)
                            Text(v.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge {
                                Text(v.type.uppercase())
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = v.text ?: "•••••••••••• (Encrypted Secret / Attached Resource)",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        scope.launch {
                            statusMsg = "Menghapus binding/variabel ${v.name}..."
                            try {
                                val res = ApiClient.api.deleteWorkerVar(email, apiKey, targetWorker, v.name)
                                if (res.isSuccessful && res.body()?.success == true) {
                                    statusMsg = "🗑 Variabel/Binding berhasil dihapus!"
                                    loadVars(targetWorker)
                                } else {
                                    statusMsg = "Gagal: ${res.body()?.msg ?: res.message()}"
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
