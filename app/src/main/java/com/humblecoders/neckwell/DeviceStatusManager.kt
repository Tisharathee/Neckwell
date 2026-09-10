package com.humblecoders.neckwell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeviceConnectionState {
    CONNECTED,
    CONNECTING,
    RECONNECTING,
    DISCONNECTED
}

enum class WifiState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    FAILED
}

data class DeviceStatus(
    val connectionState: DeviceConnectionState = DeviceConnectionState.CONNECTED,
    val wifiState: WifiState = WifiState.CONNECTED,
    val batteryLevel: Int = 85,
    val isLowBattery: Boolean = false,
    val deviceName: String = "NeckWell Sensor v2.1",
    val lastSeenTimestamp: Long = 0L
)

object DeviceStatusManager {
    private const val TAG = "NeckWell_DeviceStatus"

    private val _deviceStatus = MutableStateFlow(DeviceStatus())
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    private var statusListener: ListenerRegistration? = null
    private var calibrationListener: ListenerRegistration? = null
    private var isFirestoreListening = false
    private var firestoreSubscribersCount = 0

    // Live system listener fields
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var systemSubscribersCount = 0

    init {
        startListening()
    }

    fun updateWifiState(wifiState: WifiState) {
        if (_deviceStatus.value.wifiState != wifiState) {
            Log.d(TAG, "Live WiFi status changed: ${_deviceStatus.value.wifiState} -> $wifiState")
            _deviceStatus.value = _deviceStatus.value.copy(wifiState = wifiState)
        }
    }

    fun updateBattery(batteryLevel: Int) {
        val clamped = batteryLevel.coerceIn(0, 100)
        val isLow = clamped < 20
        if (_deviceStatus.value.batteryLevel != clamped || _deviceStatus.value.isLowBattery != isLow) {
            Log.d(TAG, "Live Battery status changed: ${_deviceStatus.value.batteryLevel}% -> $clamped% (isLow=$isLow)")
            _deviceStatus.value = _deviceStatus.value.copy(
                batteryLevel = clamped,
                isLowBattery = isLow
            )
        }
    }

    @Synchronized
    fun registerSystemListeners(context: Context) {
        systemSubscribersCount++
        if (systemSubscribersCount == 1) {
            startSystemListeners(context.applicationContext)
        }
    }

    @Synchronized
    fun unregisterSystemListeners(context: Context) {
        systemSubscribersCount = maxOf(0, systemSubscribersCount - 1)
        if (systemSubscribersCount == 0) {
            stopSystemListeners(context.applicationContext)
        }
    }

    private fun startSystemListeners(appContext: Context) {
        try {
            // 1. WiFi Connectivity Listener (NetworkCallback - event driven, no polling)
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                // Determine current WiFi state immediately on mount
                val isWifi = checkIsWifiActive(cm)
                updateWifiState(if (isWifi) WifiState.CONNECTED else WifiState.DISCONNECTED)

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val caps = cm.getNetworkCapabilities(network)
                        val isWifiNet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                        if (isWifiNet) {
                            Log.d(TAG, "WiFi Network available")
                            updateWifiState(WifiState.CONNECTED)
                        }
                    }

                    override fun onLost(network: Network) {
                        Log.d(TAG, "WiFi Network lost")
                        val isStillWifi = checkIsWifiActive(cm)
                        updateWifiState(if (isStillWifi) WifiState.CONNECTED else WifiState.DISCONNECTED)
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        val isWifiNet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        if (isWifiNet) {
                            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            updateWifiState(if (validated) WifiState.CONNECTED else WifiState.CONNECTING)
                        }
                    }

                    override fun onUnavailable() {
                        Log.d(TAG, "WiFi Network unavailable")
                        updateWifiState(WifiState.FAILED)
                    }
                }

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()

