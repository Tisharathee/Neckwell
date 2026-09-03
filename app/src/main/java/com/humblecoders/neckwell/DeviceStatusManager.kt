package com.humblecoders.neckwell

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
    private val _deviceStatus = MutableStateFlow(DeviceStatus())
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    private var statusListener: ListenerRegistration? = null
    private var calibrationListener: ListenerRegistration? = null
    private var isListening = false

    fun startListening() {
        if (isListening) return
        isListening = true

        val db = Firebase.firestore

        // Listen to esp32/status in real time
        statusListener = db.collection("esp32").document("status")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
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
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

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
                    rawStatus == "disconnected" -> DeviceConnectionState.DISCONNECTED
                    else -> _deviceStatus.value.connectionState
                }

                _deviceStatus.value = _deviceStatus.value.copy(
                    connectionState = connState,
                    wifiState = wifiState,
                    batteryLevel = battery.coerceIn(0, 100),
                    isLowBattery = battery < 20
                )
            }
    }

    fun stopListening() {
        statusListener?.remove()
        calibrationListener?.remove()
        isListening = false
    }

    fun parseConnectionState(raw: String?): DeviceConnectionState {
        return when (raw?.lowercase()?.trim()) {
            "connected", "true", "online", "active" -> DeviceConnectionState.CONNECTED
            "connecting", "pairing" -> DeviceConnectionState.CONNECTING
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
