package com.example.waywatch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.waywatch.data.network.model.AggregateDto
import com.example.waywatch.ui.theme.ErrorRed
import com.example.waywatch.ui.theme.PrimaryGreen
import com.example.waywatch.ui.theme.SecondaryAmber

@Composable
fun getSeverityColor(severity: Double): Color {
    return when {
        severity >= 4.0 -> ErrorRed
        severity >= 2.0 -> SecondaryAmber
        else -> PrimaryGreen
    }
}

@Composable
fun getSeverityText(severity: Double): String {
    return when {
        severity >= 4.0 -> "Heavy"
        severity >= 2.0 -> "Slow"
        else -> "Clear"
    }
}

@Composable
fun RouteCard(
    route: AggregateDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetColor = getSeverityColor(route.severityAvg)
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp) // Fitts' Law
            .clickable { onClick() }
            .graphicsLayer { alpha = 0.9f }, // Glassmorphism
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Route ${route.routeId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Miller's Law: max 3 data points
                Text(
                    text = "Last updated: ${route.lastAggregatedAtMs} ms ago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "Reports: ${route.sampleCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Animated Severity Pill
            Surface(
                shape = CircleShape,
                color = animatedColor.copy(alpha = 0.2f),
                contentColor = animatedColor
            ) {
                Text(
                    text = getSeverityText(route.severityAvg),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SeverityChipRow(
    routes: List<AggregateDto>,
    onChipClick: (AggregateDto) -> Unit
) {
    // Hick's Law: limit to max 5 items
    val displayedRoutes = routes.take(5)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(displayedRoutes) { route ->
            val color = getSeverityColor(route.severityAvg)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .clickable { onChipClick(route) }
                    .heightIn(min = 44.dp), // Fitts' Law
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, CircleShape)
                    )
                    Text(
                        text = route.routeId,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "(${route.sampleCount})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
