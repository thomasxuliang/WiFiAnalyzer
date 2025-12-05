package com.vrem.wifianalyzer.wifi.sniffer

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vrem.wifianalyzer.MainContext
import java.io.File

sealed class SnifferState {
    object Idle : SnifferState()
    object Preparing : SnifferState()
    object Capturing : SnifferState()
    data class Error(val message: String) : SnifferState()
}

object SnifferManager {
    private const val TAG = "WiFiSniffer"
    
    var currentState: SnifferState = SnifferState.Idle
        private set(value) {
            field = value
            Log.d(TAG, "State changed to: $value")
        }
        
    fun isCapturing(): Boolean {
        return currentState is SnifferState.Capturing
    }

    fun setState(state: SnifferState) {
        currentState = state
    }

    fun startCapture(channel: Int, bandwidth: Int, callback: (Boolean) -> Unit) {
        Log.d(TAG, "Starting capture on channel $channel, bw $bandwidth")
        if (currentState is SnifferState.Capturing) {
            Log.w(TAG, "Already capturing")
            callback(false)
            return
        }
        
        currentState = SnifferState.Preparing
        
        Thread {
            val result = try {
                startCaptureInternal(channel, bandwidth)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during startCapture", e)
                currentState = SnifferState.Error(e.message ?: "Unknown error")
                false
            }
            Handler(Looper.getMainLooper()).post {
                callback(result)
            }
        }.start()
    }

    private fun startCaptureInternal(channel: Int, bandwidth: Int): Boolean {
        Log.d(TAG, "startCaptureInternal called with channel=$channel, bandwidth=$bandwidth")
        
        try {
            // Step 1: Pause WiFi scanning to avoid dialog popup
            Log.d(TAG, "Pausing WiFi scanning...")
            val scannerService = MainContext.INSTANCE.scannerService
            scannerService.pause()
            
            // Step 2: Wait 1 second for scan to stop
            Log.d(TAG, "Waiting 1 second for scan to stop...")
            Thread.sleep(1000)
            
            // Step 3: Write request file
            val context = MainContext.INSTANCE.context
            Log.d(TAG, "Got context: $context")
            
            val dir = context.getExternalFilesDir(null)
            Log.d(TAG, "External files dir: $dir")
            
            if (dir == null) {
                Log.e(TAG, "External files dir is null")
                currentState = SnifferState.Error("External files dir is null")
                return false
            }

            val file = File(dir, "sniffer.req")
            Log.d(TAG, "Target file path: ${file.absolutePath}")
            
            val content = "ACTION=START\nCHANNEL=$channel\nBANDWIDTH=$bandwidth"
            Log.d(TAG, "Writing content: $content")
            
            file.writeText(content)
            
            Log.d(TAG, "File written successfully")
            Log.d(TAG, "File exists: ${file.exists()}")
            Log.d(TAG, "File size: ${file.length()} bytes")
            Log.d(TAG, "File absolute path: ${file.absolutePath}")
            
            currentState = SnifferState.Capturing
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sniffer request", e)
            currentState = SnifferState.Error("Failed to write sniffer request: ${e.message}")
            return false
        }
    }

    fun stopCapture(): Boolean {
        Log.d(TAG, "========== STOP CAPTURE CALLED ==========")
        Log.d(TAG, "Current state: $currentState")
        
        if (currentState !is SnifferState.Capturing) {
            Log.w(TAG, "Not in capturing state, cannot stop. Current state: $currentState")
            return false
        }
        
        Log.d(TAG, "Getting context...")
        val context = MainContext.INSTANCE.context
        Log.d(TAG, "Context: $context")
        
        Log.d(TAG, "Getting external files dir...")
        val dir = context.getExternalFilesDir(null)
        Log.d(TAG, "External files dir: $dir")
        
        if (dir == null) {
            Log.e(TAG, "External files dir is null, cannot write stop request")
            return false
        }

        val file = File(dir, "sniffer.req")
        Log.d(TAG, "Target file path: ${file.absolutePath}")
        
        return try {
            val content = "ACTION=STOP"
            Log.d(TAG, "Writing content: $content")
            
            file.writeText(content)
            
            Log.d(TAG, "File written successfully")
            Log.d(TAG, "File exists: ${file.exists()}")
            Log.d(TAG, "File size: ${file.length()} bytes")
            Log.d(TAG, "File content verification: ${file.readText()}")
            
            currentState = SnifferState.Idle
            Log.d(TAG, "State changed to Idle")
            Log.d(TAG, "========== STOP CAPTURE COMPLETED ==========")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sniffer stop request", e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            false
        }
    }

    private fun channelToFrequency(channel: Int): Int {
        if (channel == 14) return 2484
        if (channel < 14) return (channel - 1) * 5 + 2412
        return (channel - 36) * 5 + 5180 // Simplified 5GHz calculation
    }
}
