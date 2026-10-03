package com.cf.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.model.VisitorStats
import kotlinx.coroutines.delay

@Composable
fun StatsScreen() {
    var stats by remember { mutableStateOf(VisitorStats()) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val res = ApiClient.api.getVisitorStats()
                if (res.isSuccessful) {
                    stats = res.body() ?: VisitorStats()
                }
            } catch (e: Exception) {
                errorMsg = "Gagal memuat analitik: ${e.message}"
            }
            delay(10000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("📊 Visitor Live Analytics", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🟢 Online Sekarang", style = MaterialTheme.typography.labelMedium)
                Text("${stats.onlineCount} User", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔥 Total Kunjungan (Hits)", style = MaterialTheme.typography.labelMedium)
                Text("${stats.totalHits} Hits", style = MaterialTheme.typography.headlineMedium)
            }
        }

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
