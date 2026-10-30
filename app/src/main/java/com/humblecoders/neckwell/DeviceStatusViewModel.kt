package com.humblecoders.neckwell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Connection states for the ESP32-C6 hardware device based on heartbeat signals.
 */
enum class DeviceConn {
    CHECKING,
    CONNECTED,
    DISCONNECTED
}

/**
 * Timeout in milliseconds without a heartbeat change before considering the device disconnected.
 */
const val DEVICE_HEARTBEAT_TIMEOUT_MS = 45_000L

class DeviceStatusViewModel(
    private val db: FirebaseFirestore = Firebase.firestore
) : ViewModel() {

    private val beatFlow: Flow<Long?> = callbackFlow {
        val listener = db.document("esp32/status")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                trySend(snap?.getLong("beat"))
            }
        awaitClose {
            listener.remove()
        }
    }

    val connectionState: StateFlow<DeviceConn> = flow {
        var first = true
        emitAll(
            beatFlow
                .distinctUntilChanged()
                .transformLatest { _ ->
                    if (first) {
                        first = false
                        emit(DeviceConn.CHECKING)
                    } else {
                        emit(DeviceConn.CONNECTED)
                    }
                    delay(DEVICE_HEARTBEAT_TIMEOUT_MS)
                    emit(DeviceConn.DISCONNECTED)
                }
                .distinctUntilChanged()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceConn.CHECKING
    )
}

