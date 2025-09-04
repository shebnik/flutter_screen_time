package com.shebnik.flutter_screen_time.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced logging utility that writes to both console and file
 */
class Logger private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: Logger? = null
        
        fun getInstance(): Logger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Logger().also { INSTANCE = it }
            }
        }
        
        private const val SUBSYSTEM = "FlutterScreenTime"
    }
    
    private var logFile: File? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Configure the log file path (called from Flutter)
     */
    fun configureLogFile(path: String) {
        logScope.launch {
            logFile = File(path)
            log(SUBSYSTEM, "📁 Log file configured: $path", LogLevel.INFO)
            
            // Create directory if it doesn't exist
            logFile?.parentFile?.mkdirs()
            
            // Write initial log entry
            val initialMessage = """

=====================================
📱 Flutter Screen Time Android - Log Started
📅 Date: ${dateFormatter.format(Date())}
📁 Log File: $path
=====================================

"""
            writeToFile(initialMessage)
        }
    }
    
    /**
     * Log levels for categorizing messages
     */
    enum class LogLevel(val emoji: String, val androidLevel: Int) {
        DEBUG("🔍 DEBUG", Log.DEBUG),
        INFO("ℹ️ INFO", Log.INFO),
        WARNING("⚠️ WARNING", Log.WARN),
        ERROR("❌ ERROR", Log.ERROR),
        SUCCESS("✅ SUCCESS", Log.INFO)
    }
    
    /**
     * Main logging method
     */
    fun log(
        tag: String = SUBSYSTEM,
        message: String,
        level: LogLevel = LogLevel.INFO,
        throwable: Throwable? = null
    ) {
        val timestamp = dateFormatter.format(Date())
        val logMessage = "[$timestamp] ${level.emoji} [$tag] - $message"
        
        // Print to Android Log (existing behavior)
        if (throwable != null) {
            Log.println(level.androidLevel, tag, "$logMessage\n${Log.getStackTraceString(throwable)}")
        } else {
            Log.println(level.androidLevel, tag, logMessage)
        }
        
        // Write to file asynchronously
        writeToFile(if (throwable != null) "$logMessage\n${throwable.stackTraceToString()}" else logMessage)
    }
    
    /**
     * Convenience methods for different log levels
     */
    fun debug(tag: String = SUBSYSTEM, message: String, throwable: Throwable? = null) {
        log(tag, message, LogLevel.DEBUG, throwable)
    }
    
    fun info(tag: String = SUBSYSTEM, message: String, throwable: Throwable? = null) {
        log(tag, message, LogLevel.INFO, throwable)
    }
    
    fun warning(tag: String = SUBSYSTEM, message: String, throwable: Throwable? = null) {
        log(tag, message, LogLevel.WARNING, throwable)
    }
    
    fun error(tag: String = SUBSYSTEM, message: String, throwable: Throwable? = null) {
        log(tag, message, LogLevel.ERROR, throwable)
    }
    
    fun success(tag: String = SUBSYSTEM, message: String, throwable: Throwable? = null) {
        log(tag, message, LogLevel.SUCCESS, throwable)
    }
    
    /**
     * Write to log file
     */
    private fun writeToFile(message: String) {
        logScope.launch {
            logFile?.let { file ->
                try {
                    FileWriter(file, true).use { writer ->
                        PrintWriter(writer).use { printWriter ->
                            printWriter.println(message)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(SUBSYSTEM, "Failed to write to log file", e)
                }
            }
        }
    }
    
    /**
     * Get current log file content (for debugging)
     */
    fun getLogContent(): String? {
        return try {
            logFile?.takeIf { it.exists() }?.readText()
        } catch (e: Exception) {
            Log.e(SUBSYSTEM, "Failed to read log file", e)
            null
        }
    }
    
    /**
     * Clear log file
     */
    fun clearLogFile() {
        logScope.launch {
            try {
                logFile?.let { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
                log(SUBSYSTEM, "🗑️ Log file cleared", LogLevel.INFO)
            } catch (e: Exception) {
                log(SUBSYSTEM, "❌ Failed to clear log file: ${e.message}", LogLevel.ERROR, e)
            }
        }
    }
    
    /**
     * Get log file size in bytes
     */
    fun getLogFileSize(): Long? {
        return try {
            logFile?.takeIf { it.exists() }?.length()
        } catch (e: Exception) {
            Log.e(SUBSYSTEM, "Failed to get log file size", e)
            null
        }
    }
    
    /**
     * Clear logs with better naming for Flutter interface
     */
    fun clearLogs(): Boolean {
        return try {
            logFile?.let { file ->
                if (file.exists()) {
                    file.writeText("")
                    log(SUBSYSTEM, "🗑️ Log file cleared", LogLevel.INFO)
                    true
                } else {
                    false
                }
            } ?: false
        } catch (e: Exception) {
            log(SUBSYSTEM, "❌ Failed to clear log file: ${e.message}", LogLevel.ERROR, e)
            false
        }
    }
}

/**
 * Global convenience functions for easier logging
 */
fun logDebug(tag: String = "FlutterScreenTime", message: String, throwable: Throwable? = null) {
    Logger.getInstance().debug(tag, message, throwable)
}

fun logInfo(tag: String = "FlutterScreenTime", message: String, throwable: Throwable? = null) {
    Logger.getInstance().info(tag, message, throwable)
}

fun logWarning(tag: String = "FlutterScreenTime", message: String, throwable: Throwable? = null) {
    Logger.getInstance().warning(tag, message, throwable)
}

fun logError(tag: String = "FlutterScreenTime", message: String, throwable: Throwable? = null) {
    Logger.getInstance().error(tag, message, throwable)
}

fun logSuccess(tag: String = "FlutterScreenTime", message: String, throwable: Throwable? = null) {
    Logger.getInstance().success(tag, message, throwable)
}
