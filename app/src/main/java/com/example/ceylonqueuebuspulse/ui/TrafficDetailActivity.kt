package com.example.ceylonqueuebuspulse.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme
import java.text.DateFormat
import java.util.Date

class TrafficDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: ""
        val windowStartMs = intent.getLongExtra(EXTRA_WINDOW_START_MS, -1L)
        val severityAvg = intent.getDoubleExtra(EXTRA_SEVERITY_AVG, Double.NaN)
        val sampleCount = intent.getIntExtra(EXTRA_SAMPLE_COUNT, -1)

        setContent {
            CeylonQueueBusPulseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrafficDetailScreen(
                        routeId = routeId,
                        windowStartMs = windowStartMs,
                        severityAvg = severityAvg,
                        sampleCount = sampleCount
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_WINDOW_START_MS = "extra_window_start_ms"
        const val EXTRA_SEVERITY_AVG = "extra_severity_avg"
        const val EXTRA_SAMPLE_COUNT = "extra_sample_count"
    }
}

@Composable
private fun TrafficDetailScreen(
    routeId: String,
    windowStartMs: Long,
    severityAvg: Double,
    sampleCount: Int
) {
    val timeStr = if (windowStartMs > 0) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(windowStartMs))
    } else {
        "Unknown"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "Traffic detail", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Route: $routeId", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Window: $timeStr", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (!severityAvg.isNaN()) {
            Text(text = "Average severity: ${"%.1f".format(severityAvg)} / 5.0", style = MaterialTheme.typography.titleLarge)
        }
        if (sampleCount >= 0) {
            Text(text = "Samples: $sampleCount", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Next step: show related map point/provider data here.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
