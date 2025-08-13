package com.adbwireless.models

/**
 * Data class representing an Android device for wireless ADB connection
 */
data class Device(
    val name: String,                    // User-friendly device name
    val ip: String,                      // Device IP address
    val defaultPort: String = "5555",    // Default connection port
    var isConnected: Boolean = false,    // Current connection status
    val lastConnected: Long = System.currentTimeMillis() // Timestamp of last connection
) {
    /**
     * String representation for UI display
     */
    override fun toString(): String = "$name ($ip)"

    /**
     * Get full address for connection (IP:Port)
     */
    fun getConnectAddress(): String = "$ip:$defaultPort"

    /**
     * Get pairing address with custom port
     */
    fun getPairAddress(port: String): String = "$ip:$port"
}