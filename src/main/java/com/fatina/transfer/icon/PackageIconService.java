package com.fatina.transfer.icon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 安装包图标服务，负责缓存与策略分发。
 * @author Fatina 2026/06/29
 */
public class PackageIconService {
    private final IconExtractorConfig config;
    private final PackageIconExtractorFactory extractorFactory;

    public PackageIconService() {
        this.config = IconExtractorConfig.load();
        this.extractorFactory = new PackageIconExtractorFactory(List.of(
                new ApkIconExtractor(),
                new IpaIconExtractor(),
                new WindowsPackageIconExtractor(),
                new DmgIconExtractor()
        ));
    }

    public IconExtractionResult extract(File file) throws Exception {
        String extension = getFileExtension(file.getName());
        if (extension.isBlank()) {
            return null;
        }

        PackageIconExtractor extractor = extractorFactory.getExtractor(extension);
        if (extractor == null) {
            return null;
        }

        Path cachedPath = resolveCachePath(file);
        if (Files.exists(cachedPath)) {
            return new IconExtractionResult(Files.readAllBytes(cachedPath), "image/png");
        }

        IconExtractionResult result = extractor.extract(file, config);
        if (result == null || result.bytes() == null || result.bytes().length == 0) {
            return null;
        }

        Files.createDirectories(config.cacheDir());
        Files.write(cachedPath, result.bytes());
        return result;
    }

    private Path resolveCachePath(File file) throws Exception {
        String key = file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String fileName = HexFormat.of().formatHex(hash) + ".png";
        return config.cacheDir().resolve(fileName);
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return "";
        }
        return fileName.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }
}
