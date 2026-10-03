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
import com.cf.manager.data.model.ZoneItem
import kotlinx.coroutines.launch

@Composable
fun SslScreen() {
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var sslMode by remember { mutableStateOf("full") }
    var alwaysHttps by remember { mutableStateOf(true) }
    var statusMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(AppConfig.activeEmail, AppConfig.activeApiKey) {
        if (AppConfig.activeEmail.isNotEmpty()) {
            try {
                val res = ApiClient.api.listZones(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (res.isSuccessful) zones = res.body() ?: emptyList()
            } catch (e: Exception) {
                statusMsg = "Gagal memuat domain: ${e.message}"
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("🔒 Pengaturan Edge SSL/TLS", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = sslMode,
                onValueChange = { sslMode = it },
                label = { Text("Mode SSL (off / flexible / full / strict)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = alwaysHttps, onCheckedChange = { alwaysHttps = it })
                Text("Always Use HTTPS", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("🔐 Tembak Universal SSL CA", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (statusMsg.isNotEmpty()) {
                Text(statusMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        items(zones) { zone ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🌐 ${zone.name}", style = MaterialTheme.typography.titleSmall)
                    Text("Status: ${zone.status}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    statusMsg = "Menembak Let's Encrypt untuk ${zone.name}..."
                                    val res = ApiClient.api.orderCaCert(AppConfig.activeEmail, AppConfig.activeApiKey, zone.id, "lets_encrypt")
                                    statusMsg = res.body()?.msg ?: "Request terkirim!"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Let's Encrypt")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    statusMsg = "Menembak Google CA untuk ${zone.name}..."
                                    val res = ApiClient.api.orderCaCert(AppConfig.activeEmail, AppConfig.activeApiKey, zone.id, "google")
                                    statusMsg = res.body()?.msg ?: "Request terkirim!"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Google CA")
                        }
                    }
                }
            }
        }
    }
}
