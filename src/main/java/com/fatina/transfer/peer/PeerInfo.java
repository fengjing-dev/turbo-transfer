package com.fatina.transfer.peer;

import com.fatina.transfer.config.RunMode;

import java.time.Instant;

/**
 * 局域网节点信息。
 * @author Fatina 2026/06/29
 */
public record PeerInfo(
        String nodeId,
        String nodeName,
        String host,
        int port,
        RunMode runMode,
        long lastSeenEpochMillis
) {
    public String baseUrl() {
        return "http://" + host + ":" + port + "/";
    }

    public Instant lastSeen() {
        return Instant.ofEpochMilli(lastSeenEpochMillis);
    }
}
