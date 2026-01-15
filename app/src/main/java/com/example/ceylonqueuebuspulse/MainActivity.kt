// Edited: 2026-01-06
// Purpose: Main activity hosts Compose UI, binds to TrafficViewModel, schedules background sync, and streams location updates.

// Kotlin
package com.example.ceylonqueuebuspulse

// Location handling - 2025-12-28
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.ceylonqueuebuspulse.util.ConnectivityMonitor
import com.example.ceylonqueuebuspulse.util.NetworkState
import com.example.ceylonqueuebuspulse.util.ReleasedCallbackRegistry
import com.example.ceylonqueuebuspulse.work.SyncScheduler
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// Compose UI
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
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

// Entry point Activity. Hosts the Compose UI and connects it to the ViewModel.
class MainActivity : ComponentActivity() {

    // ViewModel scoped to the Activity lifecycle.
    private val viewModel: TrafficViewModel by viewModel()
    private val authViewModel: AuthViewModel by viewModel()

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

        // Compose UI content
        setContent {
            CeylonQueueBusPulseTheme {
                val trafficState by viewModel.uiState.collectAsState()
                val authState by authViewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // Observe network connectivity
                val networkState by connectivityMonitor.observeConnectivity()
                    .collectAsState(initial = NetworkState.UNKNOWN)

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
                            message = "You're offline. Data will sync when connection is restored.",
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

                // Convert epoch millis -> human-readable local date/time.
                // Uses java.text.DateFormat for API 24+ compatibility.
                val formattedLastUpdate = remember(trafficState.lastUpdatedMs) {
                    trafficState.lastUpdatedMs?.let { ms ->
                        DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.SHORT
                        ).format(Date(ms))
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Bus Traffic Updates") },
                            actions = {
                                TextButton(onClick = { authViewModel.logout() }) {
                                    Text("Logout", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }

                                // Offline indicator
                                if (networkState == NetworkState.DISCONNECTED) {
                                    Badge(
                                        containerColor = Color.Red,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text("Offline", color = Color.White)
                                    }
                                }

                                IconButton(onClick = {
                                    // Trigger immediate aggregation planner run; replaces any pending refresh.
                                    SyncScheduler.refreshNow(applicationContext)
                                    // Also update UI by asking ViewModel to refresh local view state.
                                    viewModel.refresh()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Refresh"
                                    )
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))

                        // Show sync/refresh status and last updated timestamp when available
                        if (trafficState.isSyncing) {
                            Text("Syncing…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            formattedLastUpdate?.let { pretty ->
                                Text("Last updated: $pretty", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Route Selector
                        Text("Select Route:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val routes = listOf("138", "174", "177", "120")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            routes.forEach { routeId ->
                                FilterChip(
                                    selected = trafficState.selectedRouteId == routeId,
                                    onClick = { viewModel.selectRoute(routeId) },
                                    label = { Text(routeId) }
                                )
                            }
                        }

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
                                        text = "Current Traffic: Route ${trafficState.selectedRouteId}",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Severity: %.1f / 5.0".format(latest.severityAvg),
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        text = "Based on ${latest.sampleCount} reports",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    latest.severityP50?.let { p50 ->
                                        Text(
                                            text = "Median (P50): %.1f".format(p50),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    latest.severityP90?.let { p90 ->
                                        Text(
                                            text = "P90: %.1f".format(p90),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    val minutesAgo = (System.currentTimeMillis() - latest.lastAggregatedAtMs) / 60000
                                    Text(
                                        text = "Updated $minutesAgo min ago",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No aggregated data yet for Route ${trafficState.selectedRouteId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Historical Trends
                        Text("Recent History:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        if (trafficState.aggregatedData.size > 1) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(trafficState.aggregatedData.size) { index ->
                                    val agg = trafficState.aggregatedData[index]
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            val timeStr = DateFormat.getTimeInstance(
                                                DateFormat.SHORT
                                            ).format(Date(agg.windowStartMs))
                                            Text(
                                                text = "Window: $timeStr",
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
                                            Text(
                                                text = "${agg.sampleCount} samples",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Sample action to submit a fixed location (Colombo)
                        Button(onClick = { viewModel.submitUserLocation(6.9271, 79.8612, trafficState.selectedRouteId) }) {
                            Text("Submit Sample Traffic Report")
                        }
                    }
                }
            }
        }
    }

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
