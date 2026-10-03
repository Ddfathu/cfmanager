package com.cf.manager.data.api

import com.cf.manager.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface WorkerApi {
    @FormUrlEncoded
    @POST("list-workers")
    suspend fun listWorkers(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String): Response<List<String>>

    @FormUrlEncoded
    @POST("deploy-worker")
    suspend fun deployWorker(
        @Field("cf_email") email: String,
        @Field("cf_api_key") apiKey: String,
        @Field("target_worker") target: String,
        @Field("worker_code") code: String
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("delete-worker-cf")
    suspend fun deleteWorker(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("target_worker") target: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("list-worker-vars")
    suspend fun listWorkerVars(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("target_worker") target: String): Response<List<WorkerVarItem>>

    @FormUrlEncoded
    @POST("put-worker-var")
    suspend fun putWorkerVar(
        @Field("cf_email") email: String,
        @Field("cf_api_key") apiKey: String,
        @Field("target_worker") target: String,
        @Field("var_name") name: String,
        @Field("var_value") value: String,
        @Field("var_type") type: String
    ): Response<ApiResponse>

    @POST("api/quick-deploy-pages/{project}")
    suspend fun quickDeployPages(
        @Path("project") project: String,
        @Header("X-Auth-Email") email: String,
        @Header("X-Auth-Key") apiKey: String,
        @Body payload: Map<String, String>
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("list-zones")
    suspend fun listZones(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String): Response<List<ZoneItem>>

    @FormUrlEncoded
    @POST("list-dns")
    suspend fun listDns(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("zone_id") zoneId: String): Response<List<DnsRecordItem>>

    @FormUrlEncoded
    @POST("add-dns")
    suspend fun addDns(
        @Field("cf_email") email: String,
        @Field("cf_api_key") apiKey: String,
        @Field("zone_id") zoneId: String,
        @Field("dns_type") type: String,
        @Field("dns_name") name: String,
        @Field("dns_content") content: String,
        @Field("dns_ttl") ttl: Int,
        @Field("dns_proxied") proxied: String
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("delete-dns")
    suspend fun deleteDns(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("zone_id") zoneId: String, @Field("record_id") recordId: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("update-ssl-settings")
    suspend fun updateSslSettings(
        @Field("cf_email") email: String,
        @Field("cf_api_key") apiKey: String,
        @Field("zone_id") zoneId: String,
        @Field("ssl_mode") sslMode: String,
        @Field("always_use_https") alwaysHttps: String
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("order-ca-cert")
    suspend fun orderCaCert(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("zone_id") zoneId: String, @Field("ca_type") caType: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("list-tunnels")
    suspend fun listTunnels(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String): Response<List<TunnelItem>>

    @FormUrlEncoded
    @POST("create-tunnel")
    suspend fun createTunnel(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("tunnel_name") name: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("get-tunnel-token")
    suspend fun getTunnelToken(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("tunnel_id") tunnelId: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("add-tunnel-route")
    suspend fun addTunnelRoute(
        @Field("cf_email") email: String,
        @Field("cf_api_key") apiKey: String,
        @Field("tunnel_id") tunnelId: String,
        @Field("zone_id") zoneId: String,
        @Field("subdomain") subdomain: String,
        @Field("main_domain") mainDomain: String,
        @Field("service_url") serviceUrl: String
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("add-email-destination")
    suspend fun addEmailDestination(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("email_address") address: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("enable-email-routing")
    suspend fun enableEmailRouting(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("zone_id") zoneId: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("update-email-catchall")
    suspend fun updateEmailCatchAll(
        @Field("cf_email") email: String,
        @Field("cf_api_key") apiKey: String,
        @Field("zone_id") zoneId: String,
        @Field("catchall_enabled") enabled: String,
        @Field("catchall_action") action: String,
        @Field("catchall_target") target: String
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("list-kv-namespaces")
    suspend fun listKvNamespaces(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String): Response<List<KvNamespaceItem>>

    @FormUrlEncoded
    @POST("put-kv-pair")
    suspend fun putKvPair(
        @Field("cf_email") email: String,
        @Field("cf_api_key") apiKey: String,
        @Field("namespace_id") nsId: String,
        @Field("kv_key") key: String,
        @Field("kv_value") value: String
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("list-r2-buckets")
    suspend fun listR2Buckets(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String): Response<List<R2BucketItem>>

    @FormUrlEncoded
    @POST("list-r2-objects")
    suspend fun listR2Objects(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("bucket_name") bucket: String): Response<List<R2ObjectItem>>

    @Multipart
    @POST("upload-r2-object")
    suspend fun uploadR2Object(
        @Part("cf_email") email: RequestBody,
        @Part("cf_api_key") apiKey: RequestBody,
        @Part("bucket_name") bucket: RequestBody,
        @Part("object_key") key: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse>

    @FormUrlEncoded
    @POST("read-r2-object-text")
    suspend fun readR2ObjectText(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("bucket_name") bucket: String, @Field("object_key") key: String): Response<ApiResponse>

    @FormUrlEncoded
    @POST("list-d1-databases")
    suspend fun listD1Databases(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String): Response<List<D1DatabaseItem>>

    @FormUrlEncoded
    @POST("execute-d1-query")
    suspend fun executeD1Query(@Field("cf_email") email: String, @Field("cf_api_key") apiKey: String, @Field("database_id") dbId: String, @Field("sql_query") sql: String): Response<D1QueryResponse>

    @GET("get-visitor-stats")
    suspend fun getVisitorStats(): Response<VisitorStats>
}
