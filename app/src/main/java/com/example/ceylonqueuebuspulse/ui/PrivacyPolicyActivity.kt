package com.example.ceylonqueuebuspulse.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ceylonqueuebuspulse.R
import com.example.ceylonqueuebuspulse.ui.theme.CeylonQueueBusPulseTheme

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CeylonQueueBusPulseTheme {
                PrivacyPolicyScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicyScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.title_privacy)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(id = R.string.privacy_intro),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(12.dp))

            Text(text = stringResource(id = R.string.privacy_location_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(id = R.string.privacy_location_body), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            Text(text = stringResource(id = R.string.privacy_network_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(id = R.string.privacy_network_body), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            Text(text = stringResource(id = R.string.privacy_notifications_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(id = R.string.privacy_notifications_body), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            Text(text = stringResource(id = R.string.privacy_data_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(id = R.string.privacy_data_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
