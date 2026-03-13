package com.example.waywatch.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.waywatch.settings.SettingsViewModel
import com.example.waywatch.traffic.LocationTrafficViewModel
import com.example.waywatch.traffic.MapComposeScreen
import com.example.waywatch.traffic.MapComposeViewModel
import com.example.waywatch.ui.account.AccountScreen
import com.example.waywatch.ui.account.AccountViewModel
import com.example.waywatch.ui.auth.AuthViewModel
import org.koin.androidx.compose.koinViewModel
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationScreen(
    vm: MapComposeViewModel,
    locVm: LocationTrafficViewModel,
    authVm: AuthViewModel,
    settingsVm: SettingsViewModel,
    accountVm: AccountViewModel = koinViewModel(),
    isDarkMode: Boolean,
    initialRouteId: String,
    onBackPress: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onStartLocation: (MapView) -> Unit,
    onStopLocation: () -> Unit,
    onLogout: () -> Unit,
) {
    var currentTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                    label = { Text("Map") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "My Reports") },
                    label = { Text("Reports") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Account") },
                    label = { Text("Account") }
                )
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                0 -> {
                    MapComposeScreen(
                        vm = vm,
                        locVm = locVm,
                        authVm = authVm,
                        settingsVm = settingsVm,
                        isDarkMode = isDarkMode,
                        initialRouteId = initialRouteId,
                        onBackPress = onBackPress,
                        onRequestLocationPermission = onRequestLocationPermission,
                        onStartLocation = onStartLocation,
                        onStopLocation = onStopLocation,
                        onLogout = onLogout
                    )
                }
                1 -> {
                    // Placeholder for My Reports
                    Text("My Reports Screen", modifier = Modifier.padding(16.dp))
                }
                2 -> {
                    AccountScreen(viewModel = accountVm)
                }
            }
        }
    }
}
