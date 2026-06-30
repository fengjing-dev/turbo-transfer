'use strict';

// 向 renderer 暴露“受控”的本地能力：当前为无框窗口的标题栏控制。
// 后续会在此追加系统文件选择、读取本地文件用于发送等 Node 能力。
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('turboNative', {
  minimize: () => ipcRenderer.send('window:minimize'),
  toggleMaximize: () => ipcRenderer.send('window:toggle-maximize'),
  close: () => ipcRenderer.send('window:close'),
  chooseDirectory: () => ipcRenderer.invoke('dialog:choose-dir'),
  getNetworkAddresses: () => ipcRenderer.invoke('system:network-addresses')
});
