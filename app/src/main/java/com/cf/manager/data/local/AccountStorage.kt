package com.cf.manager.data.local

import android.content.Context
import com.cf.manager.data.model.CfAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AccountStorage(context: Context) {
    private val prefs = context.getSharedPreferences("cf_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveAccounts(list: List<CfAccount>) {
        prefs.edit().putString("accounts_json", gson.toJson(list)).apply()
    }

    fun getAccounts(): MutableList<CfAccount> {
        val json = prefs.getString("accounts_json", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<CfAccount>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun getActiveIndex(): Int = prefs.getInt("active_idx", 0)
    fun setActiveIndex(idx: Int) = prefs.edit().putInt("active_idx", idx).apply()

    fun getBackendUrl(): String = prefs.getString("backend_url", "https://apiapk.ddfathu11.workers.dev/") ?: "https://apiapk.ddfathu11.workers.dev/"
    fun setBackendUrl(url: String) = prefs.edit().putString("backend_url", url).apply()
}
