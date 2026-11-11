/*
 * Copyright 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.ml.sample.sr.vm

import android.graphics.Bitmap
import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * The wrapper of visual-query-answer RESTFul requests. It is not suggested to construct this
 * wrapper class directly. Instead, you should use [VQAWrapper.create].
 *
 * It wraps two RESTFul requests, each of which can be configured:
 * 1. An image-uploading request (skippable), which _post_ the image to be queried, whose respond
 *    (expected to be a JSON string) will be deserialized to an instance of [IMAGE_RESPOND].
 * 1. A query of the uploaded image to the designated backend, whose respond (also expected to be a
 *    JSON string) will be deserialized to an instance of [VQA_RESPOND]. [queryRespondParser] will
 *    be called to extract the VQA output from the deserialized [VQA_RESPOND] instance.
 *
 * @param IMAGE_RESPOND the class to which the JSON respond from image uploading will be
 *   deserialized. It is recommended to annotate the class with [Serializable]. You can set it to
 *   [Unit] when you set [imageUploadURL] as `null` to skip the image uploading request.
 * @param VQA_RESPOND the class to which the JSON respond from image querying will be deserialized.
 *   It is recommended to annotate the class with [Serializable].
 * @param ableToQuery whether the query is ready to be called. Optional.
 * @param imageFormat the encoding format of the for the image to be sent to VQA, e.g., JPEG, PNG,
 *   etc. Optional.
 * @param imageUploadURL the URL of the image-uploading RESTFul request. An HTTP POST request will
 *   be sent to this URL to upload the image to be queried, whose header will be set according to
 *   [imageUploadHeaders] and whose payload will the a multipart/form-data, including the image and
 *   the [imageUploadAdditionalFormData]. If your VQA backend does not require you to call an image-
 *   uploading request, for example, the VQA backend allows you to embed the image as base64 string
 *   directly in the query's payload, you can set [imageUploadURL] to `null` to skip the
 *   image-uploading request. Then, the encoded image will be passed as the second parameter to
 *   [queryFormatter].
 * @param imageUploadHeaders the headers you want to put into the POST request for image uploading,
 *   in key-value pairs. You don't need to specify the "Content-Type" (together with boundary) here,
 *   which will be automatically populated. Optional.
 * @param imageFormDataName the form data name of the image in the POST request body for image
 *   uploading. Optional. If you use `cURL` to issue an image-uploading from commandline, e.g.,:
 * ```
 * curl https://.... \
 *  -H ... \
 *  -F "upload_file=@/path/to/file/uploaded"
 * ```
 *
 * The `upload_file` is what to be put here.
 *
 * @param imageUploadAdditionalFormData additional form data to be included in the POST body, to
 *   meet specify requirement of different VQA backend. Optional. If you use `cURL` to issue an
 *   image-uploading from commandline, e.g.,:
 * ```
 * curl https://.... \
 *  -H ... \
 *  -F "purpose=user_data" \
 *  -F "expire=1min" \
 *  -F "upload_file=@/path/to/file/uploaded"
 * ```
 *
 * Then you should put here:
 * ```kt
 * mapOf("purpose" to "user_data", "expire" to "1min")
 * ```
 *
 * @param queryUrl the URL of the VQA query. A POST request will be made to the URL, with a JSON
 *   string as the payload. THe JSON string will be formatted by [queryFormatter].
 * @param queryHeaders the headers you want to put into the POST request for the VQA query, in
 *   key-value pairs. You don't need to specify the "Content-Type" (together with boundary) here,
 *   which will be automatically populated. Optional.
 * @param queryFormatter the payload formatter for the VQA query request. The two inputs are the
 *   [IMAGE_RESPOND] deserialize from the image uploading request's respond, and the raw byte array
 *   of the encoded image. The first parameter will be `null` if you skip the image-uploading
 *   request by setting [imageUploadURL] as `null`, in which case, you can embed the second
 *   parameter into the payload directly.
 * @param queryRespondParser Parser of the responds from the VQA query request. The respond will be
 *   deserialized to an instance of [VQA_RESPOND] as the first parameter to this parser. The
 *   parser's responsibility is to extract the text to be displayed from it. Optional. The default
 *   behavior is to return the entire [VQA_RESPOND] as string. The returned result will shown to
 *   users.
 * @param imageRespondSerializer the serializer for [IMAGE_RESPOND]. It can be `null` **only when**
 *   [imageUploadURL] is `null`, i.e., the image uploading should be skipped.
 * @param vqaRespondSerializer the serializer for [VQA_RESPOND].
 * @sample Default.ark
 */
