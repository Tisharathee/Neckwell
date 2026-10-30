/*
 * NeckWell ESP32-C6 — Heartbeat addition
 *
 * Add this function and its call-site to your existing firmware sketch.
 * It writes `esp32/status.beat` (uptime in seconds) every 15 s so the
 * Android app can detect whether the device is online.
 *
 * Requirements:
 *   - Firebase_ESP_Client library (already used for posture / calibration)
 *   - Firebase.ready() available in loop()
 *
 * Usage:
 *   1. Copy `sendHeartbeat()` into your sketch (or a new .cpp / .h pair).
 *   2. Call `sendHeartbeat()` at the top of `loop()`.
 *   3. Reflash the ESP32-C6.
 */

#include <Firebase_ESP_Client.h>

// ── Heartbeat config ────────────────────────────────────────────────
static const unsigned long HEARTBEAT_INTERVAL_MS = 15000;   // 15 seconds
static unsigned long       lastHeartbeatMs       = 0;

/**
 * Non-blocking heartbeat writer.
 * Patches `esp32/status` with field `beat` = device uptime in seconds.
 * Only fires when Firebase is ready and at least HEARTBEAT_INTERVAL_MS
 * has elapsed since the last successful write.
 *
 * Call this from loop() — it returns immediately if it's not time yet.
 */
void sendHeartbeat() {
    if (!Firebase.ready()) return;

    unsigned long now = millis();
    if (now - lastHeartbeatMs < HEARTBEAT_INTERVAL_MS) return;
    lastHeartbeatMs = now;

    // Build the patch document: { "fields": { "beat": { "integerValue": "<uptime_s>" } } }
    FirebaseJson content;
    content.set("fields/beat/integerValue", String(now / 1000));

    // Use your existing FirebaseData object (commonly named `fbdo`).
    // Adjust `fbdo` and the project/database path to match your sketch.
    extern FirebaseData fbdo;

    String documentPath = "esp32/status";
    String updateMask   = "beat";

    if (Firebase.Firestore.patchDocument(
            &fbdo,
            FIREBASE_PROJECT_ID,   // your #define / const
            "",                    // database id, "" = default
            documentPath.c_str(),
            content.raw(),
            updateMask.c_str())) {
        // success — nothing to do
    } else {
        Serial.print("[Heartbeat] Firestore patch failed: ");
        Serial.println(fbdo.errorReason());
    }
}

/*
 * In your existing loop(), add at the very top:
 *
 *   void loop() {
 *       sendHeartbeat();          // ← add this line
 *       // … rest of your existing loop code …
 *   }
 */

