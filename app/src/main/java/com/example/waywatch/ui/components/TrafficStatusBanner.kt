package com.example.waywatch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.waywatch.data.network.model.AggregateDto

@Composable
fun TrafficStatusBanner(
    routes: List<AggregateDto>,
    modifier: Modifier = Modifier
) {
    if (routes.isEmpty()) return

    val averageSeverity = routes.map { it.severityAvg }.average()
    
    val targetColor = getSeverityColor(averageSeverity)
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 800)
    )
    
    val statusText = "Area Status: ${getSeverityText(averageSeverity)}"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(animatedColor)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusText,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
