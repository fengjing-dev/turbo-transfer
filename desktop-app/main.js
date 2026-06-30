'use strict';

const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const http = require('http');

// 开发态：直接用 target/classes + 已下载依赖运行 Java 后端，改完后端只需 mvn compile。
// 打包态后续会替换为内嵌 JRE + 后端 jar。
const APP_ROOT = app.isPackaged ? path.dirname(process.execPath) : path.resolve(__dirname, '..');
const BACKEND_ROOT = app.isPackaged ? path.join(process.resourcesPath, 'backend') : path.resolve(__dirname, '..');
const BACKEND_PORT = 8080;
const BACKEND_URL = `http://127.0.0.1:${BACKEND_PORT}`;
const BACKEND_MAIN_CLASS = 'com.fatina.transfer.server.NettyUploadServer';
const BACKEND_JAR = 'TurboTransfer.jar';

let backendProcess = null;
let mainWindow = null;

function resolveJavaCommand() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java';
  const bundledJava = path.join(process.resourcesPath, 'jre', 'bin', executable);
  if (app.isPackaged && fs.existsSync(bundledJava)) {
    return bundledJava;
  }
  return 'java';
}

function resolveBackendClasspath() {
  if (app.isPackaged) {
    return [
      path.join(BACKEND_ROOT, BACKEND_JAR),
      path.join(BACKEND_ROOT, 'lib', '*')
    ].join(path.delimiter);
  }

  return [
    path.join(BACKEND_ROOT, 'target', 'classes'),
    path.join(BACKEND_ROOT, 'target', 'app', 'lib', '*')
  ].join(path.delimiter);
}

function startBackend() {
  const classpath = resolveBackendClasspath();

  backendProcess = spawn(resolveJavaCommand(), [
    '-Dfile.encoding=UTF-8',
    `-Dturbo.transfer.app.home=${APP_ROOT}`,
    '-cp',
    classpath,
    BACKEND_MAIN_CLASS
  ], {
    cwd: APP_ROOT,
    windowsHide: true
  });

  backendProcess.stdout.on('data', (data) => process.stdout.write(`[backend] ${data}`));
  backendProcess.stderr.on('data', (data) => process.stderr.write(`[backend] ${data}`));
  backendProcess.on('exit', (code) => console.log(`[backend] process exited with code ${code}`));
}

function waitForBackend(retries = 60, intervalMs = 500) {
  return new Promise((resolve, reject) => {
    const attempt = (left) => {
      const request = http.get(`${BACKEND_URL}/api/files`, (response) => {
        response.resume();
        resolve();
      });
      request.on('error', () => {
        if (left <= 0) {
          reject(new Error('Java 后端在预期时间内未就绪'));
          return;
        }
        setTimeout(() => attempt(left - 1), intervalMs);
      });
    };
    attempt(retries);
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 960,
    minHeight: 640,
    show: false,
    frame: false,
    backgroundColor: '#00000000',
    backgroundMaterial: 'acrylic',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  mainWindow.once('ready-to-show', () => mainWindow.show());

  // 设 TT_DEV=1 时自动打开 DevTools，并补回移除菜单后失效的 F12 / Ctrl+R 快捷键。
  if (process.env.TT_DEV) {
    mainWindow.webContents.openDevTools({ mode: 'detach' });
    mainWindow.webContents.on('before-input-event', (event, input) => {
      if (input.type !== 'keyDown') {
        return;
      }
      if (input.key === 'F12') {
        mainWindow.webContents.toggleDevTools();
      } else if (input.control && input.key.toLowerCase() === 'r') {
        mainWindow.webContents.reload();
      }
    });
  }
}

function stopBackend() {
  if (!backendProcess || backendProcess.killed) {
    return;
  }
  if (process.platform === 'win32') {
    // Windows 下 child.kill 杀不掉子树，用 taskkill 连同子进程一起结束。
    spawn('taskkill', ['/pid', String(backendProcess.pid), '/T', '/F']);
  } else {
    backendProcess.kill();
  }
  backendProcess = null;
}

app.whenReady().then(async () => {
  // 移除 Electron 默认菜单栏（File/Edit/View...），无框窗口改用自定义标题栏。
  Menu.setApplicationMenu(null);

  // 无框窗口的标题栏按钮通过 IPC 控制窗口。
  ipcMain.on('window:minimize', () => mainWindow && mainWindow.minimize());
  ipcMain.on('window:toggle-maximize', () => {
    if (!mainWindow) {
      return;
    }
    if (mainWindow.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow.maximize();
    }
  });
  ipcMain.on('window:close', () => mainWindow && mainWindow.close());

  // 原生目录选择对话框，供“设置 - 传输目录”使用。
  ipcMain.handle('dialog:choose-dir', async () => {
    const result = await dialog.showOpenDialog(mainWindow, { properties: ['openDirectory'] });
    if (result.canceled || result.filePaths.length === 0) {
      return null;
    }
    return result.filePaths[0];
  });

  // 设 TT_BACKEND_EXTERNAL=1 时不自拉后端，改为连接已在 IDEA 里运行/调试的后端。
  if (!process.env.TT_BACKEND_EXTERNAL) {
    startBackend();
  }
  try {
    await waitForBackend();
  } catch (error) {
    console.error(error.message);
  }
  createWindow();
});

app.on('window-all-closed', () => {
  stopBackend();
  app.quit();
});

app.on('before-quit', stopBackend);
