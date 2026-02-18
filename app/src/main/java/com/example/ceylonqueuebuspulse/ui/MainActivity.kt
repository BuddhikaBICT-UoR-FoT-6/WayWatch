package com.example.ceylonqueuebuspulse.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ceylonqueuebuspulse.traffic.MapComposeActivity
import com.example.ceylonqueuebuspulse.traffic.LocationTrafficViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity(){
   private val locVm: LocationTrafficViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val provider by locVm.provider.collectAsState(initial = null)
            val status by locVm.status.collectAsState(initial = null)
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)){
                Button(onClick = {
                    startActivity(
                        Intent(this@MainActivity, MapComposeActivity::class.java).apply {
                            putExtra(MapComposeActivity.EXTRA_ROUTE_ID, "138")
                        }
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Map (MapComposeActivity)")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Recent Provider:")
                Spacer(modifier = Modifier.height(8.dp))

                provider?.let { p ->
                    Text(text = "Provider data: ${p.mapped}")
                } ?: Text(text = "No provider data yet")

                status?.let { Text(text = "Status: $it") }
            }

        }

    }

}