package com.example.ceylonqueuebuspulse

import com.example.ceylonqueuebuspulse.data.network.model.TrafficReportDto
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrafficMoshiTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val type = Types.newParameterizedType(List::class.java, TrafficReportDto::class.java)
    private val adapter = moshi.adapter<List<TrafficReportDto>>(type)

    private fun fixture(path: String): String {
        return javaClass.classLoader
            ?.getResource(path)
            ?.readText()
            ?: error("Missing test fixture $path")
    }

    @Test
    fun canParseTrafficReportList() {
        val parsed = adapter.fromJson(fixture("fixtures/traffic_reports_list.json"))
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(1, parsed.size)
        assertEquals("138", parsed[0].route)
        assertEquals(3, parsed[0].severity)
    }

    @Test
    fun canParseEmptyList() {
        val parsed = adapter.fromJson(fixture("fixtures/traffic_reports_list_empty.json"))
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(0, parsed.size)
    }

    @Test
    fun ignoresUnknownKeys() {
        val parsed = adapter.fromJson(fixture("fixtures/traffic_reports_list_unknown_keys.json"))
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(1, parsed.size)
        assertEquals("138", parsed[0].route)
    }

    @Test(expected = JsonDataException::class)
    fun missingRequiredFieldsFailsParsing() {
        adapter.fromJson(fixture("fixtures/traffic_reports_list_missing_fields.json"))
    }
}
