package com.bydmate.app.data.automation

import android.util.Log
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.WebhookTelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * The `webhook` automation action: one HTTP request to a URL of the user's choosing.
 *
 * Payload (JSON): `url` (https, or http on localhost only — the platform blocks other
 * cleartext hosts), `method` ("POST" default, or "GET"), optional `body` and optional `secret`
 * (sent as `Authorization: Bearer`, never logged). An empty body POSTs a JSON snapshot
 * (action name, time, SoC, speed, gear). The body may carry placeholders: {rule} (the action's
 * name) {ts} {soc} {speed} {gear} {mileage}.
 */
class WebhookAction(
    private val httpClient: OkHttpClient,
) {
    data class Spec(val url: String, val method: String, val body: String, val secret: String)

    companion object {
        const val KIND = "webhook"
        private const val TAG = "WebhookAction"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parse(payload: String?): Spec? = try {
            val json = JSONObject(payload ?: return null)
            Spec(
                url = json.optString("url").trim(),
                method = json.optString("method", "POST").uppercase().takeIf { it == "GET" } ?: "POST",
                body = json.optString("body"),
                secret = json.optString("secret"),
            )
        } catch (_: Exception) {
            null
        }

        fun payload(spec: Spec): String = JSONObject()
            .put("url", spec.url)
            .put("method", spec.method)
            .put("body", spec.body)
            .put("secret", spec.secret)
            .toString()

        /** The rule line: "Webhook POST example.com" (host only: the path may carry a token). */
        fun displayLabel(spec: Spec): String {
            val host = spec.url.toHttpUrlOrNull()?.host ?: spec.url
            return "Webhook ${spec.method} $host"
        }

        /** Null when [url] is sendable, else why not (for the validator and the dispatcher). */
        fun urlProblem(url: String): String? {
            val httpUrl = url.trim().toHttpUrlOrNull() ?: return "invalid URL"
            return if (WebhookTelemetryClient.isAllowedWebhookUrl(httpUrl)) null
            else "only https (or http://localhost) is allowed"
        }

        internal fun fill(template: String, ruleName: String, nowMs: Long, data: DiParsData?): String =
            template
                .replace("{rule}", ruleName)
                .replace("{ts}", nowMs.toString())
                .replace("{soc}", data?.soc?.toString() ?: "")
                .replace("{speed}", data?.speed?.toString() ?: "")
                .replace("{gear}", data?.gear?.toString() ?: "")
                .replace("{mileage}", data?.mileage?.toString() ?: "")

        internal fun defaultBody(ruleName: String, nowMs: Long, data: DiParsData?): String = JSONObject()
            .put("source", "bydmate")
            .put("rule", ruleName)
            .put("ts", nowMs)
            .put("soc", data?.soc ?: JSONObject.NULL)
            .put("speed", data?.speed ?: JSONObject.NULL)
            .put("gear", data?.gear ?: JSONObject.NULL)
            .put("mileage", data?.mileage ?: JSONObject.NULL)
            .toString()
    }

    suspend fun send(spec: Spec, ruleName: String, data: DiParsData?): DispatchResult =
        withContext(Dispatchers.IO) {
            urlProblem(spec.url)?.let { return@withContext DispatchResult(false, "Webhook: $it") }
            val now = System.currentTimeMillis()
            val body = if (spec.body.isBlank()) defaultBody(ruleName, now, data) else fill(spec.body, ruleName, now, data)
            try {
                val request = Request.Builder()
                    .url(spec.url)
                    .apply {
                        if (spec.method == "GET") get() else post(body.toRequestBody(JSON_MEDIA_TYPE))
                        spec.secret.trim().takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") }
                    }
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    // Never log the URL or body: they can carry tokens.
                    Log.i(TAG, "HTTP ${response.code}")
                    if (response.isSuccessful) DispatchResult(true)
                    else DispatchResult(false, "Webhook: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "request failed: ${e.javaClass.simpleName}")
                DispatchResult(false, "Webhook: ${e.javaClass.simpleName}")
            }
        }
}
