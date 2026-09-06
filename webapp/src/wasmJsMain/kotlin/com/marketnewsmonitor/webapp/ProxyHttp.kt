package com.marketnewsmonitor.webapp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The local proxy started by scripts/run_web.ps1 (see server/) -- never a real internet host. */
private const val PROXY_BASE_URL = "http://localhost:8787"

private val proxyHttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

suspend fun proxyGet(path: String): String = proxyHttpClient.get("$PROXY_BASE_URL$path").bodyAsText()

suspend fun proxyPost(path: String, body: String): String =
    proxyHttpClient.post("$PROXY_BASE_URL$path") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.bodyAsText()
