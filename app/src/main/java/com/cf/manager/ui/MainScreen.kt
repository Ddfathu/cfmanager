package com.cf.manager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.local.AccountStorage
import com.cf.manager.data.model.CfAccount
import com.cf.manager.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val storage = remember { AccountStorage(context) }

    var backendUrl by remember {
        mutableStateOf(storage.getBackendUrl().ifBlank { AppConfig.DEFAULT_BASE_URL })
    }
    var accounts by remember {
        val list = storage.getAccounts()
        if (list.isEmpty()) {
            mutableStateOf(mutableListOf(CfAccount(alias = "Utama", email = "", apiKey = "")))
        } else {
            mutableStateOf(list)
        }
    }
    var activeIdx by remember {
        val saved = storage.getActiveIndex()
        mutableStateOf(if (saved in accounts.indices) saved else 0)
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("⚡ Worker", "🔑 Vars", "📄 Pages", "🌐 DNS", "🔒 SSL", "🚇 Tunnel", "✉️ Email", "🗄 Storage", "📊 Stats", "⚙ Akun")

    // State untuk Dialog Tambah Akun Baru
    var showAddDialog by remember { mutableStateOf(false) }
    var newAlias by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newApiKey by remember { mutableStateOf("") }

    val activeAccount = accounts.getOrElse(activeIdx) { CfAccount(alias = "Default", email = "", apiKey = "") }

    LaunchedEffect(backendUrl, activeAccount) {
        ApiClient.updateBaseUrl(backendUrl)
        AppConfig.activeEmail = activeAccount.email
        AppConfig.activeApiKey = activeAccount.apiKey
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CF Manager", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Akun Aktif: ${activeAccount.alias} (${activeAccount.email.ifBlank { "Belum diisi" }})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> WorkerEditorScreen()
                1 -> VariablesScreen()
                2 -> PagesScreen()
                3 -> DnsScreen()
                4 -> SslScreen()
                5 -> TunnelScreen()
                6 -> EmailScreen()
                7 -> StorageScreen()
                8 -> StatsScreen()
                9 -> {
                    // TAB 9: PENGATURAN & MULTI-ACCOUNT MANAGER
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Text("⚙️ Pengaturan Server & Akun", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = backendUrl,
                                onValueChange = {
                                    backendUrl = it
                                    storage.setBackendUrl(it)
                                    ApiClient.updateBaseUrl(it)
                                },
                                label = { Text("URL Worker Backend (Pusat)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Daftar Akun Cloudflare", style = MaterialTheme.typography.titleMedium)
                                Button(onClick = {
                                    newAlias = "Akun ${accounts.size + 1}"
                                    newEmail = ""
                                    newApiKey = ""
                                    showAddDialog = true
                                }) {
                                    Text("➕ Tambah Akun")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Klik salah satu kartu akun di bawah untuk mengaktifkannya:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        itemsIndexed(accounts) { index, acc ->
                            val isSelected = (index == activeIdx)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        activeIdx = index
                                        storage.setActiveIndex(index)
                                        AppConfig.activeEmail = acc.email
                                        AppConfig.activeApiKey = acc.apiKey
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isSelected) "✔ ${acc.alias} [AKTIF]" else acc.alias,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (accounts.size > 1) {
                                            IconButton(onClick = {
                                                val updated = accounts.toMutableList().apply { removeAt(index) }
                                                accounts = updated
                                                storage.saveAccounts(updated)
                                                if (activeIdx >= updated.size) {
                                                    activeIdx = 0
                                                    storage.setActiveIndex(0)
                                                }
                                                val curr = updated[activeIdx]
                                                AppConfig.activeEmail = curr.email
                                                AppConfig.activeApiKey = curr.apiKey
                                            }) {
                                                Text("🗑")
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Email: ${acc.email.ifBlank { "(Kosong)" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Key: ${if (acc.apiKey.length > 8) acc.apiKey.take(8) + "••••••••" else "(Kosong)"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Edit Rincian Akun Terpilih (${activeAccount.alias}):", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = activeAccount.alias,
                                onValueChange = { newName ->
                                    val list = accounts.toMutableList()
                                    list[activeIdx] = list[activeIdx].copy(alias = newName)
                                    accounts = list
                                    storage.saveAccounts(list)
                                },
                                label = { Text("Nama Alias Akun") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = activeAccount.email,
                                onValueChange = { newEmail ->
                                    val list = accounts.toMutableList()
                                    list[activeIdx] = list[activeIdx].copy(email = newEmail.trim())
                                    accounts = list
                                    storage.saveAccounts(list)
                                    AppConfig.activeEmail = newEmail.trim()
                                },
                                label = { Text("Email Cloudflare") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = activeAccount.apiKey,
                                onValueChange = { newKey ->
                                    val list = accounts.toMutableList()
                                    list[activeIdx] = list[activeIdx].copy(apiKey = newKey.trim())
                                    accounts = list
                                    storage.saveAccounts(list)
                                    AppConfig.activeApiKey = newKey.trim()
                                },
                                label = { Text("Global API Key (Bukan Token cfk_...)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Dialog Tambah Akun Baru
                    if (showAddDialog) {
                        AlertDialog(
                            onDismissRequest = { showAddDialog = false },
                            title = { Text("Tambah Akun Baru") },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = newAlias,
                                        onValueChange = { newAlias = it },
                                        label = { Text("Nama Alias (cth: Akun 2)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newEmail,
                                        onValueChange = { newEmail = it.trim() },
                                        label = { Text("Email Cloudflare") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newApiKey,
                                        onValueChange = { newApiKey = it.trim() },
                                        label = { Text("Global API Key") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    if (newAlias.isNotBlank()) {
                                        val list = accounts.toMutableList()
                                        val newAcc = CfAccount(alias = newAlias, email = newEmail, apiKey = newApiKey)
                                        list.add(newAcc)
                                        accounts = list
                                        storage.saveAccounts(list)
                                        // Langsung beralih ke akun yang baru dibuat
                                        activeIdx = list.size - 1
                                        storage.setActiveIndex(activeIdx)
                                        AppConfig.activeEmail = newAcc.email
                                        AppConfig.activeApiKey = newAcc.apiKey
                                        showAddDialog = false
                                    }
                                }) {
                                    Text("Simpan")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddDialog = false }) {
                                    Text("Batal")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
