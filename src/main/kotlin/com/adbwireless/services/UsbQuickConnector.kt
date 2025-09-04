package com.adbwireless.services

import com.intellij.openapi.progress.ProgressIndicator
import java.util.regex.Pattern

class UsbQuickConnector(
    private val adb: ADBService,
    private val indicator: ProgressIndicator
) {

    data class UsbDevice(
        val serial: String,
        val model: String?,
        val product: String?,
        val deviceName: String?,
        val state: String
    ) {
        fun displayName(): String {
            val label = model ?: product ?: deviceName ?: serial
            return "$label ($serial)"
        }
    }

    fun listUsbDevices(): List<UsbDevice> {
        val res = adb.executeAdbCommand("devices", "-l")
        if (!res.success) {
            throw IllegalStateException("Failed to run `adb devices -l`: ${res.error.ifBlank { res.output }}")
        }
        val devices = mutableListOf<UsbDevice>()
        res.output.lines().drop(1).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val serial = parts[0]
                val state = parts[1]
                if (state == "device") {
                    val model = parts.find { it.startsWith("model:") }?.substringAfter("model:")
                    val product = parts.find { it.startsWith("product:") }?.substringAfter("product:")
                    val devName = parts.find { it.startsWith("device:") }?.substringAfter("device:")
                    devices += UsbDevice(serial, model, product, devName, state)
                }
            }
        }
        return devices
    }

    fun getDeviceIp(serial: String): String? {
        val attempts = listOf(
            listOf("-s", serial, "shell", "ip", "-f", "inet", "addr", "show", "wlan0"),
            listOf("-s", serial, "shell", "ip", "addr", "show", "wlan0"),
            listOf("-s", serial, "shell", "ifconfig", "wlan0"),
            listOf("-s", serial, "shell", "ip", "route"),
            listOf("-s", serial, "shell", "getprop", "dhcp.wlan0.ipaddress")
        )
        for (cmd in attempts) {
            indicator.checkCanceled()
            val res = adb.executeAdbCommand(*cmd.toTypedArray())
            if (!res.success) continue
            val ip = parseIp(res.output) ?: parseIp(res.error)
            if (ip != null) return ip
        }
        return null
    }

    private fun parseIp(s: String): String? {
        val inetPattern = Pattern.compile("\\binet\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)")
        inetPattern.matcher(s).let { m ->
            if (m.find()) return m.group(1)
        }
        val srcPattern = Pattern.compile("\\bsrc\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)")
        srcPattern.matcher(s).let { m ->
            if (m.find()) return m.group(1)
        }
        val ipOnly = Pattern.compile("^(\\d+\\.\\d+\\.\\d+\\.\\d+)\$")
        ipOnly.matcher(s.trim()).let { m ->
            if (m.find()) return m.group(1)
        }
        return null
    }

    fun enableTcpIp(serial: String, port: Int = 5555) {
        val res = adb.executeAdbCommand("-s", serial, "tcpip", port.toString())
        val combined = res.output + "\n" + res.error
        if (!res.success && !combined.contains("restarting in")) {
            throw IllegalStateException("Failed to enable TCP/IP: ${res.error.ifBlank { res.output }}")
        }
    }

    fun connect(hostPort: String): Boolean {
        val res = adb.executeAdbCommand("connect", hostPort)
        val out = res.output + "\n" + res.error
        return res.success || out.contains("connected to") || out.contains("already connected")
    }
}