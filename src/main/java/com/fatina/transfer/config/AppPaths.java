package com.fatina.transfer.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Properties;

/**
 * 统一管理应用运行目录。
 * 优先使用显式配置，其次自动推断安装目录，避免目录硬编码散落。
 * @author Fatina 2026/06/29
 */
public final class AppPaths {
    private static final String APP_HOME_KEY = "turbo.transfer.app.home";
    private static final String DOWNLOAD_DIR_KEY = "turbo.transfer.download-dir";
    private static final String ICON_CACHE_DIR_KEY = "turbo.transfer.icon.cache-dir";
    private static final String PREVIEW_CACHE_DIR_KEY = "turbo.transfer.preview.cache-dir";
    private static final String NATIVE_TEMP_DIR_KEY = "turbo.transfer.icon.native-temp-dir";
    private static final String LOGS_DIR_KEY = "turbo.transfer.logs-dir";

    private final Path appHome;
    private final Path downloadDir;
    private final Path iconCacheDir;
    private final Path previewCacheDir;
    private final Path nativeTempDir;
    private final Path logsDir;

    private AppPaths(
            Path appHome,
            Path downloadDir,
            Path iconCacheDir,
            Path previewCacheDir,
            Path nativeTempDir,
            Path logsDir
    ) {
        this.appHome = appHome;
        this.downloadDir = downloadDir;
        this.iconCacheDir = iconCacheDir;
        this.previewCacheDir = previewCacheDir;
        this.nativeTempDir = nativeTempDir;
        this.logsDir = logsDir;
    }

    public static AppPaths load() {
        Properties properties = AppConfig.loadProperties();
        Path defaultAppHome = detectDefaultAppHome();
        Path appHome = resolvePath(properties, APP_HOME_KEY, defaultAppHome);
        Path downloadDir = resolvePath(properties, DOWNLOAD_DIR_KEY, appHome.resolve("downloads"));
        Path iconCacheDir = resolvePath(properties, ICON_CACHE_DIR_KEY, appHome.resolve("cache").resolve("icon"));
        Path previewCacheDir = resolvePath(properties, PREVIEW_CACHE_DIR_KEY, appHome.resolve("cache").resolve("preview"));
        Path nativeTempDir = resolvePath(properties, NATIVE_TEMP_DIR_KEY, appHome.resolve("cache").resolve("native"));
        Path logsDir = resolvePath(properties, LOGS_DIR_KEY, appHome.resolve("log"));

        AppPaths appPaths = new AppPaths(appHome, downloadDir, iconCacheDir, previewCacheDir, nativeTempDir, logsDir);
        appPaths.ensureDirectories();
        return appPaths;
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

    private void ensureDirectories() {
        try {
            Files.createDirectories(appHome);
            Files.createDirectories(downloadDir);
            Files.createDirectories(iconCacheDir);
            Files.createDirectories(previewCacheDir);
            Files.createDirectories(nativeTempDir);
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            throw new IllegalStateException("初始化应用目录失败", e);
        }
    }

    public Path appHome() {
        return appHome;
    }

    public Path downloadDir() {
        return downloadDir;
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
