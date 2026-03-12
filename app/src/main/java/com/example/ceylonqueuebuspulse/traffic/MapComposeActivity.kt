package com.example.ceylonqueuebuspulse.traffic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ceylonqueuebuspulse.BuildConfig
import com.example.ceylonqueuebuspulse.MainActivity
import com.example.ceylonqueuebuspulse.R
import com.example.ceylonqueuebuspulse.data.auth.PendingDeepLinkStore
import com.example.ceylonqueuebuspulse.settings.SettingsViewModel
import com.example.ceylonqueuebuspulse.settings.ThemeMode
import com.example.ceylonqueuebuspulse.ui.PrivacyPolicyActivity
import com.example.ceylonqueuebuspulse.ui.auth.AuthViewModel
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme
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
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.Marker
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

class MapComposeActivity : ComponentActivity() {
    private val vm: MapComposeViewModel by viewModel()
    private val locVm: LocationTrafficViewModel by viewModel()

    private val authVm: AuthViewModel by viewModel()
    private val settingsVm: SettingsViewModel by viewModel()
    private val pendingDeepLinkStore: PendingDeepLinkStore by inject()

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* handled in compose */ }

    private var headingJob: Job? = null
    private var latestHeadingDeg: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup osmdroid config (MUST BE SET BEFORE MAPVIEW CREATION)
        Configuration.getInstance().load(applicationContext, android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext))
        Configuration.getInstance().userAgentValue = packageName

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

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
            val settings by settingsVm.settings.collectAsState()
            val isDark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CeylonQueueBusPulseTheme(darkTheme = isDark) {
                MapComposeScreen(
                    vm = vm,
                    locVm = locVm,
                    authVm = authVm,
                    settingsVm = settingsVm,
                    isDarkMode = isDark,
                    initialRouteId = initialRouteId,
                    onBackPress = { finish() },
                    onRequestLocationPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onStartLocation = { mapView -> startLocationUpdates(mapView) },
                    onStopLocation = { stopLocationUpdates() },
                    onLogout = {
                        pendingDeepLinkStore.clear()
                        authVm.logout()
                        startActivity(Intent(this@MapComposeActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE_ID = "extra_route_id"
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(mapView: MapView) {
        if (locationCallback != null) return

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(6_000L)
            .build()
            
        // Setup MyLocationNewOverlay on osmdroid if not exists
        var myLocOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        if (myLocOverlay == null) {
            myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(mapView.context), mapView)
            myLocOverlay.enableMyLocation()
            mapView.overlays.add(myLocOverlay)
        } else {
            myLocOverlay.enableMyLocation()
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // The overlay manages itself pretty well, but we can force redraw
                mapView.invalidate()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapComposeScreen(
    vm: MapComposeViewModel,
    locVm: LocationTrafficViewModel,
    authVm: AuthViewModel,
    settingsVm: SettingsViewModel,
    isDarkMode: Boolean,
    initialRouteId: String,
    onBackPress: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onStartLocation: (MapView) -> Unit,
    onStopLocation: () -> Unit,
    onLogout: () -> Unit,
) {
    val routeVm: RouteCatalogViewModel = koinViewModel()
    val nearbyRoutes by routeVm.routes.collectAsState(initial = emptyList())
    val appSettings by settingsVm.settings.collectAsState()

    var query by remember { mutableStateOf("") }
    val places by vm.places.collectAsState(initial = emptyList())
    val status by vm.status.collectAsState(initial = null)
    val routePoints by locVm.routePoints.collectAsState(initial = emptyList())
    val providerData by locVm.provider.collectAsState(initial = null)

    val context = LocalContext.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

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

    LaunchedEffect(nearbyRoutes) {
        if (nearbyRoutes.isNotEmpty()) {
            val contains = nearbyRoutes.any { it.ref == selectedRouteId }
            if (!contains) {
                selectedRouteId = nearbyRoutes.first().ref
            }
        }
    }

    var selectedPoint by remember { mutableStateOf<PlaceResult?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportSeverity by remember { mutableStateOf(3f) }
    var isAnonymous by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Colors for markers
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val highSevColor = AndroidColor.RED
    val medSevColor = AndroidColor.rgb(255, 165, 0) // Orange
    val lowSevColor = AndroidColor.GREEN

    // State-aware Back Navigation
    androidx.activity.compose.BackHandler(enabled = true) {
        if (showReportDialog) {
            showReportDialog = false
        } else {
            onBackPress()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- NATIVE OSMDROID MAP ---
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    
                    // Set default center (Colombo roughly) if location not known yet
                    controller.setCenter(GeoPoint(6.9271, 79.8612))

                    // Map tap events
                    val eventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            routeVm.loadNearby(p.latitude, p.longitude)
                            selectedPoint = PlaceResult(label = "Dropped pin", lat = p.latitude, lon = p.longitude)
                            locVm.selectLocation(p.latitude, p.longitude)
                            showReportDialog = true
                            
                            val myLocOverlay = overlays.filterIsInstance<org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay>().firstOrNull()
                            val myLoc = myLocOverlay?.myLocation
                            if (myLoc != null) {
                                overlays.removeAll { it is org.osmdroid.views.overlay.Polyline }
                                val line = org.osmdroid.views.overlay.Polyline()
                                line.setPoints(listOf(myLoc, GeoPoint(p.latitude, p.longitude)))
                                line.outlinePaint.color = android.graphics.Color.parseColor("#00BFFF") 
                                line.outlinePaint.strokeWidth = 12f
                                overlays.add(line)
                                invalidate()
                            }
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    }
                    overlays.add(MapEventsOverlay(eventsReceiver))
                    
                    // Default tile color filter for dark mode (basic inversion)
                    if (isDarkMode) {
                        overlayManager.tilesOverlay.setColorFilter(org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS)
                    }

                    mapViewRef = this
                    
                    refreshPermissionState()
                    if (fineGranted.value || coarseGranted.value) {
                        onStartLocation(this)
                    }
                }
            },
            update = { wv -> 
                mapViewRef = wv 
                if (isDarkMode) {
                    wv.overlayManager.tilesOverlay.setColorFilter(org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS)
                } else {
                    wv.overlayManager.tilesOverlay.setColorFilter(null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ═══════════════════════════════════════════════════════════
        // FLOATING SEARCH BAR & BACK BUTTON
        // ═══════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 64.dp, top = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallFloatingActionButton(
                onClick = {
                    if (showReportDialog) {
                        showReportDialog = false
                    } else {
                        onBackPress()
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LaunchedEffect(query) {
                            if (query.length >= 3) {
                                kotlinx.coroutines.delay(500)
                                vm.search(query, com.example.ceylonqueuebuspulse.BuildConfig.TOMTOM_API_KEY)
                            }
                        }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                        IconButton(onClick = { vm.search(query, com.example.ceylonqueuebuspulse.BuildConfig.TOMTOM_API_KEY) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (places.isNotEmpty()) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            items(places.size) { index ->
                                val place = places[index]
                                androidx.compose.material3.ListItem(
                                    headlineContent = { Text(place.label, style = MaterialTheme.typography.bodyMedium) },
                                    leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable {
                                        query = place.label
                                        vm.selectPlace(place)
                                        // Clear results manually via ViewModel or locally if possible
                                        // For now, vm.search("") would clear or we can just assume selectPlace triggers a state change that hides this
                                        // Actually, let's ensure the VM has a clear method or just call search with empty
                                        vm.search("", "") 
                                        
                                        // Pan map
                                        mapViewRef?.let { mv ->
                                            val gp = GeoPoint(place.lat, place.lon)
                                            mv.controller.animateTo(gp)
                                            mv.controller.setZoom(15.5)
                                            
                                            // Trigger routing logic - same as singleTapConfirmedHelper
                                            routeVm.loadNearby(place.lat, place.lon)
                                            selectedPoint = place
                                            locVm.selectLocation(place.lat, place.lon)
                                            showReportDialog = true
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // FLOATING ROUTE CHIPS (below search bar)
        // ═══════════════════════════════════════════════════════════
        val preferredChips = appSettings.preferredRoutes.sorted().take(4).map { RouteChip(it) }
        val chips = when {
            nearbyRoutes.isNotEmpty() -> nearbyRoutes.take(4)
            preferredChips.isNotEmpty() -> preferredChips
            else -> emptyList()
        }
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 108.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { chip ->
                    FilterChip(
                        selected = selectedRouteId == chip.ref,
                        onClick = {
                            selectedRouteId = chip.ref
                            val updated = (appSettings.preferredRoutes + chip.ref).take(8).toSet()
                            settingsVm.setPreferredRoutes(updated)
                        },
                        label = { Text(chip.ref, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // HAMBURGER FAB (top-right corner)
        // ═══════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { menuExpanded = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_user_management)) },
                    onClick = { menuExpanded = false; onLogout() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_privacy)) },
                    onClick = { menuExpanded = false; context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_location_settings)) },
                    onClick = { menuExpanded = false; context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (isDarkMode) stringResource(R.string.menu_light_mode) else stringResource(R.string.menu_dark_mode)) },
                    onClick = { menuExpanded = false; settingsVm.setThemeMode(if (isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK) }
                )
            }
        }

        // ═══════════════════════════════════════════════════════════
        // STATUS & PERMISSION MESSAGES
        // ═══════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 148.dp, end = 16.dp)
        ) {
            status?.let {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                ) { Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
            }
            if (!(fineGranted.value || coarseGranted.value)) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f))
                ) { Text(stringResource(R.string.location_permission_required), modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // MAP CONTROLS (Center on me, Zoom In, Zoom Out)
        // ═══════════════════════════════════════════════════════════
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomIn() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, contentDescription = "Zoom In") }

            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomOut() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Remove, contentDescription = "Zoom Out") }

            FloatingActionButton(
                onClick = {
                    refreshPermissionState()
                    if (!(fineGranted.value || coarseGranted.value)) {
                        onRequestLocationPermission()
                        return@FloatingActionButton
                    }
                    
                    mapViewRef?.let { wv ->
                        onStartLocation(wv)
                        val locOverlay = wv.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                        val loc = locOverlay?.myLocation
                        if (loc != null) {
                            wv.controller.animateTo(loc)
                            routeVm.loadNearby(loc.latitude, loc.longitude)
                            
                            selectedPoint = PlaceResult(label = "My Location", lat = loc.latitude, lon = loc.longitude)
                            locVm.selectLocation(loc.latitude, loc.longitude)
                            showReportDialog = true
                            
                            wv.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline }
                            wv.invalidate()
                        }
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.action_center_on_me)) }
        }

        // ═══════════════════════════════════════════════════════════
        // SEARCH RESULTS PANEL
        // ═══════════════════════════════════════════════════════════
        if (places.isNotEmpty()) {
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(8.dp)) {
                    items(places) { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = p.label)
                                Spacer(Modifier.height(2.dp))
                                Text(text = "${p.lat}, ${p.lon}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                selectedPoint = p
                                routeVm.loadNearby(p.lat, p.lon)
                                mapViewRef?.controller?.animateTo(GeoPoint(p.lat, p.lon))
                                vm.selectPlace(p)
                                locVm.selectLocation(p.lat, p.lon)
                                showReportDialog = true
                                
                                val map = mapViewRef ?: return@Button
                                val myLocOverlay = map.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                                val myLoc = myLocOverlay?.myLocation
                                if (myLoc != null) {
                                    map.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline }
                                    val line = org.osmdroid.views.overlay.Polyline()
                                    line.setPoints(listOf(myLoc, GeoPoint(p.lat, p.lon)))
                                    line.outlinePaint.color = android.graphics.Color.parseColor("#00BFFF") 
                                    line.outlinePaint.strokeWidth = 12f
                                    map.overlays.add(line)
                                    map.invalidate()
                                }
                            }) { Text("Select") }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // TRAFFIC REPORT & DETAILS DIALOG
        // ═══════════════════════════════════════════════════════════
        if (showReportDialog) {
            val current = selectedPoint
            val mapped = providerData?.mapped
            
            // Extract Internet Traffic (try multiple possible backend keys)
            val rawInternet = mapped?.get("internetTraffic") 
                ?: mapped?.get("internet_traffic")
                ?: mapped?.get("providerSeverity")
                ?: mapped?.get("severity")
                ?: providerData?.raw?.get("internetTraffic")
                ?: providerData?.raw?.get("internet_traffic")
                ?: providerData?.raw?.get("providerSeverity")
                
            val internetTrafficVal = (rawInternet as? Number)?.toDouble()
            
            // Extract User Submissions list
            val userSubmissions = (mapped?.get("userSubmissions") 
                ?: mapped?.get("user_submissions")
                ?: providerData?.raw?.get("userSubmissions")
                ?: providerData?.raw?.get("user_submissions")) as? List<*>
            
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text("Location Traffic") },
                text = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            if (current != null) {
                                Text("Location: ${current.label}", style = MaterialTheme.typography.titleSmall)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        
                        // API Data
                        item {
                            Text("Current Traffic Status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if (internetTrafficVal != null) {
                                val trafficDesc = when {
                                    internetTrafficVal < 2.5 -> "Low traffic, smooth sailing"
                                    internetTrafficVal < 3.5 -> "Moderate traffic"
                                    else -> "High traffic, will take long time"
                                }
                                Text("Internet Traffic: $trafficDesc", style = MaterialTheme.typography.bodyLarge)
                                
                                if (internetTrafficVal >= 3.5) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val map = mapViewRef ?: return@Button
                                            val myLocOverlay = map.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                                            val myLoc = myLocOverlay?.myLocation
                                            val p = selectedPoint
                                            if (myLoc != null && p != null) {
                                                map.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline }
                                                val line = org.osmdroid.views.overlay.Polyline()
                                                val midLat = (myLoc.latitude + p.lat) / 2 + 0.02
                                                val midLon = (myLoc.longitude + p.lon) / 2 + 0.02
                                                line.setPoints(listOf(myLoc, GeoPoint(midLat, midLon), GeoPoint(p.lat, p.lon)))
                                                line.outlinePaint.color = android.graphics.Color.parseColor("#32CD32") // Green
                                                line.outlinePaint.strokeWidth = 12f
                                                map.overlays.add(line)
                                                map.invalidate()
                                                showReportDialog = false
                                                
                                                android.widget.Toast.makeText(context, "Alternative safely routed!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Text("Suggest another route")
                                    }
                                }
                            } else if (mapped != null || providerData?.raw != null) {
                                // Diagnostic: We got a response but couldn't parse the key
                                Text("No API traffic value found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                val diagStr = mapped?.toString() ?: providerData?.raw?.toString()
                                Text("API Response keys: ${diagStr?.take(100)}...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text("Internet Traffic: Scanning API...", style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // User reports
                            if (!userSubmissions.isNullOrEmpty()) {
                                Text("Recent User Reports:", style = MaterialTheme.typography.labelLarge)
                                userSubmissions.forEach { subItem ->
                                    val subMap = subItem as? Map<*, *>
                                    if (subMap != null) {
                                        val sev = (subMap["severity"] as? Number)?.toDouble() ?: 0.0
                                        val anon = (subMap["anonymous"] as? Boolean) == true
                                        val score = (subMap["score"] as? Number)?.toDouble()
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (anon) {
                                                // Anonymous marker
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = androidx.compose.ui.graphics.Color.Yellow,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    Text("Anon", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = androidx.compose.ui.graphics.Color.Black, style = MaterialTheme.typography.labelSmall)
                                                }
                                                Text("Severity $sev")
                                            } else {
                                                // Named marker with score
                                                Icon(Icons.Default.MyLocation, contentDescription = "User", modifier = Modifier.size(16.dp).padding(end = 4.dp))
                                                Text("User Severity $sev")
                                                if (score != null) {
                                                    Text(" (Accuracy Score: ${(score * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("No user submissions yet.", style = MaterialTheme.typography.bodySmall)
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        }
                        
                        // Report Section
                        item {
                            Text("Submit your report", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Traffic Level: ${reportSeverity.toInt()}", modifier = Modifier.weight(1f))
                                Text("(1=Low, 5=High)", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            Slider(value = reportSeverity, onValueChange = { reportSeverity = it }, valueRange = 1f..5f, steps = 3)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isAnonymous,
                                    onCheckedChange = { isAnonymous = it }
                                )
                                Text("Submit Anonymously", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val point = current
                        if (point != null) {
                            val submitUserId = if (isAnonymous) null else authVm.getUserId()
                            locVm.submitSample(
                                routeId = selectedRouteId, 
                                severity = reportSeverity.toInt(), 
                                lat = point.lat, 
                                lon = point.lon,
                                userIdHash = submitUserId,
                                callback = { _, _ -> }
                            )
                        }
                        showReportDialog = false
                    }) { Text("Submit") }
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
    }

    // When points change, add them as markers
    LaunchedEffect(routePoints, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        
        // Remove old markers (keep MyLocation overlay and MapEvents)
        map.overlays.removeAll { it is Marker }
        
        // Add new markers based on traffic
        for (p in routePoints) {
            val marker = Marker(map)
            marker.position = GeoPoint(p.lat, p.lon)
            marker.title = "Route ${selectedRouteId}"
            marker.snippet = "Severity: ${p.severity ?: 2.0}"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Color according to OSmdroid marker default icon styling approximation (or set icon)
            // Osmdroid default marker doesn't support direct tinting easily without a custom drawable, 
            // so we set text instead. Native marker usually suffices.
            
            map.overlays.add(marker)
        }
        
        map.invalidate()
    }

    DisposableEffect(Unit) {
        onDispose { onStopLocation() }
    }
}
