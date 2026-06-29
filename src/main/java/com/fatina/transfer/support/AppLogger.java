package com.fatina.transfer.support;

import com.fatina.transfer.config.AppPaths;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 最小文件日志器，优先保证桌面壳和服务异常可落盘追踪。
 * @author Fatina 2026/06/29
 */
public final class AppLogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AppPaths APP_PATHS = AppPaths.load();

    private final Path logFile;

    private AppLogger(Path logFile) {
        this.logFile = logFile;
    }

    public static AppLogger forName(String fileName) {
        return new AppLogger(APP_PATHS.logsDir().resolve(fileName));
    }

    public synchronized void info(String message) {
        write("INFO", message, null);
    }

    public synchronized void error(String message, Throwable throwable) {
        write("ERROR", message, throwable);
    }

    private void write(String level, String message, Throwable throwable) {
        try {
            Files.createDirectories(logFile.getParent());
            StringBuilder builder = new StringBuilder()
                    .append('[').append(FORMATTER.format(LocalDateTime.now())).append("] ")
                    .append(level).append(' ')
                    .append(message)
                    .append(System.lineSeparator());

            if (throwable != null) {
                StringWriter stringWriter = new StringWriter();
                throwable.printStackTrace(new PrintWriter(stringWriter));
                builder.append(stringWriter);
                if (!stringWriter.toString().endsWith(System.lineSeparator())) {
                    builder.append(System.lineSeparator());
                }
            }

            Files.writeString(
                    logFile,
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
            // 日志落盘失败时不再向上抛，避免反向影响主流程。
        }
    }
}
