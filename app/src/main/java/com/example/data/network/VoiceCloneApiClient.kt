package com.example.data.network

import android.content.Context
import com.example.data.model.OutputFormat
import com.example.data.model.ServerHealthInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VoiceCloneApiClient(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun checkHealth(endpointBase: String, apiKey: String): ServerHealthInfo =
        withContext(Dispatchers.IO) {
            val base = endpointBase.trim().trimEnd('/')
            if (base.isEmpty()) {
                return@withContext ServerHealthInfo(
                    isReachable = false,
                    statusText = "Endpoint URL is empty"
                )
            }

            val started = System.currentTimeMillis()
            runCatching {
                val requestBuilder = Request.Builder()
                    .url("$base/health")
                    .get()
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
                    requestBuilder.header("X-API-Key", apiKey.trim())
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - started
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext ServerHealthInfo(
                            isReachable = false,
                            statusText = "HTTP ${response.code}: ${response.message}",
                            latencyMs = elapsed
                        )
                    }
                    val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                    ServerHealthInfo(
                        isReachable = true,
                        statusText = json?.optString("status", "ok") ?: "ok",
                        backendModel = json?.optString("model", "XTTS-v2 / FastAPI") ?: "FastAPI",
                        device = json?.optString("device", "unknown") ?: "unknown",
                        latencyMs = elapsed
                    )
                }
            }.getOrElse { err ->
                ServerHealthInfo(
                    isReachable = false,
                    statusText = err.localizedMessage ?: "Connection failed"
                )
            }
        }

    suspend fun cloneVoice(
        endpointBase: String,
        apiKey: String,
        referenceFile: File,
        text: String,
        language: String,
        speed: Float,
        similarity: Float,
        temperature: Float,
        outputFormat: OutputFormat,
        onUploadCompleted: () -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val base = endpointBase.trim().trimEnd('/')
            require(base.startsWith("http://") || base.startsWith("https://")) {
                "API endpoint must start with http:// or https://"
            }
            require(referenceFile.exists() && referenceFile.length() > 0L) {
                "Reference audio file not found"
            }

            val mediaType = when (referenceFile.extension.lowercase()) {
                "mp3" -> "audio/mpeg".toMediaType()
                "m4a" -> "audio/mp4".toMediaType()
                "flac" -> "audio/flac".toMediaType()
                else -> "audio/wav".toMediaType()
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "reference_audio",
                    referenceFile.name,
                    referenceFile.asRequestBody(mediaType)
                )
                .addFormDataPart("text", text)
                .addFormDataPart("language", language)
                .addFormDataPart("speed", speed.toString())
                .addFormDataPart("similarity", similarity.toString())
                .addFormDataPart("temperature", temperature.toString())
                .addFormDataPart("output_format", outputFormat.extension)
                .build()

            val requestBuilder = Request.Builder()
                .url("$base/api/v1/clone")
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
                requestBuilder.header("X-API-Key", apiKey.trim())
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                onUploadCompleted()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(300).orEmpty()
                    throw IllegalStateException("Server returned HTTP ${response.code}: $errorBody")
                }

                val outDir = File(context.filesDir, "outputs").apply { mkdirs() }
                val outFile = File(outDir, "clone_${System.currentTimeMillis()}.${outputFormat.extension}")
                val bodyStream = response.body?.byteStream()
                    ?: throw IllegalStateException("Empty audio response from server")

                FileOutputStream(outFile).use { fos ->
                    bodyStream.copyTo(fos)
                }

                if (outFile.length() == 0L) {
                    throw IllegalStateException("Server returned 0 bytes of audio")
                }
                outFile
            }
        }
    }
}
