package com.fatina.transfer.icon;

import com.fatina.transfer.config.AppConfig;
import com.fatina.transfer.config.AppPaths;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 图标提取配置。
 * 目录统一从 AppPaths 派生，本类只负责能力开关与 native 临时目录读取。
 * @author Fatina 2026/06/29
 */
public final class IconExtractorConfig {
    private static final String APK_ENABLED_KEY = "turbo.transfer.icon.apk-enabled";
    private static final String EXE_ENABLED_KEY = "turbo.transfer.icon.exe-enabled";
    private static final String DMG_ENABLED_KEY = "turbo.transfer.icon.dmg-enabled";

    private final Path cacheDir;
    private final boolean apkEnabled;
    private final boolean exeEnabled;
    private final boolean dmgEnabled;
    private final Path nativeTempDir;

    private IconExtractorConfig(
            Path cacheDir,
            boolean apkEnabled,
            boolean exeEnabled,
            boolean dmgEnabled,
            Path nativeTempDir
    ) {
        this.cacheDir = cacheDir;
        this.apkEnabled = apkEnabled;
        this.exeEnabled = exeEnabled;
        this.dmgEnabled = dmgEnabled;
        this.nativeTempDir = nativeTempDir;
    }

    public static IconExtractorConfig load() {
        AppPaths appPaths = AppPaths.load();
        Properties properties = AppConfig.loadProperties();
        return new IconExtractorConfig(
                appPaths.iconCacheDir(),
                Boolean.parseBoolean(AppConfig.resolve(properties, APK_ENABLED_KEY, "true")),
                Boolean.parseBoolean(AppConfig.resolve(properties, EXE_ENABLED_KEY, "true")),
                Boolean.parseBoolean(AppConfig.resolve(properties, DMG_ENABLED_KEY, "true")),
                appPaths.nativeTempDir()
        );
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public boolean apkEnabled() {
        return apkEnabled;
    }

    public boolean exeEnabled() {
        return exeEnabled;
    }

    public boolean dmgEnabled() {
        return dmgEnabled;
    }

    public Path nativeTempDir() {
        return nativeTempDir;
    }
}