class VQAWrapper<IMAGE_RESPOND, VQA_RESPOND>(
    val ableToQuery: () -> Boolean = { true },
    private val imageFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    private val imageUploadURL: URL?,
    private val imageUploadHeaders: Map<String, String>? = null,
    private val imageFormDataName: String = "file",
    private val imageUploadAdditionalFormData: Map<String, String>? = null,
    private val queryUrl: URL,
    private val queryHeaders: Map<String, String>? = null,
    private val queryFormatter: (IMAGE_RESPOND?, ByteArray) -> String,
    private val queryRespondParser: (VQA_RESPOND) -> String = { it.toString() },
    private val imageRespondSerializer: KSerializer<IMAGE_RESPOND>?,
    private val vqaRespondSerializer: KSerializer<VQA_RESPOND>,
) {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private var count = AtomicInteger(0)

    private fun rgbEncoder(format: Bitmap.CompressFormat, rgbaBitmap: Bitmap): ByteArray {
        // Encode JPEG
        val jpegStream = ByteArrayOutputStream()
        rgbaBitmap.compress(format, 95, jpegStream)
        return jpegStream.toByteArray()
    }

    @SuppressWarnings("ComplexCondition")
    private suspend fun uploadImageJpeg(name: String, encodedImage: ByteArray): IMAGE_RESPOND? =
        withContext(Dispatchers.IO) {
            if (imageUploadURL == null) {
                return@withContext null
            }
            val boundary = "----ArkBoundary${System.currentTimeMillis()}"
            val conn =
                (imageUploadURL.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    imageUploadHeaders?.forEach { (key, value) -> setRequestProperty(key, value) }
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

            DataOutputStream(conn.outputStream).use { out ->
                // purpose=user_data
                imageUploadAdditionalFormData?.forEach { (key, value) ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
                    out.writeBytes("$value\r\n")
                }

                // file part
                out.writeBytes("--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"${imageFormDataName}\"; filename=\"$name.jpg\"\r\n"
                )
                when (imageFormat) {
                    Bitmap.CompressFormat.JPEG -> out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    Bitmap.CompressFormat.PNG -> out.writeBytes("Content-Type: image/png\r\n\r\n")
                    else -> out.writeBytes("Content-Type: image/WEBP\r\n\r\n")
                }
                out.write(encodedImage)
                out.writeBytes("\r\n--$boundary--\r\n")
            }

            val responseText = conn.inputStream.bufferedReader().readText()

            try {
                jsonConfig.decodeFromString(imageRespondSerializer!!, responseText)
            } catch (e: SerializationException) {
                throw VQAException("file upload error: get exception: $e", e)
            } catch (e: IllegalArgumentException) {
                throw VQAException("file upload error: get exception: $e", e)
            }
        }

    /**
     * Launch VQA query for an image.
     *
     * Step-by-step:
     * 1. Encode the image as the given format.
     * 1. Make the image-uploading request, if the image-uploading URL [imageUploadURL] is not NULL.
     *    with the headers in [imageUploadHeaders], the encoded image as a form-data entry called
     *    [imageFormDataName], and the additional form data entries in
     *    [imageUploadAdditionalFormData].
     * 1. Deserialized the responds from the image-uploading request to [IMAGE_RESPOND], if the
     *    [imageUploadURL] is not null.
     * 1. Pass the deserialization result (null is no image-uploading request has been made) and the
     *    encoded image to the [queryFormatter] to format a payload string (expected to be a JSON
     *    string).
     * 1. Make the query request toward the [queryUrl], with header key-value pairs given in
     *    [queryHeaders].
     * 1. Deserialize the respond from the query result to [VQA_RESPOND]
     * 1. Run the [queryRespondParser] on the deserialization result, to get the text output and
     *    return it.
     *
     * @throws VQAException if the network request fails, or the deserialization cannot be
     *   completed.
     */
    suspend fun queryImage(rgbaBitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val encodedImage = rgbEncoder(imageFormat, rgbaBitmap)

            val payload =
                queryFormatter(
                    uploadImageJpeg(
                        "upscaled_image_from_camera_${count.addAndGet(1)}",
                        encodedImage,
                    ),
                    encodedImage,
                )

            val conn =
                (queryUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    queryHeaders?.forEach { (key, value) -> setRequestProperty(key, value) }
                    setRequestProperty("Content-Type", "application/json")
                }

            conn.outputStream.use { it.write(payload.toByteArray()) }

            val responseText = conn.inputStream.bufferedReader().readText()

            val parsed =
                try {
                    jsonConfig.decodeFromString(vqaRespondSerializer, responseText)
                } catch (e: SerializationException) {
                    throw VQAException("query error: get exception: $e", e)
                } catch (e: IllegalArgumentException) {
                    throw VQAException("query error: get exception: $e", e)
                }
            queryRespondParser(parsed)
        }

    /** The default VQA wrapper, for your convenience. */
    object Default {
        /** The deserialization for the [ark]'s image uploading. */
        @Serializable
        data class FileUploadResponse(
            val `object`: String,
            val id: String,
            val purpose: String,
            val filename: String,
            val bytes: Int,
            @SerialName("mime_type") val mimeType: String,
            val status: String,
        ) {
            val isValid: Boolean
                get() {
                    return id.isNotEmpty() &&
                        `object` == "file" &&
                        mimeType == "image/jpeg" &&
                        bytes != 0
                }
        }

        /**
         * The deserialization for the [ark]'s VQA query
         *
         * @param type the content type, i.e., thinking or summary or the final output.
         * @param text the content body.
         */
        @Serializable data class ContentFromArk(val type: String, val text: String)

        /** The deserialization for the [ark]'s VQA query. */
        @Serializable
        data class LLMOutput(
            val type: String,
            val content: Array<ContentFromArk>? = null,
            val status: String,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as LLMOutput

                if (type != other.type) return false
                if (!content.contentEquals(other.content)) return false
                if (status != other.status) return false
                return true
            }

            override fun hashCode(): Int {
                var result = type.hashCode()
                result = 31 * result + (content?.contentHashCode() ?: 0)
                result = 31 * result + status.hashCode()
                return result
            }
        }

        /**
         * The statistics fields in the [ark]'s VQA respond, also served as a part of the
         * deserialization.
         */
        @Serializable
        data class Usage(
            @SerialName("input_tokens") val inputTokens: Int,
            @SerialName("output_tokens") val outputTokens: Int,
            @SerialName("total_tokens") val totalTokens: Int,
        )

        /** The deserialization for the [ark]'s VQA query. */
        @Serializable
        data class QueryResponse(
            val `object`: String,
            val output: Array<LLMOutput>,
            val usage: Usage,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as QueryResponse

                if (`object` != other.`object`) return false
                if (!output.contentEquals(other.output)) return false
                if (usage != other.usage) return false

                return true
            }

            override fun hashCode(): Int {
                var result = `object`.hashCode()
                result = 31 * result + output.contentHashCode()
                result = 31 * result + usage.hashCode()
                return result
            }
        }

        private const val QUESTION_STR =
            "This is an image from the pass-through camera on a headset, upscaled by a" +
                " super-resolution neural network. Please help the user to understand what he/she" +
                " is seeing. If there are some products seen in the image, please also provide" +
                " the purchase information for those. Please do not use markdown and make" +
                " your outputs short, no more than 60 words."

        private val apiKey = getApiKeyProp()

        /**
         * The default VQA backend, based on ByteDance's Volcano engine. To use the default one, you
         * will need to apply for a Volcano API key, and run locally:
         * ```
         * adb shell setprop debug.spatialml.apikey <API-KEY>
         * ```
         *
         * to set the API key to the device.
         */
        val ark
            get() =
                create<FileUploadResponse, QueryResponse>(
                    ableToQuery = { apiKey.isNotEmpty() },
                    imageFormat = Bitmap.CompressFormat.JPEG,
                    imageUploadURL = URL("https://ark-cn-beijing.bytedance.net/api/v3/files"),
                    imageUploadHeaders = mapOf("Authorization" to "Bearer $apiKey"),
                    imageFormDataName = "file",
                    imageUploadAdditionalFormData = mapOf("purpose" to "user_data"),
                    queryUrl = URL("https://ark-cn-beijing.bytedance.net/api/v3/responses"),
                    queryHeaders = mapOf("Authorization" to "Bearer $apiKey"),
                    queryFormatter = { imageUploadRespond, _ ->
                        if (imageUploadRespond == null) {
                            throw VQAException("file upload error: get null response")
                        } else if (!imageUploadRespond.isValid) {
                            throw VQAException(
                                "file upload error: get response $imageUploadRespond"
                            )
                        } else if (imageUploadRespond.purpose != "user_data") {
                            Log.w(
                                "SpatialML",
                                "file upload gets unexpected result: $imageUploadRespond",
                            )
                        } else {
                            Log.i("SpatialML", "file uploaded to ARK: $imageUploadRespond")
                        }
                        """
                        {
                          "model": "ep-20251218093425-rl48r",
                          "input": [
                            {
                              "role": "user",
                              "content": [
                                { "type": "input_image", "file_id": "${imageUploadRespond.id}" }
                              ]
                            },
                            {
                              "role": "system",
                              "content": [
                                { "type": "input_text", "text": "$QUESTION_STR" }
                              ]
                            }
                          ]
                        }
                        """
                            .trimIndent()
                    },
                    queryRespondParser = { parsed ->
                        if (parsed.`object` != "response") {
                            throw VQAException("VQA get invalid response: $parsed")
                        } else if (parsed.output.isEmpty()) {
                            throw VQAException("VQA get empty response: $parsed")
                        }

                        (parsed.output.firstOrNull {
                                it.type == "message" &&
                                    it.content != null &&
                                    it.content.isNotEmpty()
                            }
                                ?: throw VQAException(
                                    "VQA's response has no valid assistant message: $parsed"
                                ))
                            .content!!
                            .let { if (it.size > 1) it[0].text + "\n\n......" else it[0].text }
                    },
                )

        @SuppressWarnings("TooGenericExceptionCaught")
        private fun getApiKeyProp(): String =
            try {
                val process =
                    Runtime.getRuntime().exec(arrayOf("getprop", "debug.spatialml.apikey"))
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.readLine()?.trim() ?: ""
                }
            } catch (e: Exception) {
                Log.e("SpatialML", "fail to get prop for API key, due to $e")
                ""
            }
    }

    companion object {
        @JvmStatic

        /**
         * The helper function to create the [VQAWrapper].
         *
         * @param IMAGE_RESPOND the class to which the JSON respond from image uploading will be
         *   deserialized. It is recommended to annotate the class with [Serializable]. You can set
         *   it to [Unit] when you set [imageUploadURL] as `null` to skip the image uploading
         *   request.
         * @param VQA_RESPOND the class to which the JSON respond from image querying will be
         *   deserialized. It is recommended to annotate the class with [Serializable].
         * @param ableToQuery whether the query is ready to be called. Optional.
         * @param imageFormat the encoding format of the for the image to be sent to VQA, e.g.,
         *   JPEG, PNG, etc. Optional.
         * @param imageUploadURL the URL of the image-uploading RESTFul request. An HTTP POST
         *   request will be sent to this URL to upload the image to be queried, whose header will
         *   be set according to [imageUploadHeaders] and whose payload will the a
         *   multipart/form-data, including the image and the [imageUploadAdditionalFormData]. If
         *   your VQA backend does not require you to call an image- uploading request, for example,
         *   the VQA backend allows you to embed the image as base64 string directly in the query's
         *   payload, you can set [imageUploadURL] to `null` to skip the image-uploading request.
         *   Then, the encoded image will be passed as the second parameter to [queryFormatter].
         * @param imageUploadHeaders the headers you want to put into the POST request for image
         *   uploading, in key-value pairs. You don't need to specify the "Content-Type" (together
         *   with boundary) here, which will be automatically populated. Optional.
         * @param imageFormDataName the form data name of the image in the POST request body for
         *   image uploading. Optional. If you use `cURL` to issue an image-uploading from
         *   commandline, e.g.,:
         * ```
         * curl https://.... \
         *  -H ... \
         *  -F "upload_file=@/path/to/file/uploaded"
         * ```
         *
         * The `upload_file` is what to be put here.
         *
         * @param imageUploadAdditionalFormData additional form data to be included in the POST
         *   body, to meet specify requirement of different VQA backend. Optional. If you use `cURL`
         *   to issue an image-uploading from commandline, e.g.,:
         * ```
         * curl https://.... \
         *  -H ... \
         *  -F "purpose=user_data" \
         *  -F "expire=1min" \
         *  -F "upload_file=@/path/to/file/uploaded"
         * ```
         *
         * Then you should put here:
         * ```kt
         * mapOf("purpose" to "user_data", "expire" to "1min")
         * ```
         *
         * @param queryUrl the URL of the VQA query. A POST request will be made to the URL, with a
         *   JSON string as the payload. THe JSON string will be formatted by [queryFormatter].
         * @param queryHeaders the headers you want to put into the POST request for the VQA query,
         *   in key-value pairs. You don't need to specify the "Content-Type" (together with
         *   boundary) here, which will be automatically populated. Optional.
         * @param queryFormatter the payload formatter for the VQA query request. The two inputs are
         *   the [IMAGE_RESPOND] deserialize from the image uploading request's respond, and the raw
         *   byte array of the encoded image. The first parameter will be `null` if you skip the
         *   image-uploading request by setting [imageUploadURL] as `null`, in which case, you can
         *   embed the second parameter into the payload directly.
         * @param queryRespondParser Parser of the responds from the VQA query request. The respond
         *   will be deserialized to an instance of [VQA_RESPOND] as the first parameter to this
         *   parser. The parser's responsibility is to extract the text to be displayed from it.
         *   Optional. The default behavior is to return the entire [VQA_RESPOND] as string. The
         *   returned result will shown to users.
         * @see VQAWrapper
         */
        inline fun <reified IMAGE_RESPOND, reified VQA_RESPOND> create(
            noinline ableToQuery: () -> Boolean = { true },
            imageFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
            imageUploadURL: URL?,
            imageUploadHeaders: Map<String, String>? = null,
            imageFormDataName: String = "file",
            imageUploadAdditionalFormData: Map<String, String>? = null,
            queryUrl: URL,
            queryHeaders: Map<String, String>? = null,
            noinline queryFormatter: (IMAGE_RESPOND?, ByteArray) -> String,
            noinline queryRespondParser: (VQA_RESPOND) -> String = { it.toString() },
        ): VQAWrapper<IMAGE_RESPOND, VQA_RESPOND> {
            val imageSerializer =
                try {
                    serializer<IMAGE_RESPOND>()
                } catch (e: SerializationException) {
                    imageUploadURL?.let { throw e }
                    null
                }
            return VQAWrapper(
                ableToQuery,
                imageFormat,
                imageUploadURL,
                imageUploadHeaders,
                imageFormDataName,
                imageUploadAdditionalFormData,
                queryUrl,
                queryHeaders,
                queryFormatter,
                queryRespondParser,
                imageSerializer,
                serializer<VQA_RESPOND>(),
            )
        }
    }
}
