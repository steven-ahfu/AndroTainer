package com.dokeraj.androtainer.globalvars

import android.content.Context
import androidx.core.content.edit
import com.dokeraj.androtainer.models.AlertState
import com.dokeraj.androtainer.models.ContainerThreshold
import com.dokeraj.androtainer.models.Credential
import com.dokeraj.androtainer.models.CredentialDeserializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.Instant

/** SharedPreferences-backed store for threshold monitoring — usable from both the
 * UI and StatsMonitorWorker (which runs without MainActiviy/GlobalApp hydration). */
object MonitorStore {

    fun readThresholds(context: Context): List<ContainerThreshold> {
        val prefs = context.getSharedPreferences(PermaVals.SP_DB, Context.MODE_PRIVATE)
        val json = prefs.getString(PermaVals.CONTAINER_THRESHOLDS, null) ?: return emptyList()
        return try {
            Gson().fromJson(json, Array<ContainerThreshold>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun writeThresholds(context: Context, thresholds: List<ContainerThreshold>) {
        val prefs = context.getSharedPreferences(PermaVals.SP_DB, Context.MODE_PRIVATE)
        prefs.edit { putString(PermaVals.CONTAINER_THRESHOLDS, Gson().toJson(thresholds)) }
    }

    fun readAlertState(context: Context): AlertState {
        val prefs = context.getSharedPreferences(PermaVals.SP_DB, Context.MODE_PRIVATE)
        val json = prefs.getString(PermaVals.ALERT_STATE, null) ?: return AlertState()
        return try {
            Gson().fromJson(json, AlertState::class.java)
        } catch (e: Exception) {
            AlertState()
        }
    }

    fun writeAlertState(context: Context, state: AlertState) {
        val prefs = context.getSharedPreferences(PermaVals.SP_DB, Context.MODE_PRIVATE)
        prefs.edit { putString(PermaVals.ALERT_STATE, Gson().toJson(state)) }
    }

    /** replicates MainActiviy.initializeGlobalVar(): freshest persisted credential
     * (max lastActivity), read through the tolerant CredentialDeserializer */
    fun readCurrentCredential(context: Context): Credential? {
        val prefs = context.getSharedPreferences(PermaVals.SP_DB, Context.MODE_PRIVATE)
        val usersStr = prefs.getString(PermaVals.USERS_CREDENTIALS, null) ?: return null
        return try {
            val gson = GsonBuilder()
                .registerTypeAdapter(Credential::class.java, CredentialDeserializer())
                .create()
            gson.fromJson(usersStr, Array<Credential>::class.java)
                .filter { it.lastActivity != null }
                .maxByOrNull { it.lastActivity!! }
        } catch (e: Exception) {
            null
        }
    }

    /** JWT validity per the MainActiviy.isJwtValid() sentinel semantics:
     * API-key users: valid while jwtValidUntil == 0L (the "hack" sentinel);
     * JWT users: valid while now < jwtValidUntil */
    fun isCredentialUsable(credential: Credential): Boolean {
        if (credential.jwt == null) return false
        return if (credential.isUsingApiKey)
            credential.jwtValidUntil == 0L
        else
            credential.jwtValidUntil?.let {
                Instant.now().isBefore(Instant.ofEpochMilli(it))
            } == true
    }
}
