package com.fatina.transfer.icon;

/**
 * 图标提取结果。
 *
 * @author Fatina 2026/06/29
 * @param bytes PNG 图标二进制
 * @param contentType 响应内容类型
 */
public record IconExtractionResult(byte[] bytes, String contentType) {
}
