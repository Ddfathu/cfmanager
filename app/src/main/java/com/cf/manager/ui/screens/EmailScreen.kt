package com.cf.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.model.ZoneItem
import kotlinx.coroutines.launch

@Composable
fun EmailScreen() {
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var forwardEmailInput by remember { mutableStateOf("") }
    var catchAllAction by remember { mutableStateOf("forward") }
    var catchAllTarget by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(AppConfig.activeEmail, AppConfig.activeApiKey) {
        if (AppConfig.activeEmail.isNotEmpty()) {
            try {
                val res = ApiClient.api.listZones(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (res.isSuccessful) {
                    zones = res.body() ?: emptyList()
                    if (zones.isNotEmpty()) selectedZone = zones[0]
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("✉️ Email Routing & Catch-All", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = forwardEmailInput,
                onValueChange = { forwardEmailInput = it },
                label = { Text("Daftarkan Email Forwarding Baru") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (forwardEmailInput.isBlank()) return@Button
                    scope.launch {
                        statusMsg = "Mendaftarkan destinasi..."
                        val res = ApiClient.api.addEmailDestination(AppConfig.activeEmail, AppConfig.activeApiKey, forwardEmailInput)
                        statusMsg = res.body()?.msg ?: "Email didaftarkan! Cek inbox verifikasi."
                        forwardEmailInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("➕ Daftarkan Email Tujuan")
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("⚡ Konfigurasi Catch-All Rule", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = catchAllAction,
                onValueChange = { catchAllAction = it },
                label = { Text("Aksi (forward / worker)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = catchAllTarget,
                onValueChange = { catchAllTarget = it },
                label = { Text("Target Email / Target Worker") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val z = selectedZone ?: return@Button
                    scope.launch {
                        statusMsg = "Menyimpan setelan catch-all..."
                        val res = ApiClient.api.updateEmailCatchAll(AppConfig.activeEmail, AppConfig.activeApiKey, z.id, "true", catchAllAction, catchAllTarget)
                        statusMsg = res.body()?.msg ?: "Catch-All disimpan!"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Simpan Catch-All")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val z = selectedZone ?: return@Button
                    scope.launch {
                        statusMsg = "Memasang DNS MX/SPF otomatis ke Cloudflare..."
                        val res = ApiClient.api.enableEmailRouting(AppConfig.activeEmail, AppConfig.activeApiKey, z.id)
                        statusMsg = res.body()?.msg ?: "MX/SPF berhasil dikonfigurasi!"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚡ Pasang MX & SPF Otomatis")
            }

            if (statusMsg.isNotEmpty()) {
                Text(statusMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
