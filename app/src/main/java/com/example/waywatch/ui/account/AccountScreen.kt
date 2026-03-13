package com.example.waywatch.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.waywatch.data.network.model.SessionDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(viewModel: AccountViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchSessions()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Account & Sessions") })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.isLoading && uiState.sessions.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null && uiState.sessions.isEmpty()) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.sessions) { session ->
                        SessionItem(
                            session = session,
                            onRevokeClick = { viewModel.revokeSession(session.deviceId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItem(session: SessionDto, onRevokeClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.deviceName ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                Text(text = "Device ID: ${session.deviceId}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Last Used: ${session.lastUsedAt ?: session.issuedAt}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onRevokeClick) {
                Text("Revoke")
            }
        }
    }
}
