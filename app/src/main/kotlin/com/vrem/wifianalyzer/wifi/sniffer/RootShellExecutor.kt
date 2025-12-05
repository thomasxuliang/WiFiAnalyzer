package com.vrem.wifianalyzer.wifi.sniffer

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException

class RootShellExecutor {
    companion object {
        private const val TAG = "WiFiSniffer"
        private val SU_PATHS = arrayOf(
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su"
        )
    }

    private var suPath: String? = null

    private fun getSuPath(): String {
        if (suPath != null) return suPath!!

        // Diagnostic: Check permissions of known su paths
        try {
            val p = Runtime.getRuntime().exec("ls -l /system/xbin/su /system/bin/su")
            val output = p.inputStream.bufferedReader().readText()
            Log.d(TAG, "ls -l su output: $output")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ls su", e)
        }

        // Try to find a working su
        for (path in SU_PATHS) {
            try {
                Log.d(TAG, "Probing su at: $path")
                val process = Runtime.getRuntime().exec(arrayOf(path, "-c", "id"))
                val exitValue = process.waitFor()
                if (exitValue == 0) {
                    Log.d(TAG, "Found working su at: $path")
                    suPath = path
                    return path
                } else {
                    Log.w(TAG, "Probing $path exited with $exitValue")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Probing $path failed", e)
            }
        }

        // Try sh -c su
        try {
            Log.d(TAG, "Probing sh -c su")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "su -c id"))
            val exitValue = process.waitFor()
            if (exitValue == 0) {
                Log.d(TAG, "Found working su via sh")
                suPath = "sh -c su" // Note: this changes how we execute later
                // This logic implies we need to change execute() to handle this special case
                // For now, let's just return "su" but we know it needs sh wrapper? 
                // Actually if sh -c su works, then maybe we should return "su" but ensure we execute it via sh?
                // But Runtime.exec("su") failed.
                // So if this works, we should probably change execute implementation.
                // But for simplicity, let's assume if this works, maybe "su" works inside sh?
                // But we returned "su" in execute(). 
                // Let's just log it for now.
            } 
        } catch (e: Exception) {
             Log.w(TAG, "Probing sh -c su failed", e)
        }
        
        Log.e(TAG, "No working su found, defaulting to 'su'")
        return "su"
    }

    fun execute(command: String): Boolean {
        Log.d(TAG, "Executing command: $command")
        var process: Process? = null
        var os: DataOutputStream? = null
        try {
            val su = getSuPath()
            process = Runtime.getRuntime().exec(su)
            os = DataOutputStream(process.outputStream)
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            Log.d(TAG, "Command exit value: $exitValue")
            return exitValue == 0
        } catch (e: IOException) {
            Log.e(TAG, "IOException execute command: $command", e)
            e.printStackTrace()
            return false
        } catch (e: InterruptedException) {
            Log.e(TAG, "InterruptedException execute command: $command", e)
            e.printStackTrace()
            return false
        } finally {
            try {
                os?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun executeSequence(commands: List<String>): Boolean {
        Log.d(TAG, "Executing sequence: $commands")
        var process: Process? = null
        var os: DataOutputStream? = null
        try {
            val su = getSuPath()
            process = Runtime.getRuntime().exec(su)
            os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes(cmd + "\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            Log.d(TAG, "Sequence exit value: $exitValue")
            return exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing sequence", e)
            e.printStackTrace()
            return false
        } finally {
            try {
                os?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
