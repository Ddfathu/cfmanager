package com.cf.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.model.TunnelItem
import com.cf.manager.data.model.ZoneItem
import kotlinx.coroutines.launch

@Composable
fun TunnelScreen() {
    var tunnels by remember { mutableStateOf<List<TunnelItem>>(emptyList()) }
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var tunnelNameInput by remember { mutableStateOf("") }
    var activeToken by remember { mutableStateOf("") }
    
    var selectedTunnelId by remember { mutableStateOf("") }
    var selectedZoneId by remember { mutableStateOf("") }
    var subDomainInput by remember { mutableStateOf("") }
    var mainDomainInput by remember { mutableStateOf("") }
    var serviceUrlInput by remember { mutableStateOf("http://localhost:8080") }
    
    var statusMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            try {
                val resT = ApiClient.api.listTunnels(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (resT.isSuccessful) tunnels = resT.body() ?: emptyList()
                val resZ = ApiClient.api.listZones(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (resZ.isSuccessful) {
                    zones = resZ.body() ?: emptyList()
                    if (zones.isNotEmpty() && selectedZoneId.isEmpty()) {
                        selectedZoneId = zones[0].id
                        mainDomainInput = zones[0].name
                    }
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            }
        }
    }

    LaunchedEffect(AppConfig.activeEmail, AppConfig.activeApiKey) {
        if (AppConfig.activeEmail.isNotEmpty()) loadData()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("🚇 Cloudflare Tunnel Manager", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tunnelNameInput,
                    onValueChange = { tunnelNameInput = it },
                    label = { Text("Nama Tunnel Baru") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (tunnelNameInput.isBlank()) return@Button
                        scope.launch {
                            statusMsg = "Membuat tunnel..."
                            val res = ApiClient.api.createTunnel(AppConfig.activeEmail, AppConfig.activeApiKey, tunnelNameInput)
                            if (res.isSuccessful) {
                                statusMsg = res.body()?.msg ?: "Tunnel dibuat!"
                                tunnelNameInput = ""
                                loadData()
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Buat")
                }
            }

            if (activeToken.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = activeToken,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tunnel Run Token") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("🔗 Hubungkan Public Hostname (Ingress Route)", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            
            OutlinedTextField(
                value = subDomainInput,
                onValueChange = { subDomainInput = it },
                label = { Text("Subdomain (ssh / vpn / app)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = serviceUrlInput,
                onValueChange = { serviceUrlInput = it },
                label = { Text("Target Lokal / Service URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (selectedTunnelId.isBlank() || selectedZoneId.isBlank()) {
                        statusMsg = "Pilih salah satu tunnel di bawah dulu!"
                        return@Button
                    }
                    scope.launch {
                        statusMsg = "Menyimpan route ingress ke Cloudflare..."
                        val res = ApiClient.api.addTunnelRoute(
                            AppConfig.activeEmail,
                            AppConfig.activeApiKey,
                            selectedTunnelId,
                            selectedZoneId,
                            subDomainInput,
                            mainDomainInput,
                            serviceUrlInput
                        )
                        statusMsg = res.body()?.msg ?: "Route berhasil dipasang!"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔗 Pasang Hostname & DNS CNAME")
            }

            if (statusMsg.isNotEmpty()) {
                Text(statusMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 6.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Daftar Tunnel (Klik untuk pilih):", style = MaterialTheme.typography.labelMedium)
        }

        items(tunnels) { t ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedTunnelId == t.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = { selectedTunnelId = t.id }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("🚇 ${t.name}", style = MaterialTheme.typography.titleSmall)
                        Text("ID: ${t.id}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        scope.launch {
                            val res = ApiClient.api.getTunnelToken(AppConfig.activeEmail, AppConfig.activeApiKey, t.id)
                            if (res.isSuccessful) {
                                activeToken = res.body()?.token ?: "Token tidak ditemukan"
                            }
                        }
                    }) {
                        Text("Token")
                    }
                }
            }
        }
    }
}
