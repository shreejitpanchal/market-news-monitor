package com.marketnewsmonitor.app.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupDataTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun `round trips tickers and api key through json`() {
        val original = BackupData(
            exportedAt = 1_700_000_000_000L,
            tickers = listOf(
                BackupTicker(symbol = "AAPL", companyName = "Apple Inc.", addedAt = 1L),
                BackupTicker(symbol = "TSLA", companyName = null, addedAt = 2L),
            ),
            apiKey = "sk-ant-test-key",
        )

        val encoded = json.encodeToString(BackupData.serializer(), original)
        val decoded = json.decodeFromString(BackupData.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `decoding tolerates a missing api key`() {
        val encoded = json.encodeToString(
            BackupData.serializer(),
            BackupData(exportedAt = 1L, tickers = emptyList(), apiKey = null),
        )

        val decoded = json.decodeFromString(BackupData.serializer(), encoded)

        assertNull(decoded.apiKey)
        assertEquals(BackupData.CURRENT_VERSION, decoded.version)
    }

    @Test
    fun `ignores unknown fields for forward compatibility`() {
        val encodedWithExtraField = """
            {"version":1,"exportedAt":1,"tickers":[],"apiKey":null,"futureField":"ignored"}
        """.trimIndent()

        val decoded = json.decodeFromString(BackupData.serializer(), encodedWithExtraField)

        assertEquals(1L, decoded.exportedAt)
    }
}
