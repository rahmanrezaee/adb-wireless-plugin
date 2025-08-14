package com.adbwireless.services

import com.adbwireless.models.Device
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Simple ADB Service - Direct commands only
 */
@Service(Service.Level.PROJECT)
class ADBService(private val project: Project) {

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String = "",
        val fullOutput: String = "" // Full combined output for debugging
    )

    private val adbPath = "D:\\Sdk\\platform-tools\\adb.exe"

    /**
     * Execute ADB command - Simple and direct with timeout
     */
    suspend fun executeAdbCommand(vararg args: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val command = listOf(adbPath) + args.toList()

            println("[ADB] Executing: ${command.joinToString(" ")}")

            // Use ProcessBuilder with explicit timeout handling
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(false)
            val process = processBuilder.start()

            // Read output and error streams
            val output = StringBuilder()
            val error = StringBuilder()

            // Start reading streams in background
            val outputJob = async {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        output.appendLine(line)
                        println("[ADB-OUT] $line")
                    }
                }
            }

            val errorJob = async {
                process.errorStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        error.appendLine(line)
                        println("[ADB-ERR] $line")
                    }
                }
            }

            // Wait for process with timeout
            val completed = withTimeoutOrNull(45000) { // 45 seconds timeout
                process.waitFor()
                true
            }

            if (completed == null) {
                println("[ADB] Process timed out, destroying...")
                process.destroyForcibly()
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                outputJob.cancel()
                errorJob.cancel()

                return@withContext CommandResult(
                    success = false,
                    output = output.toString().trim(),
                    error = "Command timed out after 45 seconds",
                    fullOutput = "TIMEOUT: ${output}\nERROR: ${error}"
                )
            }

            // Wait for stream reading to complete
            outputJob.await()
            errorJob.await()

            val exitCode = process.exitValue()
            val outputStr = output.toString().trim()
            val errorStr = error.toString().trim()
            val fullOutput = "EXIT_CODE: $exitCode\nSTDOUT:\n$outputStr\nSTDERR:\n$errorStr"

            println("[ADB] Exit code: $exitCode")
            println("[ADB] Output: '$outputStr'")
            println("[ADB] Error: '$errorStr'")

            CommandResult(
                success = exitCode == 0,
                output = outputStr,
                error = errorStr,
                fullOutput = fullOutput
            )

        } catch (e: Exception) {
            println("[ADB] Exception: ${e.message}")
            e.printStackTrace()
            CommandResult(
                success = false,
                output = "",
                error = "Exception: ${e.message}",
                fullOutput = "Exception: ${e.message}"
            )
        }
    }

    /**
     * Pair device - Handle interactive input properly
     */
    suspend fun pairDevice(ip: String, port: String, code: String): CommandResult {
        println("[ADB] Pairing device: $ip:$port with code: $code")

        return withContext(Dispatchers.IO) {
            try {
                val command = listOf(adbPath, "pair", "$ip:$port")
                println("[ADB] Executing: ${command.joinToString(" ")}")

                val processBuilder = ProcessBuilder(command)
                val process = processBuilder.start()

                // Send the pairing code to stdin
                process.outputStream.use { outputStream ->
                    outputStream.write("$code\n".toByteArray())
                    outputStream.flush()
                }

                val output = StringBuilder()
                val error = StringBuilder()

                // Read streams
                val outputJob = async {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            output.appendLine(line)
                            println("[ADB-OUT] $line")
                        }
                    }
                }

                val errorJob = async {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            error.appendLine(line)
                            println("[ADB-ERR] $line")
                        }
                    }
                }

                // Wait with timeout
                val completed = withTimeoutOrNull(45000) {
                    process.waitFor()
                    true
                }

                if (completed == null) {
                    println("[ADB] Pairing timed out")
                    process.destroyForcibly()
                    outputJob.cancel()
                    errorJob.cancel()
                    return@withContext CommandResult(
                        success = false,
                        output = output.toString().trim(),
                        error = "Pairing timed out after 45 seconds",
                        fullOutput = "TIMEOUT: ${output}\nERROR: ${error}"
                    )
                }

                outputJob.await()
                errorJob.await()

                val exitCode = process.exitValue()
                val outputStr = output.toString().trim()
                val errorStr = error.toString().trim()

                // Check if pairing was successful
                val success = exitCode == 0 ||
                        outputStr.contains("Successfully paired") ||
                        outputStr.contains("paired to")

                println("[ADB] Pairing result - Exit: $exitCode, Success: $success")

                CommandResult(
                    success = success,
                    output = outputStr,
                    error = errorStr,
                    fullOutput = "EXIT: $exitCode\nOUT: $outputStr\nERR: $errorStr"
                )

            } catch (e: Exception) {
                println("[ADB] Pairing exception: ${e.message}")
                e.printStackTrace()
                CommandResult(
                    success = false,
                    output = "",
                    error = "Pairing exception: ${e.message}",
                    fullOutput = "Exception: ${e.message}"
                )
            }
        }
    }

    /**
     * Connect to device - Simple, no checking
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
}
