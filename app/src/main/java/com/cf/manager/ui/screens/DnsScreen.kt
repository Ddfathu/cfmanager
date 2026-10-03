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
import com.cf.manager.data.model.DnsRecordItem
import com.cf.manager.data.model.ZoneItem
import kotlinx.coroutines.launch

@Composable
fun DnsScreen() {
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var dnsList by remember { mutableStateOf<List<DnsRecordItem>>(emptyList()) }
    var recordType by remember { mutableStateOf("A") }
    var nameInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
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
                statusMsg = "Gagal memuat zone: ${e.message}"
            }
        }
    }

    fun loadRecords(zoneId: String) {
        scope.launch {
            try {
                val res = ApiClient.api.listDns(AppConfig.activeEmail, AppConfig.activeApiKey, zoneId)
                if (res.isSuccessful) dnsList = res.body() ?: emptyList()
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            }
        }
    }

    LaunchedEffect(selectedZone) {
        selectedZone?.let { loadRecords(it.id) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🌐 Kelola DNS Record", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = recordType,
                onValueChange = { recordType = it },
                label = { Text("Tipe") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Name / Sub") },
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = contentInput,
            onValueChange = { contentInput = it },
            label = { Text("Content / Target IP") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val zone = selectedZone ?: return@Button
                scope.launch {
                    val res = ApiClient.api.addDns(AppConfig.activeEmail, AppConfig.activeApiKey, zone.id, recordType, nameInput, contentInput, 1, "true")
                    if (res.isSuccessful) {
                        statusMsg = "Sukses tambah DNS!"
                        nameInput = ""
                        contentInput = ""
                        loadRecords(zone.id)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("➕ Simpan DNS Record")
        }

        if (statusMsg.isNotEmpty()) {
            Text(statusMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(dnsList) { r ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("[${r.type}] ${r.name}", style = MaterialTheme.typography.titleSmall)
                            Text(r.content, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            selectedZone?.let { z ->
                                scope.launch {
                                    ApiClient.api.deleteDns(AppConfig.activeEmail, AppConfig.activeApiKey, z.id, r.id)
                                    loadRecords(z.id)
                                }
                            }
                        }) {
                            Text("🗑")
                        }
                    }
                }
            }
        }
    }
}
