@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

// Edited: 2026-01-06
// Purpose: Main activity hosts Compose UI, binds to TrafficViewModel, schedules background sync, and streams location updates.

// Kotlin
package com.example.ceylonqueuebuspulse

// Location handling - 2025-12-28
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.example.ceylonqueuebuspulse.data.auth.PendingDeepLinkStore
import com.example.ceylonqueuebuspulse.notifications.NotificationChannels
import com.example.ceylonqueuebuspulse.settings.SettingsViewModel
import com.example.ceylonqueuebuspulse.settings.ThemeMode
import com.example.ceylonqueuebuspulse.ui.SettingsActivity
import com.example.ceylonqueuebuspulse.ui.TrafficDetailActivity
import com.example.ceylonqueuebuspulse.traffic.MapComposeActivity
import com.example.ceylonqueuebuspulse.util.ConnectivityMonitor
import com.example.ceylonqueuebuspulse.util.NetworkState
import com.example.ceylonqueuebuspulse.util.ReleasedCallbackRegistry
import com.example.ceylonqueuebuspulse.work.SyncScheduler
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// Compose UI
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import java.text.DateFormat
import java.util.Date

// Theme + ViewModel
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme
import com.example.ceylonqueuebuspulse.ui.TrafficViewModel
import com.example.ceylonqueuebuspulse.ui.auth.AuthScreen
import com.example.ceylonqueuebuspulse.ui.auth.AuthViewModel

// Google Play Services Location APIs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    // Fused Location state
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var locationCallback: LocationCallback? = null

    // Runtime permission launcher for fine/coarse location (multiple permissions)
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fineGranted || coarseGranted) {
                startLocationUpdates()
            } else {
                // Permission denied: optionally show a snackbar/toast
                // Avoid calling viewModel.clearError() here to prevent unresolved reference
            }
        }

    // Runtime permission launcher for notifications
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

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

        // Initialize fused location client and request configuration
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(10000L)
            .build()

        // Check/ask permissions and start streaming if allowed
        ensureLocationPermissionAndStart()

        // Notifications
        NotificationChannels.ensureCreated(applicationContext)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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

            CeylonQueueBusPulseTheme(darkTheme = darkTheme) {
                val trafficState by viewModel.uiState.collectAsState()
                val authState by authViewModel.uiState.collectAsState()

                // When auth flips to logged-in, immediately open the map screen.
                LaunchedEffect(authState.isLoggedIn) {
                    if (authState.isLoggedIn) {
                        val pending = pendingDeepLinkStore.consume()
                        val i = Intent(this@MainActivity, MapComposeActivity::class.java)

                        // If we had a pending deep link, translate it to map extras.
                        // Supported incoming links:
                        //   ceylonqueue://route?routeId=138
                        //   ceylonqueue://report?routeId=138&windowStartMs=...
                        if (pending != null && pending.scheme == "ceylonqueue") {
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
                    return@CeylonQueueBusPulseTheme
                }

                val formattedLastUpdate = remember(trafficState.lastUpdatedMs) {
                    trafficState.lastUpdatedMs?.let { ms ->
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
                    }
                }

                val isRefreshing = trafficState.isSyncing
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = isRefreshing,
                    onRefresh = {
                        SyncScheduler.refreshNow(applicationContext)
                        viewModel.refresh()
                    }
                )

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

                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState)
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

                        PullRefreshIndicator(
                            refreshing = isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
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
        if (uri.scheme != "ceylonqueue") return

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

    private enum class SortMode { NEWEST, OLDEST, HIGHEST_SEVERITY }

    // Ensure permissions are granted; if not, request them
    private fun ensureLocationPermissionAndStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Begin receiving fused location updates and forward to ViewModel
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Avoid duplicate registration
        if (locationCallback != null) return

        // Extra guard: ensure we still have permission at call time to avoid SecurityException
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        // Handle incoming location batches
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                // Use selected route from ViewModel instead of hardcoded value
                val currentRouteId = viewModel.uiState.value.selectedRouteId
                viewModel.submitUserLocation(loc.latitude, loc.longitude, currentRouteId)
            }
        }

        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            mainLooper
        )
    }

    // Stop receiving updates to conserve battery/resources
    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    // Resume: re-verify permission and restart updates
    override fun onResume() {
        super.onResume()
        ensureLocationPermissionAndStart()
    }

    // Pause: stop updates while in background
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }
}

// Preview composable for design-time rendering in Android Studio
@Preview(showBackground = true)
@Composable
fun AppPreview() {
    CeylonQueueBusPulseTheme {
        Text("Bus Traffic Updates")
    }
}
