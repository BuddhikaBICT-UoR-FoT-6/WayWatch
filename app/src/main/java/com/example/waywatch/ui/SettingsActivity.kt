package com.example.waywatch.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.waywatch.R
import com.example.waywatch.settings.SettingsViewModel
import com.example.waywatch.settings.ThemeMode
import com.example.waywatch.ui.theme.waywatchTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsActivity : ComponentActivity() {

    private val vm: SettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            waywatchTheme {
                SettingsScreen(vm = vm)
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()

    val context = LocalContext.current

    var preferredRoutesText by remember(settings.preferredRoutes) {
        mutableStateOf(settings.preferredRoutes.sorted().joinToString(","))
    }
    var watchedRoutesText by remember(settings.watchedRoutes) {
        mutableStateOf(settings.watchedRoutes.sorted().joinToString(","))
    }
    var severityText by remember(settings.severityThreshold) { mutableStateOf(settings.severityThreshold.toString()) }
    var refreshText by remember(settings.refreshIntervalMinutes) { mutableStateOf(settings.refreshIntervalMinutes.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // Privacy / permissions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.title_privacy), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(id = R.string.privacy_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        context.startActivity(Intent(context, PrivacyPolicyActivity::class.java))
                    }) {
                        Text(stringResource(id = R.string.action_privacy))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeModePicker(
                    value = settings.themeMode,
                    onChange = vm::setThemeMode
                )

                OutlinedTextField(
                    value = severityText,
                    onValueChange = { severityText = it },
                    label = { Text("Severity threshold (0-5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = refreshText,
                    onValueChange = { refreshText = it },
                    label = { Text("Refresh interval (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        severityText.toDoubleOrNull()?.let { vm.setSeverityThreshold(it) }
                        refreshText.toIntOrNull()?.let { vm.setRefreshIntervalMinutes(it.coerceAtLeast(15)) }
                    }) {
                        Text("Save")
                    }

                    TextButton(onClick = {
                        severityText = settings.severityThreshold.toString()
                        refreshText = settings.refreshIntervalMinutes.toString()
                    }) {
                        Text("Reset")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Routes (comma separated)", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = preferredRoutesText,
                    onValueChange = { preferredRoutesText = it },
                    label = { Text("Preferred routes") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = watchedRoutesText,
                    onValueChange = { watchedRoutesText = it },
                    label = { Text("Watched routes (notifications)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        vm.setPreferredRoutes(preferredRoutesText.toRouteSet())
                        vm.setWatchedRoutes(watchedRoutesText.toRouteSet())
                    }) {
                        Text("Save routes")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModePicker(value: ThemeMode, onChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { expanded = true }) {
                Text(value.name)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.name) },
                    onClick = {
                        expanded = false
                        onChange(mode)
                    }
                )
            }
        }
    }
}

private fun String.toRouteSet(): Set<String> =
    split(',')
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .toSet()
