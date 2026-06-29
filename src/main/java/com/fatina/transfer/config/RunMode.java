package com.fatina.transfer.config;

/**
 * 应用运行模式。
 * SINGLE 表示单端宿主模式，DUAL 表示双端对等模式，MIXED 预留给后续互联互传能力。
 * @author Fatina 2026/06/29
 */
public enum RunMode {
    SINGLE,
    DUAL,
    MIXED;

    public static RunMode from(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE;
        }

        return switch (value.trim().toUpperCase()) {
            case "DUAL" -> DUAL;
            case "MIXED" -> MIXED;
            default -> SINGLE;
        };
    }
}
