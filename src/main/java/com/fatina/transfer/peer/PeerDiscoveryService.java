package com.fatina.transfer.peer;

import com.fatina.transfer.config.AppConfig;
import com.fatina.transfer.config.RunMode;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 轻量局域网发现服务。
 * 通过 UDP 广播节点心跳，不依赖外部服务。
 * @author Fatina 2026/06/29
 */
public final class PeerDiscoveryService {
    private static final String PROTOCOL_PREFIX = "TURBO_TRANSFER";
    private static final int SOCKET_TIMEOUT_MILLIS = 1000;
    private static final int BROADCAST_BUFFER_SIZE = 512;

    private final AppConfig appConfig;
    private final String nodeId;
    private final String nodeName;
    private final int serverPort;
    private final RunMode runMode;
    private final int discoveryPort;
    private final int intervalSeconds;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean running;

    public PeerDiscoveryService(AppConfig appConfig, String nodeName, int serverPort) {
        this.appConfig = appConfig;
        this.nodeId = UUID.randomUUID().toString();
        this.nodeName = nodeName;
        this.serverPort = serverPort;
        this.runMode = appConfig.runMode();
        this.discoveryPort = appConfig.discoveryPort();
        this.intervalSeconds = Math.max(1, appConfig.discoveryIntervalSeconds());
    }

    public synchronized void start() {
        if (running || !appConfig.discoveryEnabled() || runMode == RunMode.SINGLE) {
            return;
        }

        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(discoveryPort));
            socket.setBroadcast(true);
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        } catch (SocketException e) {
            throw new IllegalStateException("启动节点发现失败", e);
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "peer-discovery-broadcaster");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::broadcastSafely, 0, intervalSeconds, TimeUnit.SECONDS);

        listenerThread = new Thread(this::listenLoop, "peer-discovery-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        peers.clear();
    }

    public Collection<PeerInfo> peers() {
        return new ArrayList<>(peers.values());
    }

    public PeerInfo localPeer() {
        return new PeerInfo(nodeId, nodeName, detectLocalAddress(), serverPort, runMode, System.currentTimeMillis());
    }

    public boolean isEnabled() {
        return appConfig.discoveryEnabled() && runMode != RunMode.SINGLE;
    }

    private void broadcastSafely() {
        try {
            broadcastOnce();
        } catch (Exception ignored) {
            // 节点发现失败不影响主传输链路。
        }
    }

    private void broadcastOnce() throws IOException {
        DatagramSocket currentSocket = socket;
        if (!running || currentSocket == null) {
            return;
        }

        String payload = serialize(localPeer());
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
                bytes,
                bytes.length,
                InetAddress.getByName("255.255.255.255"),
                discoveryPort
        );
        currentSocket.send(packet);
    }

    private void listenLoop() {
        byte[] buffer = new byte[BROADCAST_BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                PeerInfo peerInfo = deserialize(payload, packet.getAddress().getHostAddress());
                if (peerInfo != null && !nodeId.equals(peerInfo.nodeId())) {
                    peers.put(peerInfo.nodeId(), peerInfo);
                }
            } catch (IOException ignored) {
                // timeout or socket close
            }
        }
    }

    private String serialize(PeerInfo peerInfo) {
        return String.join("|",
                PROTOCOL_PREFIX,
                peerInfo.nodeId(),
                peerInfo.nodeName(),
                Integer.toString(peerInfo.port()),
                peerInfo.runMode().name()
        );
    }

    private PeerInfo deserialize(String payload, String host) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 5 || !PROTOCOL_PREFIX.equals(parts[0])) {
            return null;
        }

        try {
            String nodeId = parts[1];
            String nodeName = parts[2];
            int port = Integer.parseInt(parts[3]);
            RunMode mode = RunMode.from(parts[4]);
            return new PeerInfo(nodeId, nodeName, host, port, mode, System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    private String detectLocalAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String hostAddress = address.getHostAddress();
                    if (!hostAddress.contains(":")) {
                        return hostAddress;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "127.0.0.1";
    }
}
