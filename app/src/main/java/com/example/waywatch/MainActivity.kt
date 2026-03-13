@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

// Edited: 2026-01-06
// Purpose: Main activity hosts Compose UI, binds to TrafficViewModel, schedules background sync, and streams location updates.

// Kotlin
package com.example.waywatch

// Location handling - 2025-12-28
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.waywatch.data.auth.PendingDeepLinkStore
import com.example.waywatch.notifications.NotificationChannels
import com.example.waywatch.settings.SettingsViewModel
import com.example.waywatch.settings.ThemeMode
import com.example.waywatch.ui.SettingsActivity
import com.example.waywatch.ui.TrafficDetailActivity
import com.example.waywatch.traffic.MapComposeActivity
import com.example.waywatch.util.ConnectivityMonitor
import com.example.waywatch.util.NetworkState
import com.example.waywatch.util.PermissionHandler
import com.example.waywatch.util.ReleasedCallbackRegistry
import com.example.waywatch.work.SyncScheduler
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// Compose UI
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import java.text.DateFormat
import java.util.Date

// Theme + ViewModel
import com.example.waywatch.ui.theme.waywatchTheme
import com.example.waywatch.ui.TrafficViewModel
import com.example.waywatch.ui.auth.AuthScreen
import com.example.waywatch.ui.auth.AuthViewModel

import com.example.waywatch.ui.auth.AuthViewModel

import androidx.compose.ui.graphics.Color

// Entry point Activity. Hosts the Compose UI and connects it to the ViewModel.
class MainActivity : ComponentActivity() {

    // ViewModel scoped to the Activity lifecycle.
    private val viewModel: TrafficViewModel by viewModel()
    private val authViewModel: AuthViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()

    // Deep-link stash (used when the user must log in first)
    private val pendingDeepLinkStore: PendingDeepLinkStore by inject()

    // Connectivity monitor for network state
    private val connectivityMonitor: ConnectivityMonitor by inject()

    private lateinit var permissionHandler: PermissionHandler

    // Lifecycle: initialize location and compose UI
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw content edge-to-edge under system bars
        enableEdgeToEdge()

        // FYI: Logcat "callback not found for RELEASED message" is benign Play Services noise.
        ReleasedCallbackRegistry.noteIfSeen()

        // Removed Firebase Auth + Firestore test data initialization (MongoDB backend)

        // Schedule periodic background sync (Phase 3)
        SyncScheduler.schedule(applicationContext)

        // Notifications
        NotificationChannels.ensureCreated(applicationContext)

        // Handle runtime permissions via isolated handler
        permissionHandler = PermissionHandler(this)
        permissionHandler.setOnPermissionsGrantedListener {
            // Permissions are granted. Location updates are automatically handled by TrafficViewModel's collection of LocationTracker.
        }

        // Handle deep link if app launched from a URI.
        // If user isn't logged in yet, we stash it and apply after login.
        handleDeepLink(intent?.data)

