package com.fatina.transfer.preview;

/**
 * 文件预览结果。
 *
 * @author Fatina 2026/06/29
 * @param bytes PNG 预览图
 * @param contentType 响应类型
 */
public record FilePreviewResult(byte[] bytes, String contentType) {
}