                cm.registerNetworkCallback(request, callback)
                networkCallback = callback
                Log.i(TAG, "Subscribed to live WiFi connectivity events")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start WiFi network listener: ${e.message}")
        }

        try {
            // 2. Battery Status Listener (BroadcastReceiver - event driven, no polling)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (level >= 0 && scale > 0) {
                            val pct = (level * 100) / scale
                            updateBattery(pct)
                        }
                    }
                }
            }

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            // Read sticky intent immediately on mount
            stickyIntent?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    updateBattery(pct)
                }
            }

            batteryReceiver = receiver
            Log.i(TAG, "Subscribed to live Battery status events")
        } catch (e: Exception) {
            Log.w(TAG, "Could not start Battery status listener: ${e.message}")
        }
    }

    private fun checkIsWifiActive(cm: ConnectivityManager): Boolean {
        return try {
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    private fun stopSystemListeners(appContext: Context) {
        networkCallback?.let { cb ->
            try {
                val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(cb)
                Log.i(TAG, "Unregistered live WiFi connectivity callback")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback: ${e.message}")
            }
            networkCallback = null
        }

        batteryReceiver?.let { br ->
            try {
                appContext.unregisterReceiver(br)
                Log.i(TAG, "Unregistered live Battery status receiver")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering battery receiver: ${e.message}")
            }
            batteryReceiver = null
        }
    }

    @Synchronized
    fun startListening() {
        firestoreSubscribersCount++
        if (isFirestoreListening) return

        try {
            val db = Firebase.firestore
            isFirestoreListening = true

            // Listen to esp32/status in real time
            statusListener = db.collection("esp32").document("status")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error in esp32/status snapshot listener", error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    if (snapshot.exists()) {
                        val rawConnection = snapshot.getString("connection")
                            ?: snapshot.getString("connection_status")
                            ?: snapshot.getString("status")
                            ?: (if (snapshot.getBoolean("connected") == true || snapshot.getBoolean("isConnected") == true) "connected" else null)

                        val connState = parseConnectionState(rawConnection)

                        val rawWifi = snapshot.getString("wifi")
                            ?: snapshot.getString("wifi_status")
                            ?: snapshot.getString("wifiStatus")
                            ?: snapshot.getString("wifiState")
                        val wifiState = parseWifiState(rawWifi)

                        val rawBattery = snapshot.getLong("battery")
                            ?: snapshot.getLong("battery_level")
                            ?: snapshot.getLong("batteryLevel")
                            ?: snapshot.getDouble("battery")?.toLong()
                            ?: snapshot.getDouble("battery_level")?.toLong()

                        val battery = rawBattery?.toInt() ?: _deviceStatus.value.batteryLevel

                        val timestamp = snapshot.getLong("timestamp") ?: System.currentTimeMillis()

                        Log.d(TAG, "Device status updated: connState=$connState, wifi=$wifiState, battery=$battery")
                        _deviceStatus.value = _deviceStatus.value.copy(
                            connectionState = connState,
                            wifiState = wifiState,
                            batteryLevel = battery.coerceIn(0, 100),
                            isLowBattery = battery < 20,
                            lastSeenTimestamp = timestamp
                        )
                    }
                }

            // Also listen to esp32/calibration in case battery/wifi/status are published there
            calibrationListener = db.collection("esp32").document("calibration")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error in esp32/calibration snapshot listener", error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    val rawStatus = snapshot.getString("status")
                    val rawBattery = snapshot.getLong("battery")
                        ?: snapshot.getLong("battery_level")
                        ?: snapshot.getDouble("battery")?.toLong()
                    val battery = rawBattery?.toInt() ?: _deviceStatus.value.batteryLevel

                    val rawWifi = snapshot.getString("wifi") ?: snapshot.getString("wifi_status")
                    val wifiState = if (rawWifi != null) parseWifiState(rawWifi) else _deviceStatus.value.wifiState

                    val connState = when {
                        rawStatus == "pending" || rawStatus == "calibrated" || rawStatus == "acknowledged" -> DeviceConnectionState.CONNECTED
                        rawStatus == "connecting" -> DeviceConnectionState.CONNECTING
                        rawStatus == "reconnecting" -> DeviceConnectionState.RECONNECTING
                        rawStatus == "disconnected" -> DeviceConnectionState.DISCONNECTED
                        else -> _deviceStatus.value.connectionState
                    }

                    Log.d(TAG, "Calibration status update: connState=$connState, wifi=$wifiState, battery=$battery")
                    _deviceStatus.value = _deviceStatus.value.copy(
                        connectionState = connState,
                        wifiState = wifiState,
                        batteryLevel = battery.coerceIn(0, 100),
                        isLowBattery = battery < 20
                    )
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Firestore listener (expected in local unit tests): ${e.message}")
        }
    }

    @Synchronized
    fun stopListening() {
        firestoreSubscribersCount = maxOf(0, firestoreSubscribersCount - 1)
        if (firestoreSubscribersCount == 0) {
            statusListener?.remove()
            statusListener = null
            calibrationListener?.remove()
            calibrationListener = null
            isFirestoreListening = false
        }
    }

    fun parseConnectionState(raw: String?): DeviceConnectionState {
        return when (raw?.lowercase()?.trim()) {
            "connected", "true", "online", "active" -> DeviceConnectionState.CONNECTED
            "connecting", "pairing" -> DeviceConnectionState.CONNECTING
            "reconnecting", "resuming", "retry", "retrying" -> DeviceConnectionState.RECONNECTING
            "disconnected", "false", "offline", "idle" -> DeviceConnectionState.DISCONNECTED
            else -> DeviceConnectionState.CONNECTED
        }
    }

    fun parseWifiState(raw: String?): WifiState {
        return when (raw?.lowercase()?.trim()) {
            "connected", "online" -> WifiState.CONNECTED
            "connecting" -> WifiState.CONNECTING
            "failed", "error" -> WifiState.FAILED
            "disconnected", "offline" -> WifiState.DISCONNECTED
            else -> WifiState.CONNECTED
        }
    }

    fun toggleConnection() {
        val currentState = _deviceStatus.value.connectionState
        if (currentState == DeviceConnectionState.CONNECTED) {
            disconnect()
        } else {
            connect()
        }
    }

    fun connect() {
        _deviceStatus.value = _deviceStatus.value.copy(
            connectionState = DeviceConnectionState.CONNECTING,
            wifiState = if (_deviceStatus.value.wifiState == WifiState.DISCONNECTED) WifiState.CONNECTING else _deviceStatus.value.wifiState
        )
        try {
            Firebase.firestore.collection("esp32").document("status").set(
                mapOf(
                    "requested" to true,
                    "action" to "connect",
                    "clientTimestamp" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        _deviceStatus.value = _deviceStatus.value.copy(
            connectionState = DeviceConnectionState.DISCONNECTED,
            wifiState = WifiState.DISCONNECTED
        )
        try {
            Firebase.firestore.collection("esp32").document("status").set(
                mapOf(
                    "requested" to false,
                    "action" to "disconnect",
                    "clientTimestamp" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
