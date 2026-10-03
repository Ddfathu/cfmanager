package com.cf.manager.data.api

import com.cf.manager.data.AppConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var currentBaseUrl = AppConfig.DEFAULT_BASE_URL

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var retrofit: Retrofit = buildRetrofit(currentBaseUrl)

    private fun buildRetrofit(url: String): Retrofit {
        val cleanUrl = if (url.endsWith("/")) url else "$url/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    var api: WorkerApi = retrofit.create(WorkerApi::class.java)
        private set

    fun updateBaseUrl(newUrl: String) {
        if (newUrl.isNotBlank() && newUrl != currentBaseUrl) {
            currentBaseUrl = newUrl
            retrofit = buildRetrofit(newUrl)
            api = retrofit.create(WorkerApi::class.java)
        }
    }
}
