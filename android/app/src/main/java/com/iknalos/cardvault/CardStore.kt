package com.iknalos.cardvault

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Card storage backed by EncryptedSharedPreferences: values are AES-256-GCM
 * encrypted with a key held in the Android Keystore, so card numbers are
 * unreadable even if the preference file is extracted from the device.
 *
 * The JSON schema matches the CardVault web app's localStorage format so
 * backups can be moved between the two.
 */
object CardStore {
    private const val CARDS_KEY = "cards"
    private const val LOCK_KEY = "lock_enabled"

    val COLORS = listOf("#6c7bff", "#ff5c7a", "#2ec4a6", "#ffa94d", "#c377e0", "#4dabf7", "#94a05a", "#8d99ae")

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            val master = MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val p = EncryptedSharedPreferences.create(
                ctx.applicationContext,
                "cardvault_secure",
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs = p
            return p
        }
    }

    fun load(ctx: Context): MutableList<Card> =
        try { fromJson(prefs(ctx).getString(CARDS_KEY, "[]") ?: "[]") } catch (e: Exception) { mutableListOf() }

    fun save(ctx: Context, cards: List<Card>) {
        prefs(ctx).edit().putString(CARDS_KEY, toJson(cards)).apply()
    }

    fun isLockEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(LOCK_KEY, false)
    fun setLockEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(LOCK_KEY, enabled).apply()
    }

    fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) +
            UUID.randomUUID().toString().replace("-", "").substring(0, 6)

    fun nowIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    fun toJson(cards: List<Card>): String {
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("value", c.value)
                put("format", c.format)
                put("notes", c.notes)
                put("color", c.color)
                put("created", c.created)
            })
        }
        return arr.toString(2)
    }

    fun fromJson(text: String): MutableList<Card> {
        val arr = JSONArray(text)
        val out = mutableListOf<Card>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val value = o.optString("value", "")
            if (value.isEmpty()) continue
            out.add(
                Card(
                    id = o.optString("id").ifEmpty { newId() },
                    name = o.optString("name").ifEmpty { "Imported card" },
                    value = value,
                    format = o.optString("format").ifEmpty { "code128" },
                    notes = o.optString("notes", ""),
                    color = o.optString("color").ifEmpty { COLORS[0] },
                    created = o.optString("created").ifEmpty { nowIso() }
                )
            )
        }
        return out
    }
}
