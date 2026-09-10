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

        assertEquals(DeviceConnectionState.RECONNECTING, DeviceStatusManager.parseConnectionState("reconnecting"))
        assertEquals(DeviceConnectionState.RECONNECTING, DeviceStatusManager.parseConnectionState("resuming"))
        assertEquals(DeviceConnectionState.RECONNECTING, DeviceStatusManager.parseConnectionState("retrying"))

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

    @Test
    fun testUpdateWifiStateLive() {
        DeviceStatusManager.updateWifiState(WifiState.DISCONNECTED)
        assertEquals(WifiState.DISCONNECTED, DeviceStatusManager.deviceStatus.value.wifiState)

        DeviceStatusManager.updateWifiState(WifiState.CONNECTED)
        assertEquals(WifiState.CONNECTED, DeviceStatusManager.deviceStatus.value.wifiState)

        DeviceStatusManager.updateWifiState(WifiState.CONNECTING)
        assertEquals(WifiState.CONNECTING, DeviceStatusManager.deviceStatus.value.wifiState)

        DeviceStatusManager.updateWifiState(WifiState.FAILED)
        assertEquals(WifiState.FAILED, DeviceStatusManager.deviceStatus.value.wifiState)
    }

    @Test
    fun testUpdateBatteryLive() {
        // Normal battery
        DeviceStatusManager.updateBattery(75)
        assertEquals(75, DeviceStatusManager.deviceStatus.value.batteryLevel)
        assertFalse(DeviceStatusManager.deviceStatus.value.isLowBattery)

        // Low battery (< 20%)
        DeviceStatusManager.updateBattery(12)
        assertEquals(12, DeviceStatusManager.deviceStatus.value.batteryLevel)
        assertTrue(DeviceStatusManager.deviceStatus.value.isLowBattery)

        // Clamping bounds
        DeviceStatusManager.updateBattery(120)
        assertEquals(100, DeviceStatusManager.deviceStatus.value.batteryLevel)
        assertFalse(DeviceStatusManager.deviceStatus.value.isLowBattery)

        DeviceStatusManager.updateBattery(-5)
        assertEquals(0, DeviceStatusManager.deviceStatus.value.batteryLevel)
        assertTrue(DeviceStatusManager.deviceStatus.value.isLowBattery)
    }
}
