package com.cf.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.model.D1DatabaseItem
import com.cf.manager.data.model.KvNamespaceItem
import com.cf.manager.data.model.R2BucketItem
import com.cf.manager.data.model.R2ObjectItem
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun StorageScreen() {
    val context = LocalContext.current
    var r2Buckets by remember { mutableStateOf<List<R2BucketItem>>(emptyList()) }
    var selectedBucket by remember { mutableStateOf("") }
    var r2Objects by remember { mutableStateOf<List<R2ObjectItem>>(emptyList()) }
    var d1Databases by remember { mutableStateOf<List<D1DatabaseItem>>(emptyList()) }
    var kvList by remember { mutableStateOf<List<KvNamespaceItem>>(emptyList()) }

    var sqlQuery by remember { mutableStateOf("SELECT * FROM users LIMIT 10;") }
    var sqlResult by remember { mutableStateOf("") }

    var previewTitle by remember { mutableStateOf("") }
    var previewContent by remember { mutableStateOf("") }
    var showPreviewDialog by remember { mutableStateOf(false) }

    var kvKeyInput by remember { mutableStateOf("") }
    var kvValInput by remember { mutableStateOf("") }

    var statusMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && selectedBucket.isNotBlank()) {
            scope.launch {
                statusMsg = "Membaca file & upload ke R2..."
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}.bin"

                    val emailPart = AppConfig.activeEmail.toRequestBody("text/plain".toMediaTypeOrNull())
                    val keyPart = AppConfig.activeApiKey.toRequestBody("text/plain".toMediaTypeOrNull())
                    val bucketPart = selectedBucket.toRequestBody("text/plain".toMediaTypeOrNull())
                    val objectKeyPart = fileName.toRequestBody("text/plain".toMediaTypeOrNull())
                    val fileBody = bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file_data", fileName, fileBody)

                    val res = ApiClient.api.uploadR2Object(emailPart, keyPart, bucketPart, objectKeyPart, filePart)
                    statusMsg = res.body()?.msg ?: "File berhasil diunggah ke R2!"

                    val objRes = ApiClient.api.listR2Objects(AppConfig.activeEmail, AppConfig.activeApiKey, selectedBucket)
                    if (objRes.isSuccessful) r2Objects = objRes.body() ?: emptyList()
                } catch (e: Exception) {
                    statusMsg = "Gagal upload: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(AppConfig.activeEmail, AppConfig.activeApiKey) {
        if (AppConfig.activeEmail.isNotEmpty()) {
            try {
                val r2Res = ApiClient.api.listR2Buckets(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (r2Res.isSuccessful) {
                    r2Buckets = r2Res.body() ?: emptyList()
                    if (r2Buckets.isNotEmpty() && selectedBucket.isEmpty()) {
                        selectedBucket = r2Buckets[0].name
                    }
                }
                val d1Res = ApiClient.api.listD1Databases(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (d1Res.isSuccessful) d1Databases = d1Res.body() ?: emptyList()
                val kvRes = ApiClient.api.listKvNamespaces(AppConfig.activeEmail, AppConfig.activeApiKey)
                if (kvRes.isSuccessful) kvList = kvRes.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(selectedBucket) {
        if (selectedBucket.isNotBlank() && AppConfig.activeEmail.isNotEmpty()) {
            try {
                val res = ApiClient.api.listR2Objects(AppConfig.activeEmail, AppConfig.activeApiKey, selectedBucket)
                if (res.isSuccessful) r2Objects = res.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("💿 R2 Buckets & File Manager", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r2Buckets.forEach { b ->
                    FilterChip(
                        selected = selectedBucket == b.name,
                        onClick = { selectedBucket = b.name },
                        label = { Text(b.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (selectedBucket.isBlank()) {
                        statusMsg = "Pilih bucket terlebih dahulu!"
                        return@Button
                    }
                    filePickerLauncher.launch("*/*")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📤 Pilih File HP & Upload ke $selectedBucket")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("File di bucket ($selectedBucket):", style = MaterialTheme.typography.labelMedium)
        }

        items(r2Objects) { obj ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(obj.key, style = MaterialTheme.typography.titleSmall)
                        Text("${obj.size / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        scope.launch {
                            val res = ApiClient.api.readR2ObjectText(AppConfig.activeEmail, AppConfig.activeApiKey, selectedBucket, obj.key)
                            previewTitle = obj.key
                            previewContent = res.body()?.text ?: "File kosong atau format bukan teks."
                            showPreviewDialog = true
                        }
                    }) {
                        Text("👁️ Baca")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("🗄️️ D1 Databases & SQL Runner", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = sqlQuery,
                onValueChange = { sqlQuery = it },
                label = { Text("Query SQL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    val db = d1Databases.firstOrNull() ?: return@Button
                    scope.launch {
                        sqlResult = "Executing..."
                        try {
                            val res = ApiClient.api.executeD1Query(AppConfig.activeEmail, AppConfig.activeApiKey, db.uuid, sqlQuery)
                            sqlResult = res.body()?.result?.toString() ?: "Empty / Error"
                        } catch (e: Exception) {
                            sqlResult = "Err: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚡ Jalankan SQL")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(sqlResult.ifEmpty { "Hasil SQL..." }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("🔑 KV Storage (Key-Value)", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = kvKeyInput, onValueChange = { kvKeyInput = it }, label = { Text("Key") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = kvValInput, onValueChange = { kvValInput = it }, label = { Text("Value") }, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    val ns = kvList.firstOrNull()
                    if (ns == null || kvKeyInput.isBlank()) {
                        statusMsg = "Pilih namespace KV dulu atau isi key!"
                        return@Button
                    }
                    scope.launch {
                        val res = ApiClient.api.putKvPair(AppConfig.activeEmail, AppConfig.activeApiKey, ns.id, kvKeyInput, kvValInput)
                        statusMsg = res.body()?.msg ?: "Key-Value disimpan!"
                        kvKeyInput = ""
                        kvValInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Simpan Key-Value")
            }

            if (statusMsg.isNotEmpty()) {
                Text(statusMsg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = { Text(previewTitle) },
            text = {
                OutlinedTextField(
                    value = previewContent,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
