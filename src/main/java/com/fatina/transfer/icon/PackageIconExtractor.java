package com.fatina.transfer.icon;

import java.io.File;
import java.util.Set;

/**
 * 安装包图标提取策略。
 * @author Fatina 2026/06/29
 */
public interface PackageIconExtractor {

    Set<String> supportedExtensions();

    IconExtractionResult extract(File file, IconExtractorConfig config) throws Exception;
}
