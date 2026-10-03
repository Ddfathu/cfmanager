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
import com.cf.manager.data.model.WorkerVarItem
import kotlinx.coroutines.launch

@Composable
fun VariablesScreen() {
    var workers by remember { mutableStateOf<List<String>>(emptyList()) }
    var targetWorker by remember { mutableStateOf("") }
    var varList by remember { mutableStateOf<List<WorkerVarItem>>(emptyList()) }
    var varName by remember { mutableStateOf("") }
    var varValue by remember { mutableStateOf("") }
    var isSecret by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(AppConfig.activeEmail, AppConfig.activeApiKey) {
        if (AppConfig.activeEmail.isNotEmpty()) {
            try {
                val res = ApiClient.api.listWorkers(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (res.isSuccessful) {
                    workers = res.body() ?: emptyList()
                    if (workers.isNotEmpty()) targetWorker = workers[0]
                }
            } catch (_: Exception) {}
        }
    }

    fun loadVars(w: String) {
        scope.launch {
            try {
                val res = ApiClient.api.listWorkerVars(AppConfig.activeEmail, AppConfig.activeApiKey, w)
                if (res.isSuccessful) varList = res.body() ?: emptyList()
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            }
        }
    }

    LaunchedEffect(targetWorker) {
        if (targetWorker.isNotEmpty()) loadVars(targetWorker)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔑 Variables & Secrets", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = targetWorker,
            onValueChange = { targetWorker = it },
            label = { Text("Worker Target") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = varName,
                onValueChange = { varName = it },
                label = { Text("Nama (env.VAR)") },
                modifier = Modifier.weight(1f)
            )
            Row(modifier = Modifier.padding(top = 12.dp)) {
                Checkbox(checked = isSecret, onCheckedChange = { isSecret = it })
                Text("Secret?", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = varValue,
            onValueChange = { varValue = it },
            label = { Text("Value Data") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (targetWorker.isBlank() || varName.isBlank()) return@Button
                scope.launch {
                    val type = if (isSecret) "secret_text" else "plain_text"
                    val res = ApiClient.api.putWorkerVar(AppConfig.activeEmail, AppConfig.activeApiKey, targetWorker, varName, varValue, type)
                    statusMsg = res.body()?.msg ?: "Saved"
                    varName = ""
                    varValue = ""
                    loadVars(targetWorker)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 Simpan Variable")
        }

        if (statusMsg.isNotEmpty()) {
            Text(statusMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(varList) { v ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("env.${v.name} [${v.type.uppercase()}]", style = MaterialTheme.typography.titleSmall)
                        Text(v.text ?: "•••••••••••• (Encrypted Secret)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
