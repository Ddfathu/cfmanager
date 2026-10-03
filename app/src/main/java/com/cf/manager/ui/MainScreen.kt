package com.cf.manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var accounts by remember { mutableStateOf(storage.getAccounts()) }
    var activeIdx by remember { mutableStateOf(storage.getActiveIndex()) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("⚡ Worker", "🔑 Vars", "📄 Pages", "🌐 DNS", "🔒 SSL", "🚇 Tunnel", "✉️ Email", "🗄 Storage", "📊 Stats", "⚙ Akun")

    val activeAccount = accounts.getOrNull(activeIdx) ?: CfAccount(alias = "Default", email = "", apiKey = "")

    LaunchedEffect(backendUrl, activeAccount) {
        ApiClient.updateBaseUrl(backendUrl)
        AppConfig.activeEmail = activeAccount.email
        AppConfig.activeApiKey = activeAccount.apiKey
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CF Manager (${activeAccount.alias})") }
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
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Konfigurasi Terpusat", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

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
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = activeAccount.email,
                            onValueChange = { newEmail ->
                                val list = accounts.toMutableList()
                                if (list.isEmpty()) list.add(CfAccount(alias = "Utama", email = newEmail, apiKey = activeAccount.apiKey))
                                else list[activeIdx] = list[activeIdx].copy(email = newEmail)
                                accounts = list
                                storage.saveAccounts(list)
                                AppConfig.activeEmail = newEmail
                            },
                            label = { Text("Email Cloudflare") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = activeAccount.apiKey,
                            onValueChange = { newKey ->
                                val list = accounts.toMutableList()
                                if (list.isEmpty()) list.add(CfAccount(alias = "Utama", email = activeAccount.email, apiKey = newKey))
                                else list[activeIdx] = list[activeIdx].copy(apiKey = newKey)
                                accounts = list
                                storage.saveAccounts(list)
                                AppConfig.activeApiKey = newKey
                            },
                            label = { Text("Global API Key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
