package com.fatina.transfer.desktop;

import com.fatina.transfer.config.AppConfig;
import com.fatina.transfer.server.NettyUploadServer;
import javax.swing.SwingUtilities;

/**
 * 桌面宿主入口。
 * @author Fatina 2026/06/29
 */
public final class DesktopLauncher {
    private static final AppConfig APP_CONFIG = AppConfig.load();

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopMainFrame frame = new DesktopMainFrame(new NettyUploadServer(APP_CONFIG.serverPort()));
            frame.setVisible(true);
            frame.startServerOnLaunch();
        });
    }
}
