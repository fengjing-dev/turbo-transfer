package com.fatina.transfer.icon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安装包图标提取策略工厂。
 * @author Fatina 2026/06/29
 */
public class PackageIconExtractorFactory {
    private final Map<String, PackageIconExtractor> extractorByExtension;

    public PackageIconExtractorFactory(List<PackageIconExtractor> extractors) {
        this.extractorByExtension = new HashMap<>();
        for (PackageIconExtractor extractor : extractors) {
            for (String extension : extractor.supportedExtensions()) {
                extractorByExtension.put(extension, extractor);
            }
        }
    }

    public PackageIconExtractor getExtractor(String extension) {
        return extractorByExtension.get(extension);
    }
}
