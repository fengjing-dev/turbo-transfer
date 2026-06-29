package com.fatina.transfer.preview;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件预览策略工厂。
 * @author Fatina 2026/06/29
 */
public class FilePreviewExtractorFactory {
    private final Map<String, FilePreviewExtractor> extractorByExtension = new HashMap<>();

    public FilePreviewExtractorFactory(List<FilePreviewExtractor> extractors) {
        for (FilePreviewExtractor extractor : extractors) {
            for (String extension : extractor.supportedExtensions()) {
                extractorByExtension.put(extension, extractor);
            }
        }
    }

    public FilePreviewExtractor getExtractor(String extension) {
        return extractorByExtension.get(extension);
    }
}
