/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl

import io.ktor.client.engine.*
import io.ktor.client.engine.curl.internal.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*

internal class CurlClientEngine(override val config: CurlClientEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    private val curlProcessor = CurlProcessor(coroutineContext)

    override suspend fun execute(
        data: HttpRequestData
    ): HttpResponseData {
        val callContext = coroutineContext + Job()
        val requestTime = GMTDate()

        val curlRequest = data.toCurlRequest(config)
        val responseData = curlProcessor.executeRequest(curlRequest)

        return with(responseData) {
            val headerBytes = ByteReadChannel(headersBytes).apply {
                readUTF8Line()
            }
            val rawHeaders = parseHeaders(headerBytes)

            val body = writer(coroutineContext) {
                channel.writeFully(bodyBytes)
            }.channel

            val status = HttpStatusCode.fromValue(status)

            val headers = buildHeaders {
                appendAll(CIOHeaders(rawHeaders))
                rawHeaders.release()
            }

            HttpResponseData(
                status, requestTime, headers, version.fromCurl(),
                body, callContext
            )
        }
    }

    override fun close() {
        curlProcessor.close()
        coroutineContext.cancel()
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated("This exception will be removed in a future release in favor of a better error handling.")
class CurlIllegalStateException(cause: String) : IllegalStateException(cause)

@Suppress("KDocMissingDocumentation")
@Deprecated("This exception will be removed in a future release in favor of a better error handling.")
class CurlRuntimeException(cause: String) : RuntimeException(cause)
