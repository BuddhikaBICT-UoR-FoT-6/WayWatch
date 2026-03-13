package com.example.waywatch.traffic

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.waywatch.R
import kotlin.random.Random

class MapActivity : AppCompatActivity() {
    private val vm: LocationTrafficViewModel by viewModel()
    private lateinit var tapHint: TextView
    private lateinit var submitBtn: Button
    private var selLat: Double = 7.21
    private var selLon: Double = 79.88

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        tapHint = findViewById(R.id.tapHint)
        submitBtn = findViewById(R.id.submitBtn)

        // Observe provider results (StateFlow) using repeatOnLifecycle
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.provider.collectLatest { p ->
                        if (p != null && p.mapped != null) {
                            val severity = p.mapped["severity"]
                            tapHint.text = "Provider severity: $severity"
                        }
                    }
                }
                launch {
                    vm.status.collectLatest { s ->
                        if (s != null) tapHint.text = s
                    }
                }
            }
        }

        // Tapping the root view will jitter coordinates and trigger provider lookup
        findViewById<View>(R.id.root).setOnClickListener {
            selLat += (Random.nextDouble(-0.001, 0.001))
            selLon += (Random.nextDouble(-0.001, 0.001))
            tapHint.text = "Selected: ${"%.6f".format(selLat)}, ${"%.6f".format(selLon)}"
            vm.selectLocation(selLat, selLon)

            // TODO: when TomTom SDK is available, add a marker to the mapView here
        }

        submitBtn.setOnClickListener {
            // Submit severity (use fixed severity for demo)
            vm.submitSample(null, 3, selLat, selLon) { ok, err ->
                runOnUiThread {
                    tapHint.text = if (ok) "Sample submitted" else "Failed to submit sample: $err"
                }
            }
        }
    }

    // Keep basic lifecycle hooks; no TomTom map calls here to avoid SDK dependency during static checks.
    override fun onStart() { super.onStart() }
    override fun onResume() { super.onResume() }
    override fun onPause() { super.onPause() }
    override fun onStop() { super.onStop() }
    override fun onDestroy() { super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory() }
}
