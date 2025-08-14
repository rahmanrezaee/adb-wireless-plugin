package com.adbwireless.models

/**
 * Simple device model
 */
data class Device(
    val name: String,
    val ip: String,
    val port: String = "5555",
    var isConnected: Boolean = false
) {
    fun getConnectAddress(): String = "$ip:$port"

    override fun toString(): String = "$name ($ip:$port)"
}