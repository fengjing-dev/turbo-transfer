package com.fatina.transfer.desktop;

import com.fatina.transfer.config.AppConfig;
import com.fatina.transfer.server.NettyUploadServer;
import com.fatina.transfer.support.AppLogger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * 最小桌面宿主壳，只负责服务托管、地址展示和本地入口。
 * @author Fatina 2026/06/29
 */
public final class DesktopMainFrame extends JFrame {
    private static final AppConfig APP_CONFIG = AppConfig.load();
    private static final AppLogger LOGGER = AppLogger.forName(APP_CONFIG.desktopLogFile());

    private final NettyUploadServer server;
    private final JLabel statusLabel = new JLabel("服务状态：未启动");
    private final JLabel modeLabel = new JLabel();
    private final JLabel homeLabel = new JLabel();
    private final JLabel downloadLabel = new JLabel();
    private final JTextArea addressArea = new JTextArea(6, 48);
    private final JButton startButton = new JButton("启动服务");
    private final JButton stopButton = new JButton("停止服务");
    private final JButton openConsoleButton = new JButton("打开本机控制台");
    private final JButton openDownloadButton = new JButton("打开下载目录");

    public DesktopMainFrame(NettyUploadServer server) {
        this.server = server;
        setTitle(APP_CONFIG.appName() + " Host");
        setSize(760, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initUi();
        bindEvents();
        refreshView();
    }

    public void startServerOnLaunch() {
        runAsync(() -> {
            server.start();
            LOGGER.info("桌面宿主自动启动服务成功");
            onUi(this::refreshView);
        }, "启动服务失败");
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createTitledBorder("主机信息"));

        homeLabel.setText("安装目录：" + server.appPaths().appHome());
        downloadLabel.setText("下载目录：" + server.appPaths().downloadDir());
        modeLabel.setText("运行模式：" + describeMode(APP_CONFIG.runMode()));
        infoPanel.add(statusLabel);
        infoPanel.add(modeLabel);
        infoPanel.add(homeLabel);
        infoPanel.add(downloadLabel);

        JPanel addressPanel = new JPanel(new BorderLayout());
        addressPanel.setBorder(BorderFactory.createTitledBorder("局域网访问地址"));
        addressArea.setEditable(false);
        addressArea.setLineWrap(true);
        addressArea.setWrapStyleWord(true);
        addressPanel.add(new JScrollPane(addressArea), BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actionPanel.add(startButton);
        actionPanel.add(stopButton);
        actionPanel.add(openConsoleButton);
        actionPanel.add(openDownloadButton);

        root.add(infoPanel, BorderLayout.NORTH);
        root.add(addressPanel, BorderLayout.CENTER);
        root.add(actionPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void bindEvents() {
        startButton.addActionListener(event -> runAsync(() -> {
            server.start();
            LOGGER.info("用户手动启动服务成功");
            onUi(this::refreshView);
        }, "启动服务失败"));

        stopButton.addActionListener(event -> runAsync(() -> {
            server.stop();
            LOGGER.info("用户手动停止服务");
            onUi(this::refreshView);
        }, "停止服务失败"));

        openConsoleButton.addActionListener(event -> runAsync(() -> {
            URI uri = URI.create(server.localConsoleUrl());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(uri);
                LOGGER.info("打开本机控制台: " + uri);
            }
        }, "打开本机控制台失败"));

        openDownloadButton.addActionListener(event -> runAsync(() -> {
            Path downloadDir = server.appPaths().downloadDir();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(downloadDir.toFile());
                LOGGER.info("打开下载目录: " + downloadDir);
            }
        }, "打开下载目录失败"));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                server.stop();
                LOGGER.info("桌面宿主已关闭");
            }
        });
    }

    private void refreshView() {
        boolean running = server.isRunning();
        statusLabel.setText("服务状态：" + (running ? "运行中" : "已停止") + "   端口：" + server.port());
        modeLabel.setText("运行模式：" + describeMode(APP_CONFIG.runMode()));
        homeLabel.setText("安装目录：" + server.appPaths().appHome());
        downloadLabel.setText("下载目录：" + server.appPaths().downloadDir());

        List<String> urls = running
                ? server.getAccessUrls()
                : List.of("服务未启动，局域网地址将在启动后显示。");
        addressArea.setText(String.join(System.lineSeparator(), urls));

        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        openConsoleButton.setEnabled(running);
    }

    private void runAsync(ThrowingRunnable runnable, String errorTitle) {
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                LOGGER.error(errorTitle, e);
                onUi(() -> JOptionPane.showMessageDialog(this, e.getMessage(), errorTitle, JOptionPane.ERROR_MESSAGE));
            }
        }, "desktop-shell-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void onUi(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    private String describeMode(com.fatina.transfer.config.RunMode runMode) {
        return switch (runMode) {
            case SINGLE -> "单端模式";
            case DUAL -> "双端模式";
            case MIXED -> "混合模式";
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
