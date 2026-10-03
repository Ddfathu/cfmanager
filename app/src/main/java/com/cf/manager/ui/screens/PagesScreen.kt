package com.cf.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import kotlinx.coroutines.launch

@Composable
fun PagesScreen() {
    var statusMsg by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var rawHtml by remember { mutableStateOf("<!DOCTYPE html><html><body><h1>Halo Pages Native!</h1></body></html>") }
    var workerScript by remember { mutableStateOf("export default { async fetch(req, env) { return env.ASSETS.fetch(req); } };") }
    var isDeploying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("📄 Cloudflare Pages Studio", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = projectName,
            onValueChange = { projectName = it.lowercase().trim() },
            label = { Text("Nama Project Pages") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = rawHtml,
            onValueChange = { rawHtml = it },
            label = { Text("HTML / index.html") },
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = workerScript,
            onValueChange = { workerScript = it },
            label = { Text("_worker.js Function") },
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (projectName.isBlank()) {
                    statusMsg = "Nama project tidak boleh kosong!"
                    return@Button
                }
                scope.launch {
                    isDeploying = true
                    statusMsg = "Mengirim payload ke Worker..."
                    try {
                        val payload = mapOf("html" to rawHtml, "workerCode" to workerScript)
                        val res = ApiClient.api.quickDeployPages(projectName, AppConfig.activeEmail, AppConfig.activeApiKey, payload)
                        if (res.isSuccessful) {
                            statusMsg = "🚀 Deploy sukses diproses oleh Worker!"
                        } else {
                            statusMsg = "Gagal HTTP: ${res.code()}"
                        }
                    } catch (e: Exception) {
                        statusMsg = "Error: ${e.message}"
                    } finally {
                        isDeploying = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDeploying
        ) {
            Text(if (isDeploying) "Memproses Deploy..." else "🚀 Deploy ke Pages via Worker")
        }

        if (statusMsg.isNotEmpty()) {
            Text(statusMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
