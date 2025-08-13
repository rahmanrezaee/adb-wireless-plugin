package com.adbwireless.services

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.InetAddress
import java.util.*
import kotlin.random.Random

/**
 * Service for generating QR codes for wireless ADB debugging
 * PROJECT-LEVEL SERVICE - One instance per project
 */
@Service(Service.Level.PROJECT)
class QRCodeService(private val project: Project) {

    /**
     * Generate QR code data for wireless debugging
     * Format follows Android's wireless debugging QR code standard
     */
    fun generateWirelessDebuggingQRData(ip: String, port: String, password: String): String {
        // Android wireless debugging QR format:
        // WIFI:T:ADB;S:devicename;P:password;H:ip:port;;
        val deviceName = getDeviceName()
        return "WIFI:T:ADB;S:$deviceName;P:$password;H:$ip:$port;;"
    }

    /**
     * Generate QR code image from data
     */
    fun generateQRCodeImage(data: String, size: Int = 300): BufferedImage? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            hints[EncodeHintType.MARGIN] = 1
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"

            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size, hints)

            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)

            // Draw the QR code
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val color = if (bitMatrix[x, y]) Color.BLACK.rgb else Color.WHITE.rgb
                    image.setRGB(x, y, color)
                }
            }

            image
        } catch (e: WriterException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get local device name for QR code
     */
    private fun getDeviceName(): String {
        return try {
            val hostname = InetAddress.getLocalHost().hostName
            if (hostname.isNotBlank()) hostname else "AndroidStudio"
        } catch (e: Exception) {
            "AndroidStudio"
        }
    }

    /**
     * Generate QR code for wireless debugging with random password
     * This creates a complete QR code that Android devices can scan
     */
    fun generateWirelessDebuggingQR(ip: String, port: String): Pair<String, BufferedImage?> {
        val password = generateRandomPassword()
        val qrData = generateWirelessDebuggingQRData(ip, port, password)
        val qrImage = generateQRCodeImage(qrData, 300)

        // Debug output
        println("Generated QR Data: $qrData")
        println("Password: $password")

        return Pair(password, qrImage)
    }

    /**
     * Generate a random 6-digit password for pairing
     */
    private fun generateRandomPassword(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    /**
     * Parse QR code content from wireless debugging QR
     */
    fun parseQRCodeContent(qrContent: String): QRCodeData? {
        return try {
            // Clean up the input
            val cleanContent = qrContent.trim()

            if (!cleanContent.startsWith("WIFI:T:ADB;")) {
                println("QR content doesn't start with WIFI:T:ADB;")
                return null
            }

            val parts = cleanContent.split(";")
            var deviceName = ""
            var password = ""
            var hostInfo = ""

            for (part in parts) {
                when {
                    part.startsWith("S:") -> deviceName = part.substring(2)
                    part.startsWith("P:") -> password = part.substring(2)
                    part.startsWith("H:") -> hostInfo = part.substring(2)
                }
            }

            if (hostInfo.isEmpty() || password.isEmpty()) {
                println("Missing host info or password in QR code")
                return null
            }

            val hostParts = hostInfo.split(":")
            if (hostParts.size != 2) {
                println("Invalid host format: $hostInfo")
                return null
            }

            QRCodeData(hostParts[0], hostParts[1], password, deviceName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Data class for parsed QR code information
     */
    data class QRCodeData(
        val ip: String,
        val port: String,
        val password: String,
        val deviceName: String
    )
}