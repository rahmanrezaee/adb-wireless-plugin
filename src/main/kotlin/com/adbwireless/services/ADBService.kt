package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Service for executing ADB commands and managing wireless connections
 * PROJECT-LEVEL SERVICE - One instance per project
 */
@Service(Service.Level.PROJECT)  // ✅ Explicit project-level declaration
class ADBService(private val project: Project) {

    /**
     * Result of an ADB command execution
     */
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String = ""
    )

    /**
     * Find ADB executable path from common locations
     * No longer depends on Android Studio plugin - uses direct path detection
     */
    private fun getAdbPath(): String? {
        // Common ADB installation paths for different platforms
        val commonPaths = when {
            System.getProperty("os.name").lowercase().contains("windows") -> arrayOf(
                "D:/Sdk/platform-tools/adb.exe", // Your specific path
                "C:/Android/Sdk/platform-tools/adb.exe",
                System.getProperty("user.home") + "/AppData/Local/Android/Sdk/platform-tools/adb.exe",
                System.getProperty("user.home") + "/Android/Sdk/platform-tools/adb.exe",
                "C:/Users/" + System.getProperty("user.name") + "/AppData/Local/Android/Sdk/platform-tools/adb.exe"
            )
            System.getProperty("os.name").lowercase().contains("mac") -> arrayOf(
                System.getProperty("user.home") + "/Library/Android/sdk/platform-tools/adb",
                "/usr/local/bin/adb",
                "/opt/homebrew/bin/adb",
                System.getProperty("user.home") + "/Android/Sdk/platform-tools/adb"
            )
            else -> arrayOf( // Linux and others
                "/usr/local/bin/adb",
                "/usr/bin/adb",
                "/opt/android-sdk/platform-tools/adb",
                System.getProperty("user.home") + "/Android/Sdk/platform-tools/adb",
                System.getProperty("user.home") + "/android-sdk/platform-tools/adb"
            )
        }

        // Check each path to find working ADB installation
        for (path in commonPaths) {
            val adbFile = File(path)
            if (adbFile.exists() && adbFile.canExecute()) {
                return adbFile.absolutePath
            }
        }

        // Try to find ADB in system PATH
        try {
            val adbCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "adb.exe" else "adb"
            val process = ProcessBuilder("which", adbCommand).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotEmpty() && File(result).exists()) {
                return result
            }
        } catch (e: Exception) {
            // Ignore - will try other methods
        }

        return null
    }

    /**
     * Execute ADB command asynchronously
     */
    suspend fun executeAdbCommand(vararg args: String): CommandResult = withContext(Dispatchers.IO) {
        val adbPath = getAdbPath()
            ?: return@withContext CommandResult(false, "", "ADB executable not found. Please ensure Android SDK is installed and ADB is in your PATH.")

        try {
            val command = listOf(adbPath) + args.toList()
            val processBuilder = ProcessBuilder(command)
            val process = processBuilder.start()

            val output = StringBuilder()
            val error = StringBuilder()

            // Read output stream
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            // Read all output
            outputReader.use { reader ->
                reader.lineSequence().forEach { line ->
                    output.appendLine(line)
                }
            }

            // Read all error output
            errorReader.use { reader ->
                reader.lineSequence().forEach { line ->
                    error.appendLine(line)
                }
            }

            val exitCode = process.waitFor()
            val success = exitCode == 0

            CommandResult(
                success = success,
                output = output.toString().trim(),
                error = error.toString().trim()
            )

        } catch (e: Exception) {
            CommandResult(false, "", "Exception executing ADB command: ${e.message}")
        }
    }

    /**
     * Pair with Android device using pairing code
     */
    suspend fun pairDevice(ip: String, port: String, code: String): CommandResult {
        return executeAdbCommand("pair", "$ip:$port", code)
    }

    /**
     * Connect to paired Android device
     */
    suspend fun connectDevice(device: Device): CommandResult {
        return executeAdbCommand("connect", device.getConnectAddress())
    }

    /**
     * Disconnect from specific device
     */
    suspend fun disconnectDevice(device: Device): CommandResult {
        return executeAdbCommand("disconnect", device.getConnectAddress())
    }

    /**
     * List all connected devices
     */
    suspend fun listDevices(): CommandResult {
        return executeAdbCommand("devices")
    }

    /**
     * Disconnect from all devices
     */
    suspend fun disconnectAll(): CommandResult {
        return executeAdbCommand("disconnect")
    }

    /**
     * Check if ADB is available and working
     */
    suspend fun checkAdbAvailability(): CommandResult {
        return executeAdbCommand("version")
    }

    /**
     * Get the ADB path for display purposes
     */
    fun getAdbPathForDisplay(): String {
        return getAdbPath() ?: "ADB not found"
    }

    /**
     * Test ADB connectivity and return detailed status
     */
    suspend fun testAdbConnectivity(): CommandResult {
        val adbPath = getAdbPath()
        if (adbPath == null) {
            return CommandResult(false, "", "ADB executable not found. Checked paths: D:\\Sdk\\platform-tools\\adb.exe and other common locations.")
        }
        
        // Test basic ADB functionality
        val versionResult = executeAdbCommand("version")
        if (!versionResult.success) {
            return CommandResult(false, "", "ADB found but not working: ${versionResult.error}")
        }
        
        // Test device listing
        val devicesResult = executeAdbCommand("devices")
        return CommandResult(
            success = true,
            output = "ADB is working correctly.\nPath: $adbPath\nVersion: ${versionResult.output}\nDevices: ${devicesResult.output}",
            error = ""
        )
    }
}