package com.vrem.wifianalyzer.wifi.sniffer

import android.util.Base64
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbClient {
    companion object {
        private const val TAG = "WiFiSniffer"
        private const val ADB_PORT = 5555
        private const val MAX_PAYLOAD = 4096
        private const val A_CNXN = 0x4e584e43
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val VERSION = 0x01000000
    }

    fun execute(command: String): Boolean {
        Log.d(TAG, "AdbClient executing: $command")
        try {
            Log.d(TAG, "Connecting to 127.0.0.1:$ADB_PORT")
            val socket = Socket("127.0.0.1", ADB_PORT)
            Log.d(TAG, "Connected")
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Send CNXN
            sendPacket(output, A_CNXN, VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray())
            
            // 2. Read CNXN response
            val response = readPacket(input)
            if (response.command != A_CNXN) {
                Log.e(TAG, "Expected CNXN, got ${response.command}")
                socket.close()
                return false
            }

            // 3. Send OPEN shell:command
            val localId = 1
            sendPacket(output, A_OPEN, localId, 0, "shell:$command\u0000".toByteArray())

            // 4. Read OKAY
            val openResponse = readPacket(input)
            if (openResponse.command != A_OKAY) {
                Log.e(TAG, "Expected OKAY, got ${openResponse.command}")
                socket.close()
                return false
            }
            val remoteId = openResponse.arg0

            // 5. Read output (WRTE) or Close (CLSE)
            // We just want to wait for it to finish? 
            // Or just fire and forget? 
            // Better wait for CLSE to ensure command finished.
            while (true) {
                val packet = readPacket(input)
                if (packet.command == A_WRTE) {
                    sendPacket(output, A_OKAY, localId, remoteId, null) // Ack
                    // Log output?
                    if (packet.payload != null) {
                        Log.d(TAG, "ADB Output: ${String(packet.payload)}")
                    }
                } else if (packet.command == A_CLSE) {
                    sendPacket(output, A_CLSE, localId, remoteId, null) // Ack close
                    break
                }
            }

            socket.close()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "ADB execution failed", e)
            return false
        }
    }

    private data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataCrc: Int,
        val magic: Int,
        val payload: ByteArray?
    )

    private fun sendPacket(output: OutputStream, command: Int, arg0: Int, arg1: Int, data: ByteArray?) {
        val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(data?.size ?: 0)
        buffer.putInt(if (data != null) getCrc32(data) else 0)
        buffer.putInt(command.inv())
        output.write(buffer.array())
        if (data != null) {
            output.write(data)
        }
        output.flush()
    }

    private fun readPacket(input: InputStream): AdbPacket {
        val header = ByteArray(24)
        var totalRead = 0
        while (totalRead < 24) {
            val read = input.read(header, totalRead, 24 - totalRead)
            if (read < 0) throw java.io.IOException("EOF reading header")
            totalRead += read
        }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val len = buffer.int
        val crc = buffer.int
        val magic = buffer.int

        var payload: ByteArray? = null
        if (len > 0) {
            payload = ByteArray(len)
            totalRead = 0
            while (totalRead < len) {
                val read = input.read(payload, totalRead, len - totalRead)
                if (read < 0) throw java.io.IOException("EOF reading payload")
                totalRead += read
            }
        }
        return AdbPacket(command, arg0, arg1, len, crc, magic, payload)
    }

    private fun getCrc32(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += b.toInt() and 0xFF
        }
        return sum
    }
}
