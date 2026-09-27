package com.forgebuild.taskflow.ai

import com.forgebuild.taskflow.settings.GeminiKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/** Direct client for the Gemini generateContent API with function calling. Keys go to Google only. */
class GeminiClient(private val keyStore: GeminiKeyStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000 }
        expectSuccess = false
    }

    private val model = "gemini-flash-lite-latest"
    private fun url() = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    class AllKeysFailedException : Exception("Every configured Gemini API key failed.")
    class NoKeysException : Exception("No Gemini API key configured.")
    class HighUsageExhaustedException(msg: String) : Exception(msg)
    class InvalidApiKeyException(msg: String) : Exception(msg)

    private fun isTemporaryHighUsage(status: Int, body: String): Boolean {
        if (status == 429 || status == 503 || status == 500 || status == 502 || status == 504) return true
        val lower = body.lowercase()
        return lower.contains("resource_exhausted") ||
                lower.contains("rate limit") ||
                lower.contains("quota exceeded") ||
                lower.contains("temporarily unavailable") ||
                lower.contains("high load") ||
                lower.contains("overloaded") ||
                lower.contains("capacity")
    }

    /** Send the conversation; returns the parsed response JSON. Fails over across keys and retries high-load states. */
    suspend fun generate(contents: JsonArray, tools: JsonArray?, systemInstruction: String): JsonObject {
        val keys = keyStore.keys.value
        if (keys.isEmpty()) throw NoKeysException()
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemInstruction) }) }
            }
            put("contents", contents)
            if (tools != null) put("tools", tools)
            putJsonObject("generationConfig") { put("temperature", 0.4); put("maxOutputTokens", 2048) }
        }

        var sawHighUsage = false
        var sawInvalidKey = false
        var lastError: Exception? = null

        for (key in keys) {
            val maxRetries = 4
            for (attempt in 0..maxRetries) {
                try {
                    val resp = client.post(url()) {
                        contentType(ContentType.Application.Json)
                        header("x-goog-api-key", key)
                        setBody(body.toString())
                    }
                    val text = resp.body<String>()
                    val status = resp.status.value

                    if (status in 200..299) {
                        return json.parseToJsonElement(text).jsonObject
                    }

                    if (isTemporaryHighUsage(status, text)) {
                        sawHighUsage = true
                        lastError = Exception("Gemini high usage (HTTP $status): $text")
                        if (attempt < maxRetries) {
                            val retryAfterHeader = resp.headers["Retry-After"]?.toLongOrNull()
                            val waitMs = if (retryAfterHeader != null && retryAfterHeader in 1..15) {
                                retryAfterHeader * 1000L
                            } else {
                                val base = when (attempt) {
                                    0 -> 1500L
                                    1 -> 3000L
                                    2 -> 6000L
                                    else -> 10000L
                                }
                                base + Random.nextLong(100L, 500L)
                            }
                            delay(waitMs)
                            continue
                        } else {
                            break
                        }
                    }

                    if (status in listOf(400, 401, 403)) {
                        sawInvalidKey = true
                    }
                    lastError = Exception("HTTP $status: $text")
                    break

                } catch (e: Exception) {
                    lastError = e
                    if (attempt < maxRetries) {
                        delay(1500L + attempt * 1500L)
                        continue
                    }
                    break
                }
            }
        }

        if (sawHighUsage && !sawInvalidKey) {
            throw HighUsageExhaustedException("Gemini servers are experiencing high usage right now. Retries were exhausted; please wait a moment and try again.").also {
                it.initCause(lastError)
            }
        }
        if (sawInvalidKey && !sawHighUsage) {
            throw InvalidApiKeyException("Configured Gemini API key is invalid or rejected by Google. Please check Settings.").also {
                it.initCause(lastError)
            }
        }
        throw AllKeysFailedException().also { it.initCause(lastError) }
    }

    companion object {
        fun textPart(t: String): JsonObject = buildJsonObject { put("text", t) }
        /** Raw audio clip for Gemini's native audio understanding (no local transcription). */
        fun inlineDataPart(mimeType: String, base64Data: String): JsonObject = buildJsonObject {
            putJsonObject("inlineData") {
                put("mimeType", mimeType)
                put("data", base64Data)
            }
        }
        fun functionCallPart(name: String, args: JsonObject): JsonObject = buildJsonObject {
            putJsonObject("functionCall") { put("name", name); put("args", args) }
        }
        fun functionResponsePart(name: String, result: String): JsonObject = buildJsonObject {
            putJsonObject("functionResponse") { put("name", name); putJsonObject("response") { put("result", JsonPrimitive(result)) } }
        }
        fun content(role: String, parts: List<JsonObject>): JsonObject = buildJsonObject {
            put("role", role); put("parts", JsonArray(parts))
        }
    }
}
