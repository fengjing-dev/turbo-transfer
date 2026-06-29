package com.fatina.transfer.preview;

import java.io.File;
import java.util.Set;

/**
 * 文件预览提取策略。
 * @author Fatina 2026/06/29
 */
public interface FilePreviewExtractor {

    Set<String> supportedExtensions();

    FilePreviewResult extract(File file, PreviewConfig config) throws Exception;
}
