package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Service for persisting plugin settings and saved devices
 * APPLICATION-LEVEL SERVICE - Shared across all projects
 */
@State(
    name = "ADBWirelessSettings",
    storages = [Storage("adbWirelessSettings.xml")]
)
@Service(Service.Level.APP)
class SettingsService : PersistentStateComponent<SettingsService> {

    /**
     * Serializable device state for XML persistence
     */
    data class DeviceState(
        var name: String = "",
        var ip: String = "",
        var defaultPort: String = "5555",
        var lastConnected: Long = 0
    ) {
        fun toDevice() = Device(name, ip, defaultPort, false, lastConnected)
    }

    // ✅ FIXED: Renamed property to avoid conflict with method name
    var deviceStates: MutableList<DeviceState> = mutableListOf()

    companion object {
        /**
         * Get singleton instance of settings service
         */
        fun getInstance(): SettingsService {
            return ApplicationManager.getApplication().getService(SettingsService::class.java)
        }
    }

    /**
     * Get current state for persistence
     */
    override fun getState(): SettingsService = this

    /**
     * Load state from persistence
     */
    override fun loadState(state: SettingsService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * Save a device to persistent storage
     */
    fun saveDevice(device: Device) {
        // Remove existing device with same IP to avoid duplicates
        deviceStates.removeIf { it.ip == device.ip }

        // Add new device at the beginning (most recent first)
        deviceStates.add(0, DeviceState(
            name = device.name,
            ip = device.ip,
            defaultPort = device.defaultPort,
            lastConnected = System.currentTimeMillis()
        ))

        // Keep only last 10 devices to avoid unlimited growth
        if (deviceStates.size > 10) {
            deviceStates = deviceStates.take(10).toMutableList()
        }
    }

    /**
     * Get all saved devices sorted by last connected time
     * ✅ FIXED: No more naming conflict with property
     */
    fun getSavedDevices(): List<Device> {
        return deviceStates
            .sortedByDescending { it.lastConnected }
            .map { it.toDevice() }
    }

    /**
     * Update last connected time for a device
     */
    fun updateLastConnected(ip: String) {
        deviceStates.find { it.ip == ip }?.let { device ->
            device.lastConnected = System.currentTimeMillis()
        }
    }

    /**
     * Remove a device from saved devices
     */
    fun removeDevice(ip: String) {
        deviceStates.removeIf { it.ip == ip }
    }

    /**
     * Clear all saved devices
     */
    fun clearAllDevices() {
        deviceStates.clear()
    }

    /**
     * Get the most recently connected device
     */
    fun getMostRecentDevice(): Device? {
        return getSavedDevices().firstOrNull()
    }

    /**
     * Get count of saved devices
     */
    fun getDeviceCount(): Int {
        return deviceStates.size
    }

    /**
     * Check if a device with given IP exists
     */
    fun hasDevice(ip: String): Boolean {
        return deviceStates.any { it.ip == ip }
    }
}