package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Simple settings service for device storage
 */
@State(
    name = "ADBWirelessSettings",
    storages = [Storage("adbWirelessSettings.xml")]
)
@Service(Service.Level.APP)
class SettingsService : PersistentStateComponent<SettingsService> {

    data class DeviceState(
        var name: String = "",
        var ip: String = "",
        var port: String = "5555"
    ) {
        fun toDevice() = Device(name, ip, port)
    }

    var devices: MutableList<DeviceState> = mutableListOf()

    companion object {
        fun getInstance(): SettingsService {
            return ApplicationManager.getApplication().getService(SettingsService::class.java)
        }
    }

    override fun getState(): SettingsService = this

    override fun loadState(state: SettingsService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun saveDevice(device: Device) {
        // Remove existing device with same IP
        devices.removeIf { it.ip == device.ip }
        // Add new device
        devices.add(0, DeviceState(device.name, device.ip, device.port))
        // Keep only last 10 devices
        if (devices.size > 10) {
            devices = devices.take(10).toMutableList()
        }
    }

    fun getSavedDevices(): List<Device> {
        return devices.map { it.toDevice() }
    }

    fun removeDevice(ip: String) {
        devices.removeIf { it.ip == ip }
    }

    fun clearAllDevices() {
        devices.clear()
    }
}