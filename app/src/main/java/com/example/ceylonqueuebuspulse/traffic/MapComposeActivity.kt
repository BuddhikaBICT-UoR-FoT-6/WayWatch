package com.example.ceylonqueuebuspulse.traffic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ceylonqueuebuspulse.BuildConfig
import com.example.ceylonqueuebuspulse.MainActivity
import com.example.ceylonqueuebuspulse.R
import com.example.ceylonqueuebuspulse.data.auth.PendingDeepLinkStore
import com.example.ceylonqueuebuspulse.ui.auth.AuthViewModel
import com.example.ceylonqueuebuspulse.util.HeadingProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.shape.RoundedCornerShape

class MapComposeActivity : ComponentActivity() {
    private val vm: MapComposeViewModel by viewModel()
    private val locVm: LocationTrafficViewModel by viewModel()

    // Auth for logout
    private val authVm: AuthViewModel by viewModel()
    private val pendingDeepLinkStore: PendingDeepLinkStore by inject()

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* handled in compose via state */ }

    private var headingJob: Job? = null
    private var latestHeadingDeg: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        // Start heading updates (best-effort; may be unavailable on some devices).
        try {
            val headingProvider = HeadingProvider(applicationContext)
            headingJob = headingProvider.headings()
                .distinctUntilChanged()
                .onEach { latestHeadingDeg = it }
                .launchIn(lifecycleScope)
        } catch (_: Throwable) {
            // ignore
        }

        val initialRouteId = intent.getStringExtra(EXTRA_ROUTE_ID)?.trim().takeUnless { it.isNullOrEmpty() } ?: "138"

        setContent {
            MapComposeScreen(
                vm = vm,
                locVm = locVm,
                initialRouteId = initialRouteId,
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onStartLocation = { webView -> startLocationUpdates(webView) },
                onStopLocation = { stopLocationUpdates() },
                onMapClick = { lat, lon ->
                    locVm.selectLocation(lat, lon)
                },
                onPlaceSelected = { place ->
                    vm.selectPlace(place)
                },
                onLogout = {
                    // Clear any pending deep links to avoid stale navigation
                    pendingDeepLinkStore.clear()

                    // Clear the session and navigate to the login/register screen
                    authVm.logout()
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            )
        }
    }

    companion object {
        const val EXTRA_ROUTE_ID = "extra_route_id"
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(webView: WebView) {
        if (locationCallback != null) return

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(6_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val heading = latestHeadingDeg
                webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude}, ${heading})", null)
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback as LocationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        headingJob?.cancel()
        headingJob = null
    }
}

@Composable
fun MapComposeScreen(
    vm: MapComposeViewModel,
    locVm: LocationTrafficViewModel,
    initialRouteId: String,
    onRequestLocationPermission: () -> Unit,
    onStartLocation: (WebView) -> Unit,
    onStopLocation: () -> Unit,
    onMapClick: (Double, Double) -> Unit,
    onPlaceSelected: (PlaceResult) -> Unit,
    onLogout: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val places by vm.places.collectAsState(initial = emptyList())
    val status by vm.status.collectAsState(initial = null)
    val routePoints by locVm.routePoints.collectAsState(initial = emptyList())

    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val fineGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val coarseGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun refreshPermissionState() {
        fineGranted.value = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        coarseGranted.value = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val locationEnabled = remember {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        mutableStateOf(lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true || lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)
    }

    var selectedRouteId by remember { mutableStateOf(initialRouteId) }

    // Capture user's current location from the WebView updates so the "Center on me" button can fetch traffic.
    var lastUserLatLon by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Current selected point (from map tap or search select)
    var selectedPoint by remember { mutableStateOf<PlaceResult?>(null) }

    // User-report UI
    var showReportDialog by remember { mutableStateOf(false) }
    var reportSeverity by remember { mutableStateOf(3f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- MAP (dominant) ---
        AndroidView(
            factory = { ctx ->
                val wv = WebView(ctx)
                webViewRef = wv
                setupWebViewForLeaflet(
                    wv,
                    onMapClick = { lat, lon ->
                        val p = PlaceResult(label = "Dropped pin", lat = lat, lon = lon)
                        selectedPoint = p
                        onMapClick(lat, lon)
                        showReportDialog = true
                    },
                    onUserLocation = { lat, lon -> lastUserLatLon = lat to lon }
                )

                refreshPermissionState()
                if (fineGranted.value || coarseGranted.value) {
                    onStartLocation(wv)
                }
                wv
            },
            update = { wv -> webViewRef = wv },
            modifier = Modifier.fillMaxSize()
        )

        // --- TOP overlay header ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(id = R.string.app_name), color = Color.White)
                OutlinedButton(onClick = onLogout) { Text(stringResource(id = R.string.action_logout)) }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search place") }
                )
                Button(onClick = { vm.search(query, BuildConfig.TOMTOM_API_KEY) }) { Text("Search") }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("138", "174", "177", "120").forEach { routeId ->
                    OutlinedButton(
                        onClick = { selectedRouteId = routeId },
                        enabled = selectedRouteId != routeId
                    ) { Text(routeId) }
                }

                Spacer(Modifier.weight(1f))

                OutlinedButton(onClick = {
                    refreshPermissionState()
                    if (!(fineGranted.value || coarseGranted.value)) {
                        onRequestLocationPermission()
                        return@OutlinedButton
                    }

                    webViewRef?.let { onStartLocation(it) }

                    val coords = lastUserLatLon
                    if (coords != null) {
                        webViewRef?.evaluateJavascript("centerOnUser()", null)
                        val p = PlaceResult(label = "My location", lat = coords.first, lon = coords.second)
                        selectedPoint = p
                        locVm.selectLocation(coords.first, coords.second)
                        showReportDialog = true
                    } else {
                        webViewRef?.evaluateJavascript("centerOnUser()", null)
                    }
                }) {
                    Text(stringResource(id = R.string.action_center_on_me))
                }
            }

            status?.let { Text(it, color = Color.White) }
            if (!(fineGranted.value || coarseGranted.value)) {
                Text(text = stringResource(id = R.string.location_permission_required), color = Color.White)
            } else if (!locationEnabled.value) {
                Text(text = stringResource(id = R.string.location_services_off), color = Color.White)
            }
        }

        // --- Search results panel (only when we have results) ---
        if (places.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(8.dp)
                ) {
                    items(places) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = p.label)
                                Spacer(Modifier.height(2.dp))
                                Text(text = "${p.lat}, ${p.lon}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                selectedPoint = p
                                webViewRef?.evaluateJavascript("setLocation(${p.lat}, ${p.lon}, ${escapeJsString(p.label)})", null)
                                onPlaceSelected(p)
                                showReportDialog = true
                            }) { Text("Select") }
                        }
                    }
                }
            }
        }

        // --- Report dialog (Low/Medium/High) ---
        if (showReportDialog) {
            val current = selectedPoint
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text("Report traffic") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current != null) {
                            Text("Location: ${current.label}")
                            Text("${current.lat}, ${current.lon}")
                        }

                        Text("Severity: ${reportSeverity.toInt()} (1=Low, 3=Medium, 5=High)")
                        Slider(
                            value = reportSeverity,
                            onValueChange = { reportSeverity = it },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val point = current
                            if (point != null) {
                                locVm.submitSample(
                                    routeId = selectedRouteId,
                                    severity = reportSeverity.toInt(),
                                    lat = point.lat,
                                    lon = point.lon
                                ) { _, _ ->
                                    // status already updated in locVm; keep UI simple
                                }
                            }
                            showReportDialog = false
                        }
                    ) {
                        Text("Submit")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showReportDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    // Load route points whenever selected route changes.
    LaunchedEffect(selectedRouteId) {
        locVm.loadRoutePoints(routeId = selectedRouteId, maxPoints = 12)
        webViewRef?.evaluateJavascript("setRoute('${selectedRouteId}')", null)
    }

    // When points change, push them into the map.
    LaunchedEffect(routePoints, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (routePoints.isEmpty()) return@LaunchedEffect

        val arr = JSONArray()
        for (p in routePoints) {
            val obj = JSONObject()
            obj.put("lat", p.lat)
            obj.put("lon", p.lon)
            obj.put("label", "Route ${selectedRouteId}")
            obj.put("severity", p.severity ?: 2.0)
            arr.put(obj)
        }

        // Avoid quoting/escaping issues by passing JSON as a JS string literal via JSON.stringify
        val json = arr.toString()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("\r", "")

        wv.evaluateJavascript("setTrafficPoints(\"$json\")", null)
    }

    DisposableEffect(Unit) {
        onDispose { onStopLocation() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebViewForLeaflet(
    wv: WebView,
    onMapClick: (Double, Double) -> Unit,
    onUserLocation: (Double, Double) -> Unit
) {
    val settings: WebSettings = wv.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true

    // Helpful for debugging blank maps caused by blocked CDN assets.
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

    wv.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(
                "LeafletWebView",
                "${consoleMessage.message()} (line ${consoleMessage.lineNumber()} @ ${consoleMessage.sourceId()})"
            )
            return super.onConsoleMessage(consoleMessage)
        }
    }

    wv.webViewClient = object : WebViewClient() {
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            Log.e("LeafletWebView", "onReceivedError url=${request?.url} error=${error?.description}")
            super.onReceivedError(view, request, error)
        }

        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            Log.e("LeafletWebView", "onReceivedHttpError url=${request?.url} status=${errorResponse?.statusCode}")
            super.onReceivedHttpError(view, request, errorResponse)
        }
    }

    @Suppress("unused")
    wv.addJavascriptInterface(object {
        @JavascriptInterface
        fun onMapTap(lat: Double, lon: Double) {
            onMapClick(lat, lon)
        }

        @JavascriptInterface
        fun onUserLocation(lat: Double, lon: Double) {
            onUserLocation(lat, lon)
        }
    }, "AndroidBridge")

    wv.loadUrl("file:///android_asset/leaflet_map.html")
}

private fun escapeJsString(s: String): String {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
