package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Simplified ADB Service to fix ClassNotFoundException and hanging issues
 */
@Service(Service.Level.PROJECT)
class ADBService(private val project: Project) {

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String = "",
        val exitCode: Int = -1,
        val fullOutput: String = ""
    )

    private val adbPath = "D:\\Sdk\\platform-tools\\adb.exe"

    /**
     * Execute ADB command using simplified approach
     */
    suspend fun executeAdbCommand(vararg args: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val commandLine = GeneralCommandLine().apply {
                exePath = adbPath
                addParameters(*args)
                charset = StandardCharsets.UTF_8
                withEnvironment("ADB_SERVER_SOCKET", "tcp:127.0.0.1:5037")
            }

            println("[ADB] Executing: ${commandLine.commandLineString}")

            val processOutput = executeCommandSimple(commandLine, 30000)

            val success = processOutput.exitCode == 0
            val outputStr = processOutput.stdout.trim()
            val errorStr = processOutput.stderr.trim()

            println("[ADB] Exit code: ${processOutput.exitCode}")
            println("[ADB] Output: '$outputStr'")
            println("[ADB] Error: '$errorStr'")

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = processOutput.exitCode,
                fullOutput = "EXIT: ${processOutput.exitCode}\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"
            )

        } catch (e: Exception) {
            println("[ADB] Exception: ${e.message}")
            e.printStackTrace()
            CommandResult(
                success = false,
                output = "",
                error = "Exception: ${e.message}",
                fullOutput = "EXCEPTION: ${e.message}"
            )
        }
    }

    /**
     * FIXED: Simplified pairing using blocking approach to avoid coroutine issues
     */
    suspend fun pairDevice(ip: String, port: String, code: String): CommandResult = withContext(Dispatchers.IO) {
        println("[ADB] Starting pairing: $ip:$port with code: $code")

        try {
            val commandLine = GeneralCommandLine().apply {
                exePath = adbPath
                addParameters("pair", "$ip:$port", code)
                charset = StandardCharsets.UTF_8
                withEnvironment("ADB_SERVER_SOCKET", "tcp:127.0.0.1:5037")
            }

            println("[ADB] Executing: ${commandLine.commandLineString}")

            // Use simplified execution method
            val processOutput = executeCommandSimple(commandLine, 20000)

            val outputStr = processOutput.stdout.trim()
            val errorStr = processOutput.stderr.trim()
            val exitCode = processOutput.exitCode

            println("[ADB] Pairing completed - Exit code: $exitCode")
            println("[ADB] Pairing output: '$outputStr'")
            println("[ADB] Pairing error: '$errorStr'")

            // Enhanced success detection
            val combinedOutput = "$outputStr $errorStr".lowercase()
            val success = exitCode == 0 && (
                    combinedOutput.contains("successfully paired") ||
                            combinedOutput.contains("paired to") ||
                            (outputStr.isNotEmpty() && !combinedOutput.contains("failed") && !combinedOutput.contains("error"))
                    )

            println("[ADB] Final pairing result - Success: $success")

            CommandResult(
                success = success,
                output = outputStr,
                error = errorStr,
                exitCode = exitCode,
                fullOutput = "EXIT: $exitCode\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"
            )

        } catch (e: Exception) {
            println("[ADB] Pairing exception: ${e.message}")
            e.printStackTrace()
            CommandResult(
                success = false,
                output = "",
                error = "Pairing exception: ${e.message}",
                fullOutput = "EXCEPTION: ${e.message}"
            )
        }
    }

    /**
     * Simplified command execution using CompletableFuture to avoid coroutine issues
     */
    private fun executeCommandSimple(commandLine: GeneralCommandLine, timeoutMs: Long): ProcessOutput {
        return try {
            val handler = CapturingProcessHandler(
                commandLine.createProcess(),
                commandLine.charset,
                commandLine.commandLineString
            )

            println("[ADB] Process handler created, starting...")

            // Use CompletableFuture instead of coroutines to avoid ClassNotFoundException
            val future = CompletableFuture<ProcessOutput>()

            handler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    println("[ADB] Process terminated with exit code: ${event.exitCode}")
                    try {
                        val output = handler.runProcess(2000) // 2 seconds for final output collection
                        println("[ADB] Got process output: stdout='${output.stdout}', stderr='${output.stderr}'")
                        future.complete(output)
                    } catch (e: Exception) {
                        println("[ADB] Error getting process output: ${e.message}")
                        val emptyOutput = ProcessOutput().apply {
                            exitCode = event.exitCode
                            appendStderr("Error collecting output: ${e.message}")
                        }
                        future.complete(emptyOutput)
                    }
                }
            })

            handler.startNotify()
            println("[ADB] Process started, waiting for completion...")

            // Wait for completion with timeout
            try {
                val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                println("[ADB] Process completed successfully")
                result
            } catch (e: java.util.concurrent.TimeoutException) {
                println("[ADB] Process timed out after ${timeoutMs}ms")
                handler.destroyProcess()

                // Return timeout result
                ProcessOutput().apply {
                    appendStderr("Process timed out after ${timeoutMs}ms")
                    exitCode = -1
                }
            } catch (e: Exception) {
                println("[ADB] Error waiting for process: ${e.message}")
                handler.destroyProcess()

                ProcessOutput().apply {
                    appendStderr("Error: ${e.message}")
                    exitCode = -1
                }
            }

        } catch (e: Exception) {
            println("[ADB] Error creating process: ${e.message}")
            e.printStackTrace()

            ProcessOutput().apply {
                appendStderr("Failed to create process: ${e.message}")
                exitCode = -1
            }
        }
    }

    /**
     * Connect to device
     */
    suspend fun connectDevice(device: Device): CommandResult {
        return executeAdbCommand("connect", device.getConnectAddress())
    }

    /**
     * Disconnect from device
     */
    suspend fun disconnectDevice(device: Device): CommandResult {
        return executeAdbCommand("disconnect", device.getConnectAddress())
    }

    /**
     * List connected devices
     */
    suspend fun listDevices(): CommandResult {
        return executeAdbCommand("devices", "-l")
    }

    /**
     * Check if ADB is available
     */
    suspend fun checkAdbAvailability(): CommandResult {
        val adbFile = File(adbPath)
        if (!adbFile.exists()) {
            return CommandResult(false, "", "ADB not found at: $adbPath")
        }
        return executeAdbCommand("version")
    }

    /**
     * Restart ADB server
     */
    suspend fun restartAdbServer(): CommandResult {
        println("[ADB] Restarting ADB server...")
        val killResult = executeAdbCommand("kill-server")
        if (killResult.success) {
            delay(2000) // Wait for server to shut down
            return executeAdbCommand("start-server")
        }
        return killResult
    }

    /**
     * Clear existing connections to a device
     */
    suspend fun clearDeviceConnections(ipAddress: String): CommandResult {
        println("[ADB] Clearing existing connections to $ipAddress")

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
            return CommandResult(true, "No existing connections to disconnect", "")
        }

        var allSuccess = true
        val results = mutableListOf<String>()

        for (address in connectionsToDisconnect) {
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
}