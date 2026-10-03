package com.cf.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun WorkerEditorScreen() {
    var workerList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedWorker by remember { mutableStateOf("") }
    var workerNameInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("export default {\n  async fetch(request, env) {\n    return new Response(\"Hello from CF Worker!\");\n  }\n};") }
    var rawUrlInput by remember { mutableStateOf("https://raw.ddfathu.cc.cd/raw/4qmj2d/nauticamodv8") }
    var statusMsg by remember { mutableStateOf("") }
    var isDeploying by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshWorkers() {
        scope.launch {
            try {
                val res = ApiClient.api.listWorkers(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (res.isSuccessful) {
                    workerList = res.body() ?: emptyList()
                    if (workerList.isNotEmpty() && selectedWorker.isEmpty()) {
                        selectedWorker = workerList[0]
                    }
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            }
        }
    }

    LaunchedEffect(AppConfig.activeEmail, AppConfig.activeApiKey) {
        if (AppConfig.activeEmail.isNotEmpty()) refreshWorkers()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("⚡ Worker Editor & Deployer", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = rawUrlInput,
                onValueChange = { rawUrlInput = it },
                label = { Text("URL Raw GitHub / Preset") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    scope.launch {
                        statusMsg = "Mengunduh script raw..."
                        try {
                            val content = withContext(Dispatchers.IO) { URL(rawUrlInput).readText() }
                            codeInput = content
                            statusMsg = "Berhasil memuat script RAW!"
                        } catch (e: Exception) {
                            statusMsg = "Gagal fetch RAW: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Fetch")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = if (selectedWorker.isNotEmpty()) selectedWorker else workerNameInput,
            onValueChange = {
                selectedWorker = ""
                workerNameInput = it
            },
            label = { Text("Nama Worker Target") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text("JavaScript Code (Worker Engine)") },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val target = if (selectedWorker.isNotEmpty()) selectedWorker else workerNameInput
                    if (target.isBlank()) {
                        statusMsg = "Nama target worker tidak boleh kosong!"
                        return@Button
                    }
                    scope.launch {
                        isDeploying = true
                        statusMsg = "Mendeploy ke Cloudflare..."
                        try {
                            val res = ApiClient.api.deployWorker(AppConfig.activeEmail, AppConfig.activeApiKey, target, codeInput)
                            if (res.isSuccessful && res.body()?.success == true) {
                                statusMsg = "🎉 Berhasil deploy: ${res.body()?.worker_url ?: target}"
                                refreshWorkers()
                            } else {
                                statusMsg = "Gagal deploy: ${res.body()?.msg ?: res.message()}"
                            }
                        } catch (e: Exception) {
                            statusMsg = "Error deploy: ${e.message}"
                        } finally {
                            isDeploying = false
                        }
                    }
                },
                modifier = Modifier.weight(2f),
                enabled = !isDeploying
            ) {
                Text(if (isDeploying) "Deploying..." else "🚀 Deploy")
            }

            if (selectedWorker.isNotBlank()) {
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🗑 Hapus")
                }
            }
        }

        if (statusMsg.isNotEmpty()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus Worker?") },
            text = { Text("Yakin ingin menghapus worker '$selectedWorker' secara permanen?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        ApiClient.api.deleteWorker(AppConfig.activeEmail, AppConfig.activeApiKey, selectedWorker)
                        selectedWorker = ""
                        refreshWorkers()
                    }
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
