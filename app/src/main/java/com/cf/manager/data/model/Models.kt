package com.cf.manager.data.model

data class CfAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val alias: String,
    val email: String,
    val apiKey: String
)

data class ApiResponse(
    val success: Boolean = false,
    val msg: String? = null,
    val error: String? = null,
    val worker_url: String? = null,
    val token: String? = null,
    val tunnel_id: String? = null,
    val name_servers: List<String>? = null
)

data class DnsRecordItem(
    val id: String = "",
    val type: String = "A",
    val name: String = "",
    val content: String = "",
    val ttl: Int = 1,
    val proxied: Boolean = true
)

data class ZoneItem(
    val id: String = "",
    val name: String = "",
    val status: String = "active",
    val name_servers: List<String> = emptyList()
)

data class TunnelItem(
    val id: String = "",
    val name: String = "",
    val status: String? = null
)

data class KvNamespaceItem(
    val id: String = "",
    val title: String = ""
)

data class R2BucketItem(
    val name: String = "",
    val creation_date: String? = null
)

data class R2ObjectItem(
    val key: String = "",
    val size: Long = 0,
    val uploaded: String? = null
)

data class D1DatabaseItem(
    val uuid: String = "",
    val name: String = ""
)

data class WorkerVarItem(
    val name: String = "",
    val text: String? = null,
    val type: String = "plain_text"
)

data class VisitorStats(
    val totalHits: Int = 0,
    val onlineCount: Int = 0
)
