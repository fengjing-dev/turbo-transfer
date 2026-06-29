package com.fatina.transfer.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 统一应用级配置入口，避免端口、文件名、资源路径等常量散落。
 * @author Fatina 2026/06/29
 */
public final class AppConfig {
    static final String CONFIG_RESOURCE = "/application.properties";
    private static final String APP_NAME_KEY = "turbo.transfer.app.name";
    private static final String NODE_NAME_KEY = "turbo.transfer.node.name";
    private static final String SERVER_PORT_KEY = "turbo.transfer.server.port";
    private static final String RUN_MODE_KEY = "turbo.transfer.run.mode";
    private static final String DISCOVERY_ENABLED_KEY = "turbo.transfer.discovery.enabled";
    private static final String DISCOVERY_PORT_KEY = "turbo.transfer.discovery.port";
    private static final String DISCOVERY_INTERVAL_SECONDS_KEY = "turbo.transfer.discovery.interval-seconds";
    private static final String SERVER_LOG_FILE_KEY = "turbo.transfer.log.server-file";
    private static final String DESKTOP_LOG_FILE_KEY = "turbo.transfer.log.desktop-file";
    private static final String LOCAL_CONSOLE_HOST_KEY = "turbo.transfer.console.host";

    private final String appName;
    private final String nodeName;
    private final int serverPort;
    private final RunMode runMode;
    private final boolean discoveryEnabled;
    private final int discoveryPort;
    private final int discoveryIntervalSeconds;
    private final String serverLogFile;
    private final String desktopLogFile;
    private final String localConsoleHost;

    private AppConfig(
            String appName,
            String nodeName,
            int serverPort,
            RunMode runMode,
            boolean discoveryEnabled,
            int discoveryPort,
            int discoveryIntervalSeconds,
            String serverLogFile,
            String desktopLogFile,
            String localConsoleHost
    ) {
        this.appName = appName;
        this.nodeName = nodeName;
        this.serverPort = serverPort;
        this.runMode = runMode;
        this.discoveryEnabled = discoveryEnabled;
        this.discoveryPort = discoveryPort;
        this.discoveryIntervalSeconds = discoveryIntervalSeconds;
        this.serverLogFile = serverLogFile;
        this.desktopLogFile = desktopLogFile;
        this.localConsoleHost = localConsoleHost;
    }

    public static AppConfig load() {
        Properties properties = loadProperties();
        return new AppConfig(
                resolve(properties, APP_NAME_KEY, "TurboTransfer"),
                resolve(properties, NODE_NAME_KEY, detectHostName()),
                Integer.parseInt(resolve(properties, SERVER_PORT_KEY, "8080")),
                RunMode.from(resolve(properties, RUN_MODE_KEY, "single")),
                Boolean.parseBoolean(resolve(properties, DISCOVERY_ENABLED_KEY, "true")),
                Integer.parseInt(resolve(properties, DISCOVERY_PORT_KEY, "37020")),
                Integer.parseInt(resolve(properties, DISCOVERY_INTERVAL_SECONDS_KEY, "5")),
                resolve(properties, SERVER_LOG_FILE_KEY, "server.log"),
                resolve(properties, DESKTOP_LOG_FILE_KEY, "desktop.log"),
                resolve(properties, LOCAL_CONSOLE_HOST_KEY, "127.0.0.1")
        );
    }

    public static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = AppConfig.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("加载应用配置失败", e);
        }
    }

    public static String resolve(Properties properties, String key, String defaultValue) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        String propertyValue = properties.getProperty(key);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        return defaultValue;
    }

    public String appName() {
        return appName;
    }

    public int serverPort() {
        return serverPort;
    }

    public String nodeName() {
        return nodeName;
    }

    public RunMode runMode() {
        return runMode;
    }

    public boolean discoveryEnabled() {
        return discoveryEnabled;
    }

    public int discoveryPort() {
        return discoveryPort;
    }

    public int discoveryIntervalSeconds() {
        return discoveryIntervalSeconds;
    }

    public String serverLogFile() {
        return serverLogFile;
    }

    public String desktopLogFile() {
        return desktopLogFile;
    }

    public String localConsoleHost() {
        return localConsoleHost;
    }

    private static String detectHostName() {
        try {
            String hostName = java.net.InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.isBlank()) {
                return hostName;
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return "TurboTransfer";
    }
}
