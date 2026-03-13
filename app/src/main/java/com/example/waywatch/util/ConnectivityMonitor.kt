// Edited: 2026-01-08
// Purpose: Monitor network connectivity and provide offline-first caching strategy support

package com.example.waywatch.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Network connectivity state.
 */
enum class NetworkState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
}

/**
 * Monitor network connectivity changes.
 */
class ConnectivityMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Check if device currently has network connectivity.
     */
    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        // Consider connected if INTERNET is present and either VALIDATED is true or we have a common transport.
        // This avoids false negatives on some devices/networks where VALIDATED can lag behind.
        return hasInternet && (isValidated || hasTransport)
    }

    /**
     * Observe network connectivity as a Flow.
     * Emits NetworkState whenever connectivity changes.
     */
    fun observeConnectivity(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {

            private val networks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                networks.add(network)
                trySend(NetworkState.CONNECTED)
            }

            override fun onLost(network: Network) {
                networks.remove(network)
                if (networks.isEmpty()) {
                    trySend(NetworkState.DISCONNECTED)
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val hasInternet = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                val isValidated = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                if (hasInternet && (isValidated || hasTransport)) {
                    networks.add(network)
                    trySend(NetworkState.CONNECTED)
                } else {
                    networks.remove(network)
                    if (networks.isEmpty()) {
                        trySend(NetworkState.DISCONNECTED)
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Send initial state
        trySend(if (isConnected()) NetworkState.CONNECTED else NetworkState.DISCONNECTED)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Get connection type details.
     */
    fun getConnectionType(): ConnectionType {
        val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectionType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                ConnectionType.ETHERNET
            else -> ConnectionType.OTHER
        }
    }
}

/**
 * Types of network connections.
 */
enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE
}
