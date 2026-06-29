package com.fatina.transfer.server;

import com.fatina.transfer.config.AppConfig;
import com.fatina.transfer.config.AppPaths;
import com.fatina.transfer.config.RunMode;
import com.fatina.transfer.peer.PeerDiscoveryService;
import com.fatina.transfer.peer.PeerInfo;
import com.fatina.transfer.server.router.HttpRouter;
import com.fatina.transfer.support.AppLogger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Netty 文件传输服务，既可由桌面壳托管，也可独立命令行启动。
 * @author Fatina 2026/06/29
 */
public final class NettyUploadServer {
    private static volatile NettyUploadServer current;
    private static final AppConfig APP_CONFIG = AppConfig.load();
    private static final AppPaths APP_PATHS = AppPaths.load();
    private static final AppLogger LOGGER = AppLogger.forName(APP_CONFIG.serverLogFile());

    public static final int DEFAULT_PORT = APP_CONFIG.serverPort();
    private static final int MAX_HTTP_BODY_SIZE = 64 * 1024 * 1024;

    private final int port;
    private final PeerDiscoveryService peerDiscoveryService;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyUploadServer(int port) {
        this.port = port;
        this.peerDiscoveryService = new PeerDiscoveryService(APP_CONFIG, APP_CONFIG.nodeName(), port);
    }

    public synchronized void start() throws InterruptedException {
        if (isRunning()) {
            return;
        }
        current = this;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(MAX_HTTP_BODY_SIZE));
                            pipeline.addLast(new ChunkedWriteHandler());
                            pipeline.addLast(new HttpRouter());
                        }
                    });

            ChannelFuture bindFuture = bootstrap.bind(port).sync();
            serverChannel = bindFuture.channel();
            peerDiscoveryService.start();
            LOGGER.info("服务启动成功，端口=" + port + "，传输目录=" + transferDir());
        } catch (InterruptedException e) {
            LOGGER.error("服务启动被中断", e);
            stop();
            throw e;
        } catch (Exception e) {
            LOGGER.error("服务启动失败", e);
            stop();
            throw e;
        }
    }

    public void startBlocking() throws InterruptedException {
        start();
        printStartupBanner();
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    public synchronized void stop() {
        peerDiscoveryService.stop();
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup = null;
        }
        if (current == this) {
            current = null;
        }
        LOGGER.info("服务已停止");
    }

    public synchronized boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public int port() {
        return port;
    }

    public AppPaths appPaths() {
        return APP_PATHS;
    }

    /** 当前传输目录（实时读取，支持运行时切换）。 */
    public static String transferDir() {
        return APP_PATHS.transferDir().toString();
    }

    /** 共享的应用目录实例，供 controller 切换传输目录。 */
    public static AppPaths sharedPaths() {
        return APP_PATHS;
    }

    public String localConsoleUrl() {
        return "http://" + APP_CONFIG.localConsoleHost() + ":" + port + "/";
    }

    public List<PeerInfo> discoveredPeers() {
        return new ArrayList<>(peerDiscoveryService.peers());
    }

    public static NettyUploadServer current() {
        return current;
    }

    public boolean discoveryEnabled() {
        return peerDiscoveryService.isEnabled();
    }

    public PeerInfo localPeer() {
        return peerDiscoveryService.localPeer();
    }

    public List<String> getAccessUrls() {
        List<String> urls = new ArrayList<>();
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
                    if (hostAddress.contains(":")) {
                        if (hostAddress.contains("%")) {
                            continue;
                        }
                        urls.add("http://[" + hostAddress + "]:" + port + "/");
                    } else {
                        urls.add("http://" + hostAddress + ":" + port + "/");
                    }
                }
            }
        } catch (Exception ignored) {
            // 地址枚举失败时使用本机入口兜底。
        }

        if (urls.isEmpty()) {
            urls.add(localConsoleUrl());
        }
        return urls;
    }

    private void printStartupBanner() {
        System.out.println("==================================================");
        System.out.println(APP_CONFIG.appName() + " 已启动");
        System.out.println("运行模式: " + describeMode(APP_CONFIG.runMode()));
        for (String url : getAccessUrls()) {
            System.out.println("访问地址: " + url);
        }
        System.out.println("安装目录: " + APP_PATHS.appHome());
        System.out.println("传输目录: " + transferDir());
        System.out.println("==================================================");
    }

    private String describeMode(RunMode runMode) {
        return switch (runMode) {
            case SINGLE -> "单端模式";
            case DUAL -> "双端模式";
            case MIXED -> "混合模式";
        };
    }

    public static void main(String[] args) throws InterruptedException {
        new NettyUploadServer(DEFAULT_PORT).startBlocking();
    }
}
