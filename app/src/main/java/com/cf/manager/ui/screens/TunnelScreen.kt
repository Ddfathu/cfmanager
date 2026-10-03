package com.cf.manager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cf.manager.data.AppConfig
import com.cf.manager.data.api.ApiClient
import com.cf.manager.data.local.AccountStorage
import com.cf.manager.data.model.TunnelItem
import com.cf.manager.data.model.ZoneItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelScreen() {
    val context = LocalContext.current
    val storage = remember { AccountStorage(context) }
    val accounts = remember { storage.getAccounts() }
    val activeIdx = remember { storage.getActiveIndex() }
    val currAcc = accounts.getOrNull(activeIdx)
    val email = AppConfig.activeEmail.ifBlank { currAcc?.email ?: "" }
    val apiKey = AppConfig.activeApiKey.ifBlank { currAcc?.apiKey ?: "" }

    var tunnels by remember { mutableStateOf<List<TunnelItem>>(emptyList()) }
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var tunnelNameInput by remember { mutableStateOf("") }
    var activeToken by remember { mutableStateOf("") }

    var selectedTunnel by remember { mutableStateOf<TunnelItem?>(null) }
    var selectedZone by remember { mutableStateOf<ZoneItem?>(null) }
    var zoneExpanded by remember { mutableStateOf(false) }

    var subDomainInput by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf("http://") }
    var serviceUrlInput by remember { mutableStateOf("localhost:8080") }

    var statusMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var isRouting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tunnelToDelete by remember { mutableStateOf<TunnelItem?>(null) }

    val scope = rememberCoroutineScope()

    fun loadData() {
        if (email.isBlank() || apiKey.isBlank()) {
            statusMsg = "⚠️ Isi Email & API Key di tab Akun terlebih dahulu!"
            return
        }
        scope.launch {
            isLoading = true
            try {
                val resT = ApiClient.api.listTunnels(email, apiKey)
                if (resT.isSuccessful) {
                    tunnels = resT.body() ?: emptyList()
                    if (selectedTunnel == null && tunnels.isNotEmpty()) {
                        selectedTunnel = tunnels[0]
                    }
                }
                val resZ = ApiClient.api.listZones(email, apiKey)
                if (resZ.isSuccessful) {
                    zones = resZ.body() ?: emptyList()
                    if (selectedZone == null && zones.isNotEmpty()) {
                        selectedZone = zones[0]
                    }
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(email, apiKey) {
        if (email.isNotEmpty() && apiKey.isNotEmpty()) loadData()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. HEADER & REFRESH ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("🚇 Cloudflare Tunnel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Kelola zero-trust tunnel & public hostname", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = { loadData() }, enabled = !isLoading) {
                    Text(if (isLoading) "⏳" else "🔄")
                }
            }
        }

        // --- 2. CARD BUAT TUNNEL BARU ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("➕ Buat Tunnel Baru", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tunnelNameInput,
                            onValueChange = { tunnelNameInput = it.lowercase().trim() },
                            label = { Text("Nama Tunnel") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (tunnelNameInput.isBlank()) return@Button
                                scope.launch {
                                    isCreating = true
                                    statusMsg = "Membuat tunnel '${tunnelNameInput}'..."
                                    try {
                                        val res = ApiClient.api.createTunnel(email, apiKey, tunnelNameInput)
                                        if (res.isSuccessful && res.body()?.success == true) {
                                            statusMsg = "✅ Tunnel '${tunnelNameInput}' berhasil dibuat!"
                                            activeToken = res.body()?.token ?: ""
                                            tunnelNameInput = ""
                                            loadData()
                                        } else {
                                            statusMsg = "Gagal: ${res.body()?.msg ?: res.message()}"
                                        }
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    } finally {
                                        isCreating = false
                                    }
                                }
                            },
                            enabled = !isCreating && tunnelNameInput.isNotBlank()
                        ) {
                            Text(if (isCreating) "..." else "Buat")
                        }
                    }
                }
            }
        }

        // --- 3. TOKEN VIEWER / RUN COMMAND ---
        if (activeToken.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔑 Cloudflared Run Token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("tunnel_token", activeToken)
                                clipboard.setPrimaryClip(clip)
                                statusMsg = "📋 Token disalin ke clipboard!"
                            }) {
                                Text("Salin Token")
                            }
                        }
                        Text(
                            text = activeToken,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }
                }
            }
        }

        // --- 4. FORM INGRESS ROUTE (PUBLIC HOSTNAME) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔗 Hubungkan Public Hostname (Ingress)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Rute domain ke service lokal tunnel terpilih", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Pilih Tunnel Tujuan: ${selectedTunnel?.name ?: "(Belum dipilih)"}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))

                    ExposedDropdownMenuBox(
                        expanded = zoneExpanded,
                        onExpandedChange = { zoneExpanded = !zoneExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedZone?.name ?: "Pilih Domain Cloudflare",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Domain Utama (Zone)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = zoneExpanded,
                            onDismissRequest = { zoneExpanded = false }
                        ) {
                            zones.forEach { z ->
                                DropdownMenuItem(
                                    text = { Text(z.name) },
                                    onClick = {
                                        selectedZone = z
                                        zoneExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = subDomainInput,
                        onValueChange = { subDomainInput = it.lowercase().trim() },
                        label = { Text("Subdomain (contoh: vpn, ssh, web)") },
                        placeholder = { Text("Kosongkan jika domain root") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = serviceType,
                            onValueChange = { serviceType = it },
                            label = { Text("Proto") },
                            modifier = Modifier.width(95.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = serviceUrlInput,
                            onValueChange = { serviceUrlInput = it.trim() },
                            label = { Text("Target Lokal (cth: localhost:8080)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val tun = selectedTunnel
                            val zon = selectedZone
                            if (tun == null || zon == null) {
                                statusMsg = "⚠️ Pilih tunnel dan domain utama terlebih dahulu!"
                                return@Button
                            }
                            scope.launch {
                                isRouting = true
                                val fullService = if (serviceUrlInput.startsWith("http://") || serviceUrlInput.startsWith("https://") || serviceUrlInput.startsWith("tcp://")) {
                                    serviceUrlInput
                                } else {
                                    "$serviceType$serviceUrlInput"
                                }
                                statusMsg = "Memasang DNS & Ingress ke Cloudflare..."
                                try {
                                    val res = ApiClient.api.addTunnelRoute(
                                        email,
                                        apiKey,
                                        tun.id,
                                        zon.id,
                                        subDomainInput,
                                        zon.name,
                                        fullService
                                    )
                                    statusMsg = res.body()?.msg ?: "✅ Hostname berhasil diarahkan ke $fullService!"
                                    subDomainInput = ""
                                } catch (e: Exception) {
                                    statusMsg = "Error: ${e.message}"
                                } finally {
                                    isRouting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRouting && selectedTunnel != null && selectedZone != null
                    ) {
                        Text(if (isRouting) "Menyimpan Hostname..." else "🚀 Pasang Hostname & DNS CNAME")
                    }
                }
            }
        }

        // --- 5. DAFTAR TUNNEL CARD DENGAN INDIKATOR STATUS REAL-TIME ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daftar Tunnel (${tunnels.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Ketuk untuk memilih", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        if (tunnels.isEmpty() && !isLoading) {
            item {
                Text("Belum ada tunnel. Buat baru di bagian atas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        items(tunnels) { t ->
            val isSelected = (selectedTunnel?.id == t.id)
            val rawStatus = (t.status ?: "inactive").lowercase()
            val isHealthy = rawStatus == "healthy" || rawStatus == "active"
            val isDegraded = rawStatus == "degraded"

            val statusColor = when {
                isHealthy -> Color(0xFF16A34A) // Hijau Aktif
                isDegraded -> Color(0xFFD97706) // Kuning Degraded
                else -> Color(0xFFDC2626) // Merah Mati/Inactive
            }

            val statusLabel = when {
                isHealthy -> "Aktif"
                isDegraded -> "Degraded"
                else -> "Mati"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedTunnel = t },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(if (isSelected) "✔ " else "🚇 ", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = t.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // BADGE STATUS AKTIF / MATI REAL-TIME
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = statusColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ID: ${t.id}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedTunnel = t
                                scope.launch {
                                    statusMsg = "Mengambil token untuk '${t.name}'..."
                                    try {
                                        val res = ApiClient.api.getTunnelToken(email, apiKey, t.id)
                                        if (res.isSuccessful && !res.body()?.token.isNullOrBlank()) {
                                            activeToken = res.body()?.token ?: ""
                                            statusMsg = "✅ Token tunnel '${t.name}' berhasil dimuat!"
                                        } else {
                                            statusMsg = "Gagal mengambil token tunnel."
                                        }
                                    } catch (e: Exception) {
                                        statusMsg = "Error: ${e.message}"
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("🔑 Token", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                tunnelToDelete = t
                                showDeleteDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("🗑 Hapus", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (statusMsg.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }

    if (showDeleteDialog && tunnelToDelete != null) {
        val target = tunnelToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Tunnel?") },
            text = { Text("Yakin ingin menghapus tunnel '${target.name}' (${target.id}) dari akun Cloudflare? Semua route ingress aktif akan terputus.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        statusMsg = "Menghapus tunnel '${target.name}'..."
                        try {
                            val res = ApiClient.api.deleteTunnel(email, apiKey, target.id)
                            if (res.isSuccessful && res.body()?.success == true) {
                                statusMsg = "🗑 Tunnel '${target.name}' berhasil dihapus!"
                                if (selectedTunnel?.id == target.id) selectedTunnel = null
                                loadData()
                            } else {
                                statusMsg = "Gagal: ${res.body()?.msg ?: res.message()}"
                            }
                        } catch (e: Exception) {
                            statusMsg = "Error: ${e.message}"
                        }
                    }
                }) {
                    Text("Ya, Hapus!", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
