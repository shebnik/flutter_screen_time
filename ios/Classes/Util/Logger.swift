//
//  Logger.swift
//  flutter_screen_time
//
//  Created by Nikita on 8/21/25.
//

import Foundation
import os

/// Enhanced logging utility that writes to both console and file
class Logger {
    static let shared = Logger()
    
    private var logFileURL: URL?
    private let logQueue = DispatchQueue(label: "com.shebnik.flutter_screen_time.logger", qos: .utility)
    private let dateFormatter: DateFormatter
    private let osLogger = os.Logger(
        subsystem: "com.shebnik.flutter_screen_time.ios", category: "FlutterScreenTime")
    
    private init() {
        dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        dateFormatter.timeZone = TimeZone.current
    }
    
    /// Configure the log file path (called from Flutter)
    func configureLogFile(path: String) {
        logQueue.async {
            self.logFileURL = URL(fileURLWithPath: path)
            self.log("📁 Log file configured: \(path)", level: .info)
            
            // Create directory if it doesn't exist
            let directory = self.logFileURL!.deletingLastPathComponent()
            try? FileManager.default.createDirectory(
                at: directory, withIntermediateDirectories: true)
            
            // Write initial log entry
            let initialMessage = """
                
                =====================================
                📱 Flutter Screen Time iOS - Log Started
                📅 Date: \(self.dateFormatter.string(from: Date()))
                📁 Log File: \(path)
                =====================================
                
                """
            self.writeToFile(initialMessage)
        }
    }
    
    /// Log levels for categorizing messages
    enum LogLevel: String, CaseIterable {
        case debug = "🔍 DEBUG"
        case info = "ℹ️ INFO"
        case warning = "⚠️ WARNING"
        case error = "❌ ERROR"
        case success = "✅ SUCCESS"
    }
    
    /// Main logging method
    func log(
        _ message: String, level: LogLevel = .info, file: String = #file,
        function: String = #function, line: Int = #line
    ) {
        let filename = URL(fileURLWithPath: file).lastPathComponent
        let timestamp = dateFormatter.string(from: Date())
        let logMessage =
        "[\(timestamp)] \(level.rawValue) [\(filename):\(line)] \(function) - \(message)"
        
        // Print to console (existing behavior)
        print(logMessage)
        
        // Also log to OS log for better debugging
        switch level {
        case .debug:
            osLogger.debug("\(message)")
        case .info:
            osLogger.info("\(message)")
        case .warning:
            osLogger.warning("\(message)")
        case .error:
            osLogger.error("\(message)")
        case .success:
            osLogger.info("✅ \(message)")
        }
        
        // Write to file asynchronously
        writeToFile(logMessage)
    }
    
    /// Convenience methods for different log levels
    func debug(
        _ message: String, file: String = #file, function: String = #function, line: Int = #line
    ) {
        log(message, level: .debug, file: file, function: function, line: line)
    }
    
    func info(
        _ message: String, file: String = #file, function: String = #function, line: Int = #line
    ) {
        log(message, level: .info, file: file, function: function, line: line)
    }
    
    func warning(
        _ message: String, file: String = #file, function: String = #function, line: Int = #line
    ) {
        log(message, level: .warning, file: file, function: function, line: line)
    }
    
    func error(
        _ message: String, file: String = #file, function: String = #function, line: Int = #line
    ) {
        log(message, level: .error, file: file, function: function, line: line)
    }
    
    func success(
        _ message: String, file: String = #file, function: String = #function, line: Int = #line
    ) {
        log(message, level: .success, file: file, function: function, line: line)
    }
    
    /// Write to log file
    private func writeToFile(_ message: String) {
        logQueue.async {
            guard let logFileURL = self.logFileURL else { return }
            
            let messageWithNewline = message + "\n"
            
            if let data = messageWithNewline.data(using: .utf8) {
                if FileManager.default.fileExists(atPath: logFileURL.path) {
                    // Append to existing file
                    if let fileHandle = try? FileHandle(forWritingTo: logFileURL) {
                        fileHandle.seekToEndOfFile()
                        fileHandle.write(data)
                        fileHandle.closeFile()
                    }
                } else {
                    // Create new file
                    try? data.write(to: logFileURL)
                }
            }
        }
    }
    
    /// Get current log file content (for debugging)
    func getLogContent() -> String? {
        guard let logFileURL = logFileURL else { return nil }
        return try? String(contentsOf: logFileURL, encoding: .utf8)
    }
    
    /// Clear log file
    func clearLogFile() {
        logQueue.async {
            guard let logFileURL = self.logFileURL else { return }
            try? FileManager.default.removeItem(at: logFileURL)
            self.log("🗑️ Log file cleared", level: .info)
        }
    }
    
    /// Get log file size in bytes
    func getLogFileSize() -> Int64? {
        guard let logFileURL = logFileURL else { return nil }
        
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: logFileURL.path)
            return attributes[.size] as? Int64
        } catch {
            return nil
        }
    }
    
    /// Get the current log file content
    func getLogContent() -> String {
        guard let logFileURL = logFileURL else {
            return "Log file not configured"
        }
        
        do {
            return try String(contentsOf: logFileURL, encoding: .utf8)
        } catch {
            return "Error reading log file: \(error.localizedDescription)"
        }
    }
    
    /// Clear logs with better naming for Flutter interface
    func clearLogs() -> Bool {
        guard let logFileURL = logFileURL else { return false }
        
        do {
            try "".write(to: logFileURL, atomically: true, encoding: .utf8)
            log("🗑️ Log file cleared", level: .info)
            return true
        } catch {
            log("❌ Failed to clear log file: \(error)", level: .error)
            return false
        }
    }
}

/// Global convenience functions for easier logging
func logDebug(
    _ message: String, file: String = #file, function: String = #function, line: Int = #line
) {
    Logger.shared.debug(message, file: file, function: function, line: line)
}

func logInfo(
    _ message: String, file: String = #file, function: String = #function, line: Int = #line
) {
    Logger.shared.info(message, file: file, function: function, line: line)
}

func logWarning(
    _ message: String, file: String = #file, function: String = #function, line: Int = #line
) {
    Logger.shared.warning(message, file: file, function: function, line: line)
}

func logError(
    _ message: String, file: String = #file, function: String = #function, line: Int = #line
) {
    Logger.shared.error(message, file: file, function: function, line: line)
}

func logSuccess(
    _ message: String, file: String = #file, function: String = #function, line: Int = #line
) {
    Logger.shared.success(message, file: file, function: function, line: line)
}
