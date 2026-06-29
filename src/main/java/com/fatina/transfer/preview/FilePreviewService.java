package com.fatina.transfer.preview;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 文件预览服务，负责缓存与策略分发。
 * @author Fatina 2026/06/29
 */
public class FilePreviewService {
    private final PreviewConfig config;
    private final FilePreviewExtractorFactory extractorFactory;

    public FilePreviewService() {
        this.config = PreviewConfig.load();
        this.extractorFactory = new FilePreviewExtractorFactory(List.of(
                new PdfPreviewExtractor(),
                new PsdPreviewExtractor()
        ));
    }

    public FilePreviewResult extract(File file) throws Exception {
        String extension = getFileExtension(file.getName());
        if (extension.isBlank()) {
            return null;
        }

        FilePreviewExtractor extractor = extractorFactory.getExtractor(extension);
        if (extractor == null) {
            return null;
        }

        Path cachedPath = resolveCachePath(file);
        if (Files.exists(cachedPath)) {
            return new FilePreviewResult(Files.readAllBytes(cachedPath), "image/png");
        }

        FilePreviewResult result = extractor.extract(file, config);
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
        byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
        return config.cacheDir().resolve(HexFormat.of().formatHex(hash) + ".png");
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return "";
        }
        return fileName.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }
}
