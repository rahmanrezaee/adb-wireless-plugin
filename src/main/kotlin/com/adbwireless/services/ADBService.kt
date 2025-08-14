package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * ADB Service with dynamic path configuration
 */
@Service(Service.Level.PROJECT)
class ADBService(private val project: Project) {

    // Use IntelliJ's logger instead of println
    private val logger = thisLogger()
    private val settingsService = SettingsService.getInstance()

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String = "",
        val exitCode: Int = -1,
        val fullOutput: String = ""
    )

    /**
     * Get the current ADB path from settings
     */
    private fun getAdbPath(): String {
        return settingsService.getAdbPath()
    }

    /**
     * Execute ADB command with dynamic path resolution
     */
    fun executeAdbCommand(vararg args: String): CommandResult {
        return try {
            val adbPath = getAdbPath()

            // Validate ADB path before executing
            if (!settingsService.validateAdbPath(adbPath)) {
                logger.warn("[ADB] ADB not found at configured path: $adbPath")
                return CommandResult(
                    success = false,
                    output = "",
                    error = "ADB not found at: $adbPath. Please check your SDK configuration in Settings.",
                    fullOutput = "ADB_NOT_FOUND: $adbPath"
                )
            }

            val commandLine = GeneralCommandLine().apply {
                exePath = adbPath
                addParameters(*args)
                charset = StandardCharsets.UTF_8
            }

            // Use IntelliJ logger - this will appear in IDE logs
            logger.info("[ADB] Using ADB path: $adbPath")
            logger.info("[ADB] Executing: ${commandLine.commandLineString}")

            val processOutput = CapturingProcessHandler(commandLine).runProcess(30000)

            val success = processOutput.exitCode == 0
            val outputStr = processOutput.stdout.trim()
            val errorStr = processOutput.stderr.trim()

            logger.info("[ADB] Exit code: ${processOutput.exitCode}")
            logger.info("[ADB] Output: '$outputStr'")
            if (errorStr.isNotEmpty()) {
                logger.warn("[ADB] Error: '$errorStr'")
            }

            // Check if we need to start ADB server
            if (!success && errorStr.contains("cannot connect to daemon")) {
                logger.info("[ADB] ADB server not running, attempting to start...")
                return startServerAndRetry(*args)
            }

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = processOutput.exitCode,
                fullOutput = "EXIT: ${processOutput.exitCode}\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"
            )

        } catch (e: Exception) {
            logger.error("[ADB] Exception: ${e.message}", e)
            CommandResult(
                success = false,
                output = "",
                error = "Exception: ${e.message}",
                fullOutput = "EXCEPTION: ${e.message}"
            )
        }
    }

    /**
     * Start ADB server and retry the command
     */
    private fun startServerAndRetry(vararg originalArgs: String): CommandResult {
        logger.info("[ADB] Starting ADB server...")

        val startResult = startAdbServer()
        if (!startResult.success) {
            logger.error("[ADB] Failed to start server: ${startResult.error}")
            return CommandResult(
                success = false,
                output = "",
                error = "Failed to start ADB server: ${startResult.error}",
                fullOutput = "SERVER_START_FAILED: ${startResult.fullOutput}"
            )
        }

        logger.info("[ADB] ADB server started, retrying original command...")
        Thread.sleep(2000)

        return try {
            val adbPath = getAdbPath()
            val commandLine = GeneralCommandLine().apply {
                exePath = adbPath
                addParameters(*originalArgs)
                charset = StandardCharsets.UTF_8
            }

            val processOutput = CapturingProcessHandler(commandLine).runProcess(30000)

            val success = processOutput.exitCode == 0
            val outputStr = processOutput.stdout.trim()
            val errorStr = processOutput.stderr.trim()

            logger.info("[ADB] Retry result - Exit code: ${processOutput.exitCode}")

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = processOutput.exitCode,
                fullOutput = "EXIT: ${processOutput.exitCode}\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"
            )

        } catch (e: Exception) {
            logger.error("[ADB] Retry failed: ${e.message}", e)
            CommandResult(
                success = false,
                output = "",
                error = "Retry failed: ${e.message}",
                fullOutput = "RETRY_EXCEPTION: ${e.message}"
            )
        }
    }

    /**
     * Start ADB server explicitly
     */
    private fun startAdbServer(): CommandResult {
        return try {
            val adbPath = getAdbPath()
            val commandLine = GeneralCommandLine().apply {
                exePath = adbPath
                addParameters("start-server")
                charset = StandardCharsets.UTF_8
            }

            logger.info("[ADB] Starting server: ${commandLine.commandLineString}")
            val processOutput = CapturingProcessHandler(commandLine).runProcess(15000)

            val success = processOutput.exitCode == 0
            val outputStr = processOutput.stdout.trim()
            val errorStr = processOutput.stderr.trim()

            logger.info("[ADB] Server start - Exit code: ${processOutput.exitCode}")
            logger.info("[ADB] Server start - Output: '$outputStr'")
            if (errorStr.isNotEmpty()) {
                logger.warn("[ADB] Server start - Error: '$errorStr'")
            }

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = processOutput.exitCode,
                fullOutput = "EXIT: ${processOutput.exitCode}\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"
            )

        } catch (e: Exception) {
            logger.error("[ADB] Server start exception: ${e.message}", e)
            CommandResult(
                success = false,
                output = "",
                error = "Server start exception: ${e.message}",
                fullOutput = "SERVER_START_EXCEPTION: ${e.message}"
            )
        }
    }

    /**
     * Pair device with automatic server handling
     */
    fun pairDevice(ip: String, port: String, code: String): CommandResult {
        logger.info("[ADB] Starting pairing: $ip:$port with code: $code")

        return try {
            val adbPath = getAdbPath()

            // Validate ADB path before pairing
            if (!settingsService.validateAdbPath(adbPath)) {
                return CommandResult(
                    success = false,
                    output = "",
                    error = "ADB not found at: $adbPath. Please configure your Android SDK in Settings.",
                    fullOutput = "ADB_NOT_FOUND_FOR_PAIRING: $adbPath"
                )
            }

            val commandLine = GeneralCommandLine().apply {
                exePath = adbPath
                addParameters("pair", "$ip:$port", code)
                charset = StandardCharsets.UTF_8
            }

            logger.info("[ADB] Executing pairing: ${commandLine.commandLineString}")

            val processOutput = CapturingProcessHandler(commandLine).runProcess(20000)

            val outputStr = processOutput.stdout.trim()
            val errorStr = processOutput.stderr.trim()
            val exitCode = processOutput.exitCode

            logger.info("[ADB] Pairing completed - Exit code: $exitCode")
            logger.info("[ADB] Pairing output: '$outputStr'")
            if (errorStr.isNotEmpty()) {
                logger.warn("[ADB] Pairing error: '$errorStr'")
            }

            // Check if we need to start server and retry
            if (exitCode != 0 && errorStr.contains("cannot connect to daemon")) {
                logger.info("[ADB] Server not running for pairing, starting and retrying...")
                return startServerAndRetryPairing(ip, port, code)
            }

            // Enhanced success detection
            val combinedOutput = "$outputStr $errorStr".lowercase()
            val success = exitCode == 0 && (
                    combinedOutput.contains("successfully paired") ||
                            combinedOutput.contains("paired to") ||
                            (outputStr.isNotEmpty() && !combinedOutput.contains("failed") && !combinedOutput.contains("error"))
                    )

            logger.info("[ADB] Final pairing result - Success: $success")

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = exitCode,
                fullOutput = "EXIT: $exitCode\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"
            )

        } catch (e: Exception) {
            logger.error("[ADB] Pairing exception: ${e.message}", e)
            CommandResult(
                success = false,
                output = "",
                error = "Pairing exception: ${e.message}",
                fullOutput = "EXCEPTION: ${e.message}"
            )
        }
    }

    private fun startServerAndRetryPairing(ip: String, port: String, code: String): CommandResult {
        logger.info("[ADB] Starting server for pairing retry...")

        val startResult = startAdbServer()
        if (!startResult.success) {
            logger.error("[ADB] Failed to start server for pairing: ${startResult.error}")
            return CommandResult(
                success = false,
                output = "",
                error = "Failed to start ADB server for pairing: ${startResult.error}",
                fullOutput = "PAIRING_SERVER_START_FAILED: ${startResult.fullOutput}"
            )
        }

        Thread.sleep(3000)

        return try {
            val adbPath = getAdbPath()
            val commandLine = GeneralCommandLine().apply {
                exePath = adbPath
                addParameters("pair", "$ip:$port", code)
                charset = StandardCharsets.UTF_8
            }

            logger.info("[ADB] Retrying pairing: ${commandLine.commandLineString}")
            val processOutput = CapturingProcessHandler(commandLine).runProcess(20000)

            val outputStr = processOutput.stdout.trim()
            val errorStr = processOutput.stderr.trim()
            val exitCode = processOutput.exitCode

            val combinedOutput = "$outputStr $errorStr".lowercase()
            val success = exitCode == 0 && (
                    combinedOutput.contains("successfully paired") ||
                            combinedOutput.contains("paired to") ||
                            (outputStr.isNotEmpty() && !combinedOutput.contains("failed") && !combinedOutput.contains("error"))
                    )

            logger.info("[ADB] Pairing retry result - Success: $success")

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = exitCode,
                fullOutput = "EXIT: $exitCode\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"
            )

        } catch (e: Exception) {
            logger.error("[ADB] Pairing retry failed: ${e.message}", e)
            CommandResult(
                success = false,
                output = "",
                error = "Pairing retry failed: ${e.message}",
                fullOutput = "PAIRING_RETRY_EXCEPTION: ${e.message}"
            )
        }
    }

    // Rest of the methods with dynamic path support
    fun connectDevice(device: Device): CommandResult {
        logger.info("[ADB] Connecting to device: ${device.getConnectAddress()}")
        return executeAdbCommand("connect", device.getConnectAddress())
    }

    fun disconnectDevice(device: Device): CommandResult {
        logger.info("[ADB] Disconnecting from device: ${device.getConnectAddress()}")
        return executeAdbCommand("disconnect", device.getConnectAddress())
    }

    fun listDevices(): CommandResult {
        logger.info("[ADB] Listing connected devices")
        return executeAdbCommand("devices", "-l")
    }

    fun checkAdbAvailability(): CommandResult {
        val adbPath = getAdbPath()
        if (!settingsService.validateAdbPath(adbPath)) {
            logger.warn("[ADB] ADB not found at: $adbPath")
            return CommandResult(
                success = false,
                output = "",
                error = "ADB not found at: $adbPath. Please configure your Android SDK in Settings → Tools → ADB Wireless Manager",
                fullOutput = "ADB_NOT_AVAILABLE: $adbPath"
            )
        }
        logger.info("[ADB] Checking ADB availability at: $adbPath")
        return executeAdbCommand("version")
    }

    fun restartAdbServer(): CommandResult {
        logger.info("[ADB] Restarting ADB server...")
        val killResult = executeAdbCommand("kill-server")
        if (killResult.success) {
            Thread.sleep(2000)
            return startAdbServer()
        }
        return killResult
    }

    fun clearDeviceConnections(ipAddress: String): CommandResult {
        logger.info("[ADB] Clearing existing connections to $ipAddress")

        val devicesResult = listDevices()
        if (!devicesResult.success) {
            return devicesResult
        }

        val lines = devicesResult.output.lines()
        val connectionsToDisconnect = lines
            .filter { it.contains(ipAddress) && it.contains("device") }
            .mapNotNull { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.isNotEmpty()) parts[0] else null
            }

        if (connectionsToDisconnect.isEmpty()) {
            logger.info("[ADB] No existing connections to disconnect for $ipAddress")
            return CommandResult(true, "No existing connections to disconnect", "")
        }

        var allSuccess = true
        val results = mutableListOf<String>()

        for (address in connectionsToDisconnect) {
            logger.info("[ADB] Disconnecting existing connection: $address")
            val disconnectResult = executeAdbCommand("disconnect", address)
            results.add("Disconnect $address: ${if (disconnectResult.success) "OK" else "FAILED"}")
            if (!disconnectResult.success) {
                allSuccess = false
            }
        }

        return CommandResult(
            success = allSuccess,
            output = results.joinToString("\n"),
            error = if (allSuccess) "" else "Some disconnections failed"
        )
    }

    /**
     * Get SDK detection status for display purposes
     */
    fun getSdkStatus(): String {
        return settingsService.getSdkDetectionStatus()
    }
}