package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.File
import java.nio.file.Paths

/**
 * Enhanced settings service with SDK path configuration
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
    var androidSdkPath: String = ""
    var customAdbPath: String = ""
    var useCustomAdbPath: Boolean = false
    var isFirstTimeSetup: Boolean = true // New field to track first-time setup

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

    /**
     * Get the ADB executable path based on configuration
     */
    fun getAdbPath(): String {
        // If custom ADB path is enabled and set, use it
        if (useCustomAdbPath && customAdbPath.isNotEmpty()) {
            return if (customAdbPath.endsWith("adb") || customAdbPath.endsWith("adb.exe")) {
                customAdbPath
            } else {
                // If they provided a directory, append the executable name
                val separator = if (System.getProperty("os.name").lowercase().contains("windows")) "\\" else "/"
                val executable = if (System.getProperty("os.name").lowercase().contains("windows")) "adb.exe" else "adb"
                "$customAdbPath${separator}platform-tools${separator}$executable"
            }
        }

        // Try to use configured Android SDK path
        if (androidSdkPath.isNotEmpty()) {
            val adbPath = getAdbPathFromSdk(androidSdkPath)
            if (File(adbPath).exists()) {
                return adbPath
            }
        }

        // Auto-detect SDK path
        val detectedSdkPath = detectAndroidSdkPath()
        if (detectedSdkPath != null) {
            androidSdkPath = detectedSdkPath // Save for future use
            return getAdbPathFromSdk(detectedSdkPath)
        }

        // Fallback to system PATH
        return if (System.getProperty("os.name").lowercase().contains("windows")) "adb.exe" else "adb"
    }

    /**
     * Get ADB path from SDK directory - Made public for use in dialogs
     */
    fun getAdbPathFromSdk(sdkPath: String): String {
        val separator = File.separator
        val executable = if (System.getProperty("os.name").lowercase().contains("windows")) "adb.exe" else "adb"
        return "$sdkPath${separator}platform-tools${separator}$executable"
    }

    /**
     * Auto-detect Android SDK path
     */
    fun detectAndroidSdkPath(): String? {
        val commonPaths = when {
            System.getProperty("os.name").lowercase().contains("windows") -> listOf(
                "${System.getProperty("user.home")}\\AppData\\Local\\Android\\Sdk",
                "C:\\Users\\${System.getProperty("user.name")}\\AppData\\Local\\Android\\Sdk",
                "D:\\Android\\Sdk",
                "C:\\Android\\Sdk",
                "D:\\Sdk"
            )
            System.getProperty("os.name").lowercase().contains("mac") -> listOf(
                "${System.getProperty("user.home")}/Library/Android/sdk",
                "${System.getProperty("user.home")}/Android/Sdk",
                "/Applications/Android Studio.app/Contents/sdk"
            )
            else -> listOf( // Linux
                "${System.getProperty("user.home")}/Android/Sdk",
                "${System.getProperty("user.home")}/android-sdk",
                "/opt/android-sdk",
                "/usr/local/android-sdk"
            )
        }

        // Check environment variables
        val envSdkPath = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        if (envSdkPath != null && File(getAdbPathFromSdk(envSdkPath)).exists()) {
            return envSdkPath
        }

        // Check common paths
        for (path in commonPaths) {
            if (File(getAdbPathFromSdk(path)).exists()) {
                return path
            }
        }

        // Check local.properties file if exists (in current directory or parent directories)
        val localPropertiesPath = findLocalProperties()
        if (localPropertiesPath != null) {
            try {
                val properties = java.util.Properties()
                File(localPropertiesPath).inputStream().use { properties.load(it) }
                val sdkDir = properties.getProperty("sdk.dir")
                if (sdkDir != null && File(getAdbPathFromSdk(sdkDir)).exists()) {
                    return sdkDir
                }
            } catch (e: Exception) {
                // Ignore errors reading local.properties
            }
        }

        return null
    }

    /**
     * Find local.properties file in current or parent directories
     */
    private fun findLocalProperties(): String? {
        var currentDir = File(System.getProperty("user.dir"))

        repeat(5) { // Check up to 5 parent directories
            val localProperties = File(currentDir, "local.properties")
            if (localProperties.exists()) {
                return localProperties.absolutePath
            }
            currentDir = currentDir.parentFile ?: return null
        }

        return null
    }

    /**
     * Validate if ADB is accessible at the given path
     */
    fun validateAdbPath(path: String): Boolean {
        return try {
            val adbFile = File(path)
            adbFile.exists() && adbFile.canExecute()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get SDK detection status message
     */
    fun getSdkDetectionStatus(): String {
        val currentPath = getAdbPath()
        return when {
            useCustomAdbPath && customAdbPath.isNotEmpty() -> {
                if (validateAdbPath(currentPath)) {
                    "✅ Using custom ADB path: $currentPath"
                } else {
                    "❌ Custom ADB path not found: $currentPath"
                }
            }
            androidSdkPath.isNotEmpty() -> {
                if (validateAdbPath(currentPath)) {
                    "✅ Using configured SDK: $androidSdkPath"
                } else {
                    "❌ ADB not found in configured SDK: $androidSdkPath"
                }
            }
            else -> {
                val detectedSdk = detectAndroidSdkPath()
                if (detectedSdk != null) {
                    "✅ Auto-detected SDK: $detectedSdk"
                } else {
                    "⚠️ Using system PATH (adb command must be available)"
                }
            }
        }
    }

    /**
     * Check if ADB is properly configured
     */
    fun isAdbConfigured(): Boolean {
        val adbPath = getAdbPath()
        return validateAdbPath(adbPath)
    }
}