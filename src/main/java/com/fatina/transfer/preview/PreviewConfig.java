package com.fatina.transfer.preview;

import com.fatina.transfer.config.AppConfig;
import com.fatina.transfer.config.AppPaths;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 文件预览配置。
 * 预览缓存默认落在安装目录下，可通过统一配置或系统属性覆盖。
 * @author Fatina 2026/06/29
 */
public final class PreviewConfig {
    private static final String CACHE_DIR_KEY = "turbo.transfer.preview.cache-dir";

    private final Path cacheDir;

    private PreviewConfig(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    public static PreviewConfig load() {
        AppPaths appPaths = AppPaths.load();
        Properties properties = AppConfig.loadProperties();
        String pathValue = AppConfig.resolve(properties, CACHE_DIR_KEY, appPaths.previewCacheDir().toString());
        return new PreviewConfig(Path.of(pathValue).toAbsolutePath().normalize());
    }

    public Path cacheDir() {
        return cacheDir;
    }
}