        // Compose UI content
        setContent {
            val settings by settingsViewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            waywatchTheme(darkTheme = darkTheme) {
                val trafficState by viewModel.uiState.collectAsState()
                val authState by authViewModel.uiState.collectAsState()

                // When auth flips to logged-in, immediately open the map screen.
                LaunchedEffect(authState.isLoggedIn) {
                    if (authState.isLoggedIn) {
                        val pending = pendingDeepLinkStore.consume()
                        val i = Intent(this@MainActivity, MapComposeActivity::class.java)

                        // If we had a pending deep link, translate it to map extras.
                        // Supported incoming links:
                        //   waywatch://route?routeId=138
                        //   waywatch://report?routeId=138&windowStartMs=...
                        if (pending != null && pending.scheme == "waywatch") {
                            when (pending.host) {
                                "route" -> {
                                    val rid = pending.getQueryParameter("routeId")?.trim().orEmpty()
                                    if (rid.isNotEmpty()) i.putExtra(MapComposeActivity.EXTRA_ROUTE_ID, rid)
                                }
                                "report" -> {
                                    val rid = pending.getQueryParameter("routeId")?.trim().orEmpty()
                                    if (rid.isNotEmpty()) i.putExtra(MapComposeActivity.EXTRA_ROUTE_ID, rid)
                                }
                            }
                        }

                        startActivity(i)
                        finish()
                    }
                }

                val snackbarHostState = remember { SnackbarHostState() }

                // --- UI controls state ---
                var sortMode by remember { mutableStateOf(SortMode.NEWEST) }
                var minSeverity by remember { mutableStateOf(0f) }

                // Observe network connectivity
                val networkState by connectivityMonitor.observeConnectivity()
                    .collectAsState(initial = NetworkState.UNKNOWN)

                val offlineMessage = stringResource(id = R.string.msg_offline)

                // Show error as snackbar when errorMessage changes
                LaunchedEffect(trafficState.errorMessage) {
                    trafficState.errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                    }
                }

                // Show offline snackbar when disconnected
                LaunchedEffect(networkState) {
                    if (networkState == NetworkState.DISCONNECTED) {
                        snackbarHostState.showSnackbar(
                            message = offlineMessage,
                            duration = SnackbarDuration.Short
                        )
                    }
                }

                if (!authState.isLoggedIn) {
                    AuthScreen(
                        state = authState,
                        onEmailChange = authViewModel::setEmail,
                        onPasswordChange = authViewModel::setPassword,
                        onToggleMode = authViewModel::toggleMode,
                        onSubmit = authViewModel::submit,
                        onMessageShown = authViewModel::consumeMessages
                    )
                    return@waywatchTheme
                }

                val formattedLastUpdate = remember(trafficState.lastUpdatedMs) {
                    trafficState.lastUpdatedMs?.let { ms ->
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
                    }
                }

                val isRefreshing = trafficState.isSyncing

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(id = R.string.title_bus_traffic_updates)) },
                            actions = {
                                TextButton(onClick = { authViewModel.logout() }) {
                                    Text(stringResource(id = R.string.action_logout), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }

                                IconButton(onClick = {
                                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Settings"
                                    )
                                }

                                // Offline indicator
                                if (networkState == NetworkState.DISCONNECTED) {
                                    Badge(
                                        containerColor = Color.Red,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(stringResource(id = R.string.status_offline), color = Color.White)
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        SyncScheduler.refreshNow(applicationContext)
                                        viewModel.refresh()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = stringResource(id = R.string.action_refresh)
                                    )
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    // Double-tap to exit logic
                    var lastBackPressTime by remember { mutableLongStateOf(0L) }
                    val contextLocal = androidx.compose.ui.platform.LocalContext.current
                    val msgBackExit = stringResource(id = R.string.msg_back_press_exit)

                    androidx.activity.compose.BackHandler(enabled = true) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < 2000) {
                            (contextLocal as? android.app.Activity)?.finish()
                        } else {
                            lastBackPressTime = currentTime
                            android.widget.Toast.makeText(contextLocal, msgBackExit, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            SyncScheduler.refreshNow(applicationContext)
                            viewModel.refresh()
                        },
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Spacer(Modifier.height(4.dp))

                            // Loading state (submit/seed operations)
                            if (trafficState.isLoading) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(stringResource(id = R.string.status_working), style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            // Error state (retry)
                            trafficState.errorMessage?.let { msg ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = msg,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { viewModel.refresh() }) {
                                                Text(stringResource(id = R.string.action_retry))
                                            }
                                            TextButton(onClick = { viewModel.clearError() }) {
                                                Text(stringResource(id = R.string.action_dismiss))
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            // Show sync/refresh status and last updated timestamp when available
                            if (trafficState.isSyncing) {
                                Text(stringResource(id = R.string.status_syncing), style = MaterialTheme.typography.bodyMedium)
                            } else {
                                formattedLastUpdate?.let { pretty ->
                                    Text(stringResource(id = R.string.label_last_updated, pretty), style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Route Selector
                            Text(stringResource(id = R.string.label_select_route), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            val routes = listOf("138", "174", "177", "120")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                routes.forEach { routeId ->
                                    FilterChip(
                                        selected = trafficState.selectedRouteId == routeId,
                                        onClick = {
                                            viewModel.selectRoute(routeId)
                                            viewModel.refresh()
                                        },
                                        label = { Text(routeId) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Sorting + filtering controls
                            Text(stringResource(id = R.string.label_filters), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = sortMode == SortMode.NEWEST,
                                    onClick = { sortMode = SortMode.NEWEST },
                                    label = { Text(stringResource(id = R.string.sort_newest)) }
                                )
                                FilterChip(
                                    selected = sortMode == SortMode.OLDEST,
                                    onClick = { sortMode = SortMode.OLDEST },
                                    label = { Text(stringResource(id = R.string.sort_oldest)) }
                                )
                                FilterChip(
                                    selected = sortMode == SortMode.HIGHEST_SEVERITY,
                                    onClick = { sortMode = SortMode.HIGHEST_SEVERITY },
                                    label = { Text(stringResource(id = R.string.sort_highest)) }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(id = R.string.label_min_severity, minSeverity.toInt()),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = minSeverity,
                                onValueChange = { minSeverity = it },
                                valueRange = 0f..5f,
                                steps = 4
                            )

                            Spacer(Modifier.height(16.dp))

                            // Current Traffic Status Card
                            if (trafficState.aggregatedData.isNotEmpty()) {
                                val latest = trafficState.aggregatedData.first()
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            latest.severityAvg >= 4.0 -> Color.Red.copy(alpha = 0.2f)
                                            latest.severityAvg >= 2.5 -> Color.Yellow.copy(alpha = 0.3f)
                                            else -> Color.Green.copy(alpha = 0.2f)
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = stringResource(id = R.string.label_current_traffic, trafficState.selectedRouteId),
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.label_severity, latest.severityAvg),
                                            style = MaterialTheme.typography.headlineMedium
                                        )
                                        Text(
                                            text = stringResource(id = R.string.label_based_on_reports, latest.sampleCount),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        latest.severityP50?.let { p50 ->
                                            Text(text = "Median (P50): ${"%.1f".format(p50)}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        latest.severityP90?.let { p90 ->
                                            Text(text = "P90: ${"%.1f".format(p90)}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        val minutesAgo = (System.currentTimeMillis() - latest.lastAggregatedAtMs) / 60000
                                        Text(
                                            text = stringResource(id = R.string.label_updated_minutes_ago, minutesAgo.toInt()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = stringResource(id = R.string.empty_no_aggregates, trafficState.selectedRouteId),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.hint_tap_refresh),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Historical Trends
                            Text(stringResource(id = R.string.label_recent_history), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))

                            val history = remember(trafficState.aggregatedData, sortMode, minSeverity) {
                                val filtered = trafficState.aggregatedData
                                    .filter { it.severityAvg >= minSeverity.toDouble() }
                                when (sortMode) {
                                    SortMode.NEWEST -> filtered.sortedByDescending { it.windowStartMs }
                                    SortMode.OLDEST -> filtered.sortedBy { it.windowStartMs }
                                    SortMode.HIGHEST_SEVERITY -> filtered.sortedByDescending { it.severityAvg }
                                }
                            }

                            if (history.size > 1) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(history.size) { index ->
                                        val agg = history[index]
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    startActivity(
                                                        Intent(this@MainActivity, TrafficDetailActivity::class.java).apply {
                                                            putExtra(TrafficDetailActivity.EXTRA_ROUTE_ID, agg.routeId)
                                                            putExtra(TrafficDetailActivity.EXTRA_WINDOW_START_MS, agg.windowStartMs)
                                                            putExtra(TrafficDetailActivity.EXTRA_SEVERITY_AVG, agg.severityAvg)
                                                            putExtra(TrafficDetailActivity.EXTRA_SAMPLE_COUNT, agg.sampleCount)
                                                        }
                                                    )
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(agg.windowStartMs))
                                                Text(
                                                    text = stringResource(id = R.string.label_window, timeStr),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "Avg: %.1f | P50: %.1f | P90: %.1f".format(
                                                        agg.severityAvg,
                                                        agg.severityP50 ?: 0.0,
                                                        agg.severityP90 ?: 0.0
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.label_samples, agg.sampleCount),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Gray
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Filled.Info,
                                                        contentDescription = stringResource(id = R.string.cd_open_detail),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(id = R.string.empty_no_history),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Sample action to submit a fixed location (Colombo)
                            Button(
                                onClick = {
                                    viewModel.submitUserLocation(6.9271, 79.8612, trafficState.selectedRouteId)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(id = R.string.action_submit_sample))
                            }
                        }

                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepLink(intent?.data)
    }

    private fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme != "waywatch") return

        // If user isn't logged in, store it and let the login screen show.
        // Once logged in, we'll launch MapComposeActivity with this link.
        if (!authViewModel.uiState.value.isLoggedIn) {
            pendingDeepLinkStore.set(uri)
            return
        }

        // Logged-in flow: apply immediately.
        when (uri.host) {
            "route" -> {
                val routeId = uri.getQueryParameter("routeId")?.trim().orEmpty()
                if (routeId.isNotEmpty()) {
                    viewModel.selectRoute(routeId)
                    viewModel.refresh()
                }
            }

            "report" -> {
                val routeId = uri.getQueryParameter("routeId")?.trim().orEmpty()
                val windowStartMs = uri.getQueryParameter("windowStartMs")?.toLongOrNull() ?: -1L
                val severityAvg = uri.getQueryParameter("severityAvg")?.toDoubleOrNull() ?: Double.NaN
                val sampleCount = uri.getQueryParameter("sampleCount")?.toIntOrNull() ?: -1

                if (routeId.isNotEmpty() && windowStartMs > 0) {
                    startActivity(
                        Intent(this, TrafficDetailActivity::class.java).apply {
                            putExtra(TrafficDetailActivity.EXTRA_ROUTE_ID, routeId)
                            putExtra(TrafficDetailActivity.EXTRA_WINDOW_START_MS, windowStartMs)
                            putExtra(TrafficDetailActivity.EXTRA_SEVERITY_AVG, severityAvg)
                            putExtra(TrafficDetailActivity.EXTRA_SAMPLE_COUNT, sampleCount)
                        }
                    )
                }
            }
        }
    }

}

// Preview composable for design-time rendering in Android Studio
@Preview(showBackground = true)
@Composable
fun AppPreview() {
    waywatchTheme {
        Text("Bus Traffic Updates")
    }
}
