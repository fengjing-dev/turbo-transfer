package com.fatina.transfer.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Properties;

/**
 * 统一管理应用运行目录。
 * 传输目录支持运行时切换并持久化到用户配置文件，其余目录优先使用显式配置，否则按安装目录推断。
 * @author Fatina 2026/06/29
 */
public final class AppPaths {
    private static final String APP_HOME_KEY = "turbo.transfer.app.home";
    private static final String TRANSFER_DIR_KEY = "turbo.transfer.transfer-dir";
    private static final String LEGACY_DOWNLOAD_DIR_KEY = "turbo.transfer.download-dir";
    private static final String ICON_CACHE_DIR_KEY = "turbo.transfer.icon.cache-dir";
    private static final String PREVIEW_CACHE_DIR_KEY = "turbo.transfer.preview.cache-dir";
    private static final String NATIVE_TEMP_DIR_KEY = "turbo.transfer.icon.native-temp-dir";
    private static final String LOGS_DIR_KEY = "turbo.transfer.logs-dir";

    private final Path appHome;
    private volatile Path transferDir;
    private final Path iconCacheDir;
    private final Path previewCacheDir;
    private final Path nativeTempDir;
    private final Path logsDir;
    private final Path userSettingsFile;

    private AppPaths(
            Path appHome,
            Path transferDir,
            Path iconCacheDir,
            Path previewCacheDir,
            Path nativeTempDir,
            Path logsDir,
            Path userSettingsFile
    ) {
        this.appHome = appHome;
        this.transferDir = transferDir;
        this.iconCacheDir = iconCacheDir;
        this.previewCacheDir = previewCacheDir;
        this.nativeTempDir = nativeTempDir;
        this.logsDir = logsDir;
        this.userSettingsFile = userSettingsFile;
    }

    public static AppPaths load() {
        Properties properties = AppConfig.loadProperties();
        Path userSettingsFile = defaultUserSettingsFile();
        Properties userSettings = loadUserSettings(userSettingsFile);

        Path defaultAppHome = detectDefaultAppHome();
        Path appHome = resolvePath(properties, APP_HOME_KEY, defaultAppHome);
        Path transferDir = resolveTransferDir(properties, userSettings, appHome);
        Path iconCacheDir = resolvePath(properties, ICON_CACHE_DIR_KEY, appHome.resolve("cache").resolve("icon"));
        Path previewCacheDir = resolvePath(properties, PREVIEW_CACHE_DIR_KEY, appHome.resolve("cache").resolve("preview"));
        Path nativeTempDir = resolvePath(properties, NATIVE_TEMP_DIR_KEY, appHome.resolve("cache").resolve("native"));
        Path logsDir = resolvePath(properties, LOGS_DIR_KEY, appHome.resolve("log"));

        AppPaths appPaths = new AppPaths(appHome, transferDir, iconCacheDir, previewCacheDir, nativeTempDir, logsDir, userSettingsFile);
        appPaths.ensureDirectories();
        return appPaths;
    }

    /**
     * 传输目录解析优先级：用户配置文件 &gt; 系统属性/打包配置(新键) &gt; 旧 download-dir 兼容 &gt; 默认 transfers。
     */
    private static Path resolveTransferDir(Properties properties, Properties userSettings, Path appHome) {
        String userValue = userSettings.getProperty(TRANSFER_DIR_KEY);
        if (userValue != null && !userValue.isBlank()) {
            return normalize(Path.of(userValue));
        }
        String configured = AppConfig.resolve(properties, TRANSFER_DIR_KEY, null);
        if (configured == null || configured.isBlank()) {
            configured = AppConfig.resolve(properties, LEGACY_DOWNLOAD_DIR_KEY, null);
        }
        if (configured != null && !configured.isBlank()) {
            return normalize(Path.of(configured));
        }
        return normalize(appHome.resolve("transfers"));
    }

    private static Path detectDefaultAppHome() {
        String explicitAppHome = System.getProperty(APP_HOME_KEY);
        if (explicitAppHome != null && !explicitAppHome.isBlank()) {
            return normalize(Path.of(explicitAppHome));
        }

        String packagedLauncherPath = System.getProperty("jpackage.app-path");
        if (packagedLauncherPath != null && !packagedLauncherPath.isBlank()) {
            Path launcherPath = normalize(Path.of(packagedLauncherPath));
            Path launcherDir = launcherPath.getParent();
            if (launcherDir != null) {
                return launcherDir;
            }
        }

        Path codeSourceHome = resolveFromCodeSource();
        if (codeSourceHome != null) {
            return codeSourceHome;
        }

        return normalize(Path.of(System.getProperty("user.dir")));
    }

    private static Path resolveFromCodeSource() {
        try {
            CodeSource codeSource = AppPaths.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }

            Path locationPath = normalize(Path.of(codeSource.getLocation().toURI()));
            if (Files.isRegularFile(locationPath)) {
                Path parent = locationPath.getParent();
                if (parent == null) {
                    return null;
                }
                if ("app".equalsIgnoreCase(parent.getFileName().toString()) && parent.getParent() != null) {
                    return parent.getParent();
                }
                return parent;
            }
            return locationPath;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Path resolvePath(Properties properties, String key, Path defaultPath) {
        return normalize(Path.of(AppConfig.resolve(properties, key, defaultPath.toString())));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path defaultUserSettingsFile() {
        return normalize(Path.of(System.getProperty("user.home"), ".turbotransfer", "settings.properties"));
    }

    private static Properties loadUserSettings(Path file) {
        Properties props = new Properties();
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
                // 用户配置读取失败时退回默认，不阻断启动。
            }
        }
        return props;
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(appHome);
            Files.createDirectories(transferDir);
            Files.createDirectories(iconCacheDir);
            Files.createDirectories(previewCacheDir);
            Files.createDirectories(nativeTempDir);
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            throw new IllegalStateException("初始化应用目录失败", e);
        }
    }

    /**
     * 运行时切换传输目录：创建目录、更新内存值并持久化到用户配置，下一次写入与读取即刻生效。
     */
    public synchronized Path changeTransferDir(Path newDir) {
        Path normalized = normalize(newDir);
        try {
            Files.createDirectories(normalized);
        } catch (IOException e) {
            throw new IllegalStateException("创建传输目录失败: " + normalized, e);
        }
        this.transferDir = normalized;
        persistUserSetting(TRANSFER_DIR_KEY, normalized.toString());
        return normalized;
    }

    private void persistUserSetting(String key, String value) {
        try {
            Properties userSettings = loadUserSettings(userSettingsFile);
            userSettings.setProperty(key, value);
            if (userSettingsFile.getParent() != null) {
                Files.createDirectories(userSettingsFile.getParent());
            }
            try (OutputStream out = Files.newOutputStream(userSettingsFile)) {
                userSettings.store(out, "TurboTransfer user settings");
            }
        } catch (IOException e) {
            throw new IllegalStateException("保存用户配置失败: " + userSettingsFile, e);
        }
    }

    public Path appHome() {
        return appHome;
    }

    public Path transferDir() {
        return transferDir;
    }

    public Path iconCacheDir() {
        return iconCacheDir;
    }

    public Path previewCacheDir() {
        return previewCacheDir;
    }

    public Path nativeTempDir() {
        return nativeTempDir;
    }

    public Path logsDir() {
        return logsDir;
    }
}
