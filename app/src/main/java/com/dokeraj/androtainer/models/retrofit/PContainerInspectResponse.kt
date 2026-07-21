package com.dokeraj.androtainer.models.retrofit

import com.google.gson.annotations.SerializedName

/** Subset of the Docker inspect payload (GET .../containers/{id}/json). */
data class PContainerInspectResponse(
    @SerializedName("State") val state: PInspectState?,
    @SerializedName("RestartCount") val restartCount: Int?,
)

data class PInspectState(
    @SerializedName("Status") val status: String?,
    @SerializedName("OOMKilled") val oomKilled: Boolean?,
    @SerializedName("ExitCode") val exitCode: Int?,
    @SerializedName("StartedAt") val startedAt: String?,
    @SerializedName("Health") val health: PInspectHealth?,
)

data class PInspectHealth(
    @SerializedName("Status") val status: String?,
    @SerializedName("FailingStreak") val failingStreak: Int?,
)
