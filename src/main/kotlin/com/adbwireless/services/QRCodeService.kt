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
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.net.InetAddress
import java.util.*

/**
 * Service for generating QR codes for wireless ADB debugging
 * PROJECT-LEVEL SERVICE - One instance per project
 */
@Service(Service.Level.PROJECT)
class QRCodeService(private val project: Project) {

    /**
     * Generate QR code data for wireless debugging
     */
    fun generateWirelessDebuggingQRData(ip: String, port: String, password: String): String {
        // Format: WIFI:T:ADB;S:devicename;P:password;H:ip:port;;
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
            hints[EncodeHintType.MARGIN] = 2

            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size, hints)

            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()

            // Set background to white
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, size, size)

            // Draw QR code
            graphics.color = Color.BLACK
            for (x in 0 until size) {
                for (y in 0 until size) {
                    if (bitMatrix[x, y]) {
                        graphics.fillRect(x, y, 1, 1)
                    }
                }
            }

            graphics.dispose()
            image
        } catch (e: WriterException) {
            null
        }
    }

    /**
     * Get local device name for QR code
     */
    private fun getDeviceName(): String {
        return try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "AndroidStudio"
        }
    }

    /**
     * Generate QR code for wireless debugging with random password
     */
    fun generateWirelessDebuggingQR(ip: String, port: String): Pair<String, BufferedImage?> {
        val password = generateRandomPassword()
        val qrData = generateWirelessDebuggingQRData(ip, port, password)
        val qrImage = generateQRCodeImage(qrData)
        return Pair(password, qrImage)
    }

    /**
     * Generate a random 6-digit password for pairing
     */
    private fun generateRandomPassword(): String {
        return (100000..999999).random().toString()
    }

    /**
     * Parse QR code content from wireless debugging QR
     */
    fun parseQRCodeContent(qrContent: String): QRCodeData? {
        return try {
            if (!qrContent.startsWith("WIFI:T:ADB;")) {
                return null
            }

            val parts = qrContent.split(";")
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

            val hostParts = hostInfo.split(":")
            if (hostParts.size != 2) return null

            QRCodeData(hostParts[0], hostParts[1], password, deviceName)
        } catch (e: Exception) {
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
