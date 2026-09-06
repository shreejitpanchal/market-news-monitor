package com.marketnewsmonitor.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File
import java.util.Properties

/**
 * Local-only relay: forwards requests to the real data sources and attaches
 * the right API key/header, so the browser never holds a secret. Binds to
 * 127.0.0.1 only. Deliberately dumb -- no business logic here, that all
 * lives in webapp/ (ported from app/'s already-working equivalents).
 */

private const val PORT = 8787
private const val WEBAPP_DEV_ORIGIN = "localhost:8080"

// Gradle's `run` task working directory is this module's own project dir
// (server/), so this resolves to server/local.properties without needing
// the "server/" prefix.
private val config: Properties by lazy {
    Properties().apply {
        val file = File("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
}

private fun configValue(key: String): String? = config.getProperty(key)?.takeIf { it.isNotBlank() }

private fun userAgentHeader(): String {
    val name = configValue("user.name").orEmpty()
    val email = configValue("user.email").orEmpty()
    val contact = listOf(name, email).filter { it.isNotBlank() }.joinToString(" ")
    return if (contact.isBlank()) "MarketNewsMonitor (personal use; no contact provided)" else "MarketNewsMonitor ($contact)"
}

private val httpClient = HttpClient(CIO)

fun main() {
    embeddedServer(Netty, port = PORT, host = "127.0.0.1") {
        install(CORS) {
            allowHost(WEBAPP_DEV_ORIGIN)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
        }

        routing {
            get("/proxy/status") {
                val body = buildString {
                    append("{")
                    append("\"claudeConfigured\":${configValue("claude.apiKey") != null},")
                    append("\"finnhubConfigured\":${configValue("finnhub.apiKey") != null},")
                    append("\"alphaVantageConfigured\":${configValue("alphaVantage.apiKey") != null},")
                    append("\"name\":\"${configValue("user.name").orEmpty()}\",")
                    append("\"email\":\"${configValue("user.email").orEmpty()}\"")
                    append("}")
                }
                call.respondText(body, ContentType.Application.Json)
            }

            get("/proxy/finnhub/{path...}") {
                val key = configValue("finnhub.apiKey")
                if (key == null) {
                    call.respondText("Finnhub API key not configured in server/local.properties", status = HttpStatusCode.BadGateway)
                    return@get
                }
                val path = call.parameters.getAll("path")?.joinToString("/").orEmpty()
                val url = URLBuilder("https://finnhub.io/api/v1/$path").apply {
                    call.request.queryParameters.forEach { name, values -> values.forEach { parameters.append(name, it) } }
                    parameters.append("token", key)
                }.buildString()
                relay(call, url)
            }

            // Dedicated (not the generic {path...} passthrough above) because it
            // computes the from/to date window server-side, using java.time --
            // the wasmJs client has no date-arithmetic library, so this keeps
            // one out of it entirely rather than adding one for a single call site.
            get("/proxy/finnhub/news") {
                val key = configValue("finnhub.apiKey")
                if (key == null) {
                    call.respondText("Finnhub API key not configured in server/local.properties", status = HttpStatusCode.BadGateway)
                    return@get
                }
                val symbol = call.request.queryParameters["symbol"]
                if (symbol == null) {
                    call.respondText("symbol query parameter required", status = HttpStatusCode.BadRequest)
                    return@get
                }
                val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                val url = URLBuilder("https://finnhub.io/api/v1/company-news").apply {
                    parameters.append("symbol", symbol)
                    parameters.append("from", today.minusDays(7).toString())
                    parameters.append("to", today.toString())
                    parameters.append("token", key)
                }.buildString()
                relay(call, url)
            }

            get("/proxy/alphavantage/query") {
                val key = configValue("alphaVantage.apiKey")
                if (key == null) {
                    call.respondText("Alpha Vantage API key not configured in server/local.properties", status = HttpStatusCode.BadGateway)
                    return@get
                }
                val url = URLBuilder("https://www.alphavantage.co/query").apply {
                    call.request.queryParameters.forEach { name, values -> values.forEach { parameters.append(name, it) } }
                    parameters.append("apikey", key)
                }.buildString()
                relay(call, url)
            }

            get("/proxy/edgar/tickers") {
                relay(call, "https://www.sec.gov/files/company_tickers.json", userAgentHeader())
            }

            get("/proxy/edgar/submissions/{cik}") {
                val cik = call.parameters["cik"].orEmpty()
                relay(call, "https://data.sec.gov/submissions/CIK$cik.json", userAgentHeader())
            }

            post("/proxy/claude/messages") {
                val key = configValue("claude.apiKey")
                if (key == null) {
                    call.respondText("Claude API key not configured in server/local.properties", status = HttpStatusCode.BadGateway)
                    return@post
                }
                val requestBody = call.receiveText()
                val response = httpClient.request("https://api.anthropic.com/v1/messages") {
                    method = HttpMethod.Post
                    header("x-api-key", key)
                    header("anthropic-version", "2023-06-01")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(requestBody)
                }
                call.respondText(response.bodyAsText(), ContentType.Application.Json, HttpStatusCode.fromValue(response.status.value))
            }
        }
    }.start(wait = true)
}

private suspend fun relay(call: ApplicationCall, url: String, userAgent: String? = null) {
    val response: HttpResponse = httpClient.request(url) {
        method = HttpMethod.Get
        userAgent?.let { header(HttpHeaders.UserAgent, it) }
    }
    call.respondText(response.bodyAsText(), ContentType.Application.Json, HttpStatusCode.fromValue(response.status.value))
}
