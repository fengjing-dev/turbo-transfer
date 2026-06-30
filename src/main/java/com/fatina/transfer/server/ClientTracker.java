package com.fatina.transfer.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跟踪连接到本机的客户端 IP 及最后活跃时间。
 */
public final class ClientTracker {

    private static final ClientTracker INSTANCE = new ClientTracker();

    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    private ClientTracker() {}

    public static ClientTracker instance() {
        return INSTANCE;
    }

    public void recordAccess(InetSocketAddress remoteAddress, String userAgent,
                             String deviceModel, String platform, String platformVersion) {
        if (remoteAddress == null) {
            return;
        }
        String ip = remoteAddress.getAddress().getHostAddress();
        if (ip.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(ip)) {
            return;
        }
        clients.compute(ip, (key, existing) -> {
            String model = firstNonEmpty(deviceModel, existing != null ? existing.deviceModel() : null);
            String plat = firstNonEmpty(platform, existing != null ? existing.platform() : null);
            String platVer = firstNonEmpty(platformVersion, existing != null ? existing.platformVersion() : null);
            String ua = firstNonEmpty(userAgent, existing != null ? existing.userAgent() : null);
            long count = existing != null ? existing.requestCount() + 1 : 1;
            return new ClientInfo(ip, ua, model, plat, platVer, System.currentTimeMillis(), count);
        });
    }

    public List<ClientInfo> snapshot() {
        return new ArrayList<>(clients.values());
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }

    public record ClientInfo(String ip, String userAgent, String deviceModel,
                             String platform, String platformVersion,
                             long lastSeenMillis, long requestCount) {}
}
