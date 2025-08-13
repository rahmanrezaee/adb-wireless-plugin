import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import java.util.*

fun main() {
    println("Testing QR Code Generation...")
    
    try {
        // Test QR code generation
        val qrData = "WIFI:T:ADB;S:TestDevice;P:123456;H:192.168.1.100:5555;;"
        val qrImage = generateQRCodeImage(qrData, 300)
        
        if (qrImage != null) {
            // Save the QR code to a file
            val outputFile = File("test_qr_code.png")
            ImageIO.write(qrImage, "PNG", outputFile)
            println("✅ QR code generated successfully!")
            println("📁 Saved to: ${outputFile.absolutePath}")
            println("📋 QR Data: $qrData")
        } else {
            println("❌ Failed to generate QR code")
        }
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    }
}

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
        println("WriterException: ${e.message}")
        null
    } catch (e: Exception) {
        println("Exception: ${e.message}")
        null
    }
}
