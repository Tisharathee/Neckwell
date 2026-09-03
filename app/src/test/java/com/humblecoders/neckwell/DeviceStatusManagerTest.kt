package com.humblecoders.neckwell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStatusManagerTest {

    @Test
    fun testParseConnectionState() {
        assertEquals(DeviceConnectionState.CONNECTED, DeviceStatusManager.parseConnectionState("connected"))
        assertEquals(DeviceConnectionState.CONNECTED, DeviceStatusManager.parseConnectionState("Connected"))
        assertEquals(DeviceConnectionState.CONNECTED, DeviceStatusManager.parseConnectionState("online"))
        assertEquals(DeviceConnectionState.CONNECTED, DeviceStatusManager.parseConnectionState("true"))

        assertEquals(DeviceConnectionState.CONNECTING, DeviceStatusManager.parseConnectionState("connecting"))
        assertEquals(DeviceConnectionState.CONNECTING, DeviceStatusManager.parseConnectionState("pairing"))

        assertEquals(DeviceConnectionState.DISCONNECTED, DeviceStatusManager.parseConnectionState("disconnected"))
        assertEquals(DeviceConnectionState.DISCONNECTED, DeviceStatusManager.parseConnectionState("offline"))
        assertEquals(DeviceConnectionState.DISCONNECTED, DeviceStatusManager.parseConnectionState("false"))
        assertEquals(DeviceConnectionState.DISCONNECTED, DeviceStatusManager.parseConnectionState("idle"))
    }

    @Test
    fun testParseWifiState() {
        assertEquals(WifiState.CONNECTED, DeviceStatusManager.parseWifiState("connected"))
        assertEquals(WifiState.CONNECTED, DeviceStatusManager.parseWifiState("online"))

        assertEquals(WifiState.CONNECTING, DeviceStatusManager.parseWifiState("connecting"))

        assertEquals(WifiState.FAILED, DeviceStatusManager.parseWifiState("failed"))
        assertEquals(WifiState.FAILED, DeviceStatusManager.parseWifiState("error"))

        assertEquals(WifiState.DISCONNECTED, DeviceStatusManager.parseWifiState("disconnected"))
        assertEquals(WifiState.DISCONNECTED, DeviceStatusManager.parseWifiState("offline"))
    }

    @Test
    fun testBatteryThresholds() {
        val normalStatus = DeviceStatus(batteryLevel = 85, isLowBattery = 85 < 20)
        assertFalse(normalStatus.isLowBattery)

        val lowStatus = DeviceStatus(batteryLevel = 15, isLowBattery = 15 < 20)
        assertTrue(lowStatus.isLowBattery)
    }
}
