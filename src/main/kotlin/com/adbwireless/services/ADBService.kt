package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Enhanced ADB Service with better timeout and error handling
 */
@Service(Service.Level.PROJECT)
class ADBService(private val project: Project) {

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String = "",
        val exitCode: Int = -1,
        val command: String = "",
        val timedOut: Boolean = false
    )

    private var cachedAdbPath: String? = null
    private val debugMode = true

    private fun log(message: String) {
        if (debugMode) {
            println("[ADB-DEBUG] $message")
        }
    }

    /**
     * Find ADB executable with extensive debugging
     */
    private fun getAdbPath(): String? {
        log("Starting ADB path detection...")

        cachedAdbPath?.let {
            log("Using cached ADB path: $it")
            return it
        }

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val adbExecutable = if (isWindows) "adb.exe" else "adb"

        log("Operating System: ${System.getProperty("os.name")}")
        log("Looking for executable: $adbExecutable")

        // Check your specific path first
        val specificPaths = arrayOf(
            "D:\\Sdk\\platform-tools\\adb.exe",
            "D:/Sdk/platform-tools/adb.exe"
        )

        log("Checking your specific paths...")
        for (path in specificPaths) {
            log("Checking: $path")
            val file = File(path)
            log("  - Exists: ${file.exists()}")
            log("  - Is File: ${file.isFile}")
            log("  - Can Execute: ${file.canExecute()}")

            if (file.exists() && file.canExecute()) {
                cachedAdbPath = file.absolutePath
                log("✅ Found ADB at: $cachedAdbPath")
                return cachedAdbPath
            }
        }

        // Common paths
        val commonPaths = when {
            isWindows -> arrayOf(
                "C:\\Android\\Sdk\\platform-tools\\adb.exe",
                "${System.getProperty("user.home")}\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe",
                "${System.getProperty("user.home")}\\Android\\Sdk\\platform-tools\\adb.exe"
            )
            System.getProperty("os.name").lowercase().contains("mac") -> arrayOf(
                "${System.getProperty("user.home")}/Library/Android/sdk/platform-tools/adb",
                "/usr/local/bin/adb",
                "/opt/homebrew/bin/adb"
            )
            else -> arrayOf(
                "/usr/local/bin/adb",
                "/usr/bin/adb",
                "${System.getProperty("user.home")}/Android/Sdk/platform-tools/adb"
            )
        }

        log("Checking common paths...")
        for (path in commonPaths) {
            log("Checking: $path")
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                cachedAdbPath = file.absolutePath
                log("✅ Found ADB at: $cachedAdbPath")
                return cachedAdbPath
            }
        }

        // Try PATH
        log("Checking system PATH...")
        try {
            val whichCommand = if (isWindows) "where" else "which"
            val process = ProcessBuilder(whichCommand, adbExecutable)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().use { it.readText().trim() }
            val completed = process.waitFor(5, TimeUnit.SECONDS)

            if (completed && result.isNotEmpty()) {
                val adbPath = result.split('\n').first().trim()
                if (File(adbPath).exists()) {
                    cachedAdbPath = adbPath
                    log("✅ Found ADB in PATH: $cachedAdbPath")
                    return cachedAdbPath
                }
            }
        } catch (e: Exception) {
            log("❌ Error checking PATH: ${e.message}")
        }

        log("❌ ADB not found in any location")
        return null
    }

    /**
     * Execute ADB command with improved timeout handling
     */
    suspend fun executeAdbCommand(vararg args: String, timeoutSeconds: Long = 30): CommandResult = withContext(Dispatchers.IO) {
        val adbPath = getAdbPath()
        if (adbPath == null) {
            val error = "ADB executable not found"
            log("❌ $error")
            return@withContext CommandResult(false, "", error, -1, "")
        }

        val command = listOf(adbPath) + args.toList()
        val commandStr = command.joinToString(" ")

        log("🚀 Executing: $commandStr")
        log("Timeout: ${timeoutSeconds}s")

        var process: Process? = null
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(false)

            process = processBuilder.start()
            log("📤 Process started with PID: ${process.pid()}")

            val output = StringBuilder()
            val error = StringBuilder()

            // Create jobs to read streams
            val outputJob = async {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            output.appendLine(line)
                            log("OUT: $line")
                        }
                    }
                } catch (e: Exception) {
                    log("Error reading output: ${e.message}")
                }
            }

            val errorJob = async {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            error.appendLine(line)
                            log("ERR: $line")
                        }
                    }
                } catch (e: Exception) {
                    log("Error reading error stream: ${e.message}")
                }
            }

            // Wait for completion with timeout
            log("⏳ Waiting for process completion...")
            val completed = withTimeoutOrNull(timeoutSeconds * 1000) {
                process.waitFor()
                true
            }

            if (completed == null) {
                log("⏰ Process timed out after ${timeoutSeconds}s")
                process.destroyForcibly()

                // Wait a bit more for cleanup
                withTimeoutOrNull(5000) {
                    process.waitFor()
                }

                // Cancel reading jobs
                outputJob.cancel()
                errorJob.cancel()

                return@withContext CommandResult(
                    false,
                    output.toString().trim(),
                    "Command timed out after ${timeoutSeconds}s",
                    -1,
                    commandStr,
                    timedOut = true
                )
            }

            // Process completed, wait for stream reading
            log("📥 Process completed, reading streams...")
            try {
                withTimeout(5000) { // Max 5 seconds to read streams
                    outputJob.await()
                    errorJob.await()
                }
            } catch (e: TimeoutCancellationException) {
                log("⚠️ Stream reading timed out")
                outputJob.cancel()
                errorJob.cancel()
            }

            val exitCode = process.exitValue()
            val success = exitCode == 0
            val outputStr = output.toString().trim()
            val errorStr = error.toString().trim()

            log("📊 Command completed:")
            log("  - Exit code: $exitCode")
            log("  - Success: $success")
            log("  - Output: '$outputStr'")
            log("  - Error: '$errorStr'")

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = exitCode,
                command = commandStr,
                timedOut = false
            )

        } catch (e: Exception) {
            val errorMsg = "Exception executing ADB: ${e.message}"
            log("💥 $errorMsg")
            e.printStackTrace()

            // Try to cleanup process
            try {
                process?.destroyForcibly()
            } catch (ex: Exception) {
                log("Error cleaning up process: ${ex.message}")
            }

            CommandResult(false, "", errorMsg, -1, commandStr)
        }
    }

    /**
     * Enhanced pairing with shorter timeout and better error detection
     */
    suspend fun pairDevice(ip: String, port: String, code: String): CommandResult {
        log("🔗 Starting device pairing...")
        log("  - IP: $ip")
        log("  - Port: $port")
        log("  - Code: $code")
        log("  - Full address: $ip:$port")

        // Validate inputs
        if (ip.isBlank()) {
            return CommandResult(false, "", "IP address cannot be empty", -1, "")
        }
        if (port.isBlank()) {
            return CommandResult(false, "", "Port cannot be empty", -1, "")
        }
        if (code.isBlank()) {
            return CommandResult(false, "", "Pairing code cannot be empty", -1, "")
        }
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return CommandResult(false, "", "Pairing code must be exactly 6 digits", -1, "")
        }

        // Use shorter timeout for pairing (30 seconds instead of 90)
        log("🚀 Executing pairing command with 30s timeout...")
        val result = executeAdbCommand("pair", "$ip:$port", code, timeoutSeconds = 30)

        log("📱 Pairing result:")
        log("  - Success: ${result.success}")
        log("  - Exit code: ${result.exitCode}")
        log("  - Timed out: ${result.timedOut}")
        log("  - Output: '${result.output}'")
        log("  - Error: '${result.error}'")

        return result
    }

    /**
     * Test ADB with very short timeout
     */
    suspend fun quickAdbTest(): CommandResult {
        log("🧪 Quick ADB test...")
        val result = executeAdbCommand("version", timeoutSeconds = 10)
        log("Quick test result: success=${result.success}, timedOut=${result.timedOut}")
        return result
    }

    /**
     * Connect to device
     */
    suspend fun connectDevice(device: Device): CommandResult {
        log("🔌 Connecting to ${device.getConnectAddress()}")
        return executeAdbCommand("connect", device.getConnectAddress(), timeoutSeconds = 20)
    }

    /**
     * Kill and restart ADB server
     */
    suspend fun restartAdbServer(): CommandResult {
        log("🔄 Restarting ADB server...")

        // First kill the server
        log("Killing ADB server...")
        val killResult = executeAdbCommand("kill-server", timeoutSeconds = 10)
        log("Kill result: success=${killResult.success}")

        if (!killResult.success && !killResult.output.contains("killed")) {
            return CommandResult(false, killResult.output, "Failed to kill ADB server: ${killResult.error}")
        }

        // Wait a moment
        delay(2000)

        // Start the server
        log("Starting ADB server...")
        val startResult = executeAdbCommand("start-server", timeoutSeconds = 15)
        log("Start result: success=${startResult.success}")

        return if (startResult.success) {
            CommandResult(true, "ADB server restarted successfully", "")
        } else {
            CommandResult(false, startResult.output, "Failed to start ADB server: ${startResult.error}")
        }
    }

    // Other methods with shorter timeouts
    suspend fun disconnectDevice(device: Device): CommandResult {
        return executeAdbCommand("disconnect", device.getConnectAddress(), timeoutSeconds = 10)
    }

    suspend fun listDevices(): CommandResult {
        return executeAdbCommand("devices", "-l", timeoutSeconds = 10)
    }

    suspend fun disconnectAll(): CommandResult {
        return executeAdbCommand("disconnect", timeoutSeconds = 15)
    }

    suspend fun checkAdbAvailability(): CommandResult {
        return executeAdbCommand("version", timeoutSeconds = 5)
    }

    fun getAdbPathForDisplay(): String = getAdbPath() ?: "ADB not found"

    suspend fun testAdbConnectivity(): CommandResult {
        val adbPath = getAdbPath()
        if (adbPath == null) {
            return CommandResult(
                false,
                "",
                "ADB executable not found. Please install Android SDK Platform Tools."
            )
        }

        val versionResult = executeAdbCommand("version", timeoutSeconds = 10)
        if (!versionResult.success) {
            return CommandResult(
                false,
                "",
                "ADB found but not working: ${versionResult.error}"
            )
        }

        val devicesResult = executeAdbCommand("devices", timeoutSeconds = 10)
        return CommandResult(
            success = true,
            output = "ADB is working correctly.\nPath: $adbPath\nVersion: ${versionResult.output}\n\nDevices: ${devicesResult.output}",
            error = ""
        )
    }
}