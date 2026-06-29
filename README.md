# TurboTransfer

TurboTransfer 是一套局域网文件传输应用，支持两种运行方式：

- 单端模式：一台机器作为宿主，其他设备通过浏览器访问
- 双端模式：同一局域网内多台设备运行同一程序，自动发现彼此并展示节点列表

项目当前以 Java 17 + Netty 为核心，前端采用静态网页承载上传、下载、预览和设备发现页面。

## 特性

- 支持大文件上传和分块写入
- 支持文件列表和安全下载
- 支持图片预览
- 支持 `PDF` 和 `PSD` 预览
- 支持安装包图标提取
- 支持局域网节点发现
- 支持桌面宿主启动和命令行服务启动
- 支持 Windows 安装包打包

## 架构

源码已按职责拆分：

- `config`：全局配置、运行模式、目录规则
- `desktop`：桌面宿主入口和窗口
- `server`：Netty 服务和生命周期
- `server/handler`：HTTP 路由、上传处理、静态资源处理
- `icon`：安装包图标提取
- `preview`：文件预览提取
- `peer`：局域网节点发现
- `support`：日志和基础支撑

## 启动方式

### 1. 本地开发启动

在项目根目录执行：

```bat
start-desktop.bat
```

或者只启动服务端：

```bat
start-server.bat
```

脚本会先尝试构建，再从 `target\app` 启动。

### 2. 打包安装

先构建分发文件：

```bat
build-dist.bat
```

再打包 Windows 安装程序：

```bat
package-installer.bat
```

安装产物输出到 `dist\`。

## 配置

全局配置文件位于：

- [src/main/resources/application.properties](src/main/resources/application.properties)

常用配置项：

- `turbo.transfer.app.name`：应用名
- `turbo.transfer.node.name`：节点名
- `turbo.transfer.server.port`：服务端口
- `turbo.transfer.run.mode`：`single` / `dual` / `mixed`
- `turbo.transfer.discovery.enabled`：是否启用局域网发现
- `turbo.transfer.discovery.port`：发现端口
- `turbo.transfer.discovery.interval-seconds`：心跳间隔
- `turbo.transfer.log.server-file`：服务日志文件名
- `turbo.transfer.log.desktop-file`：桌面日志文件名
- `turbo.transfer.console.host`：本机控制台地址

运行时目录默认跟随启动目录或安装目录：

- `log`
- `downloads`
- `cache`

## 主要接口

- `GET /api/files`：文件列表
- `GET /api/download?name=...`：文件下载
- `GET /api/pkg/icon?name=...`：安装包图标
- `GET /api/file/preview?name=...`：PDF/PSD 预览
- `GET /api/peers`：局域网节点列表
- `POST /upload`：文件上传
- `POST /upload/chunk`：分块上传

## 前端页面

静态页面位于：

- [src/main/resources/web/index.html](src/main/resources/web/index.html)
- [src/main/resources/web/app.js](src/main/resources/web/app.js)

页面提供：

- 上传入口
- 文件网格展示
- 局域网设备列表

## 打包产物

分发布局在 `target\app`，安装后依赖会落在选择的安装目录下。

详细说明见：

- [PACKAGING.md](PACKAGING.md)

## 开发说明

- 代码已按职责分包，不要继续把新类堆到单一包下
- 目录、端口、日志文件名、运行模式优先从 `application.properties` 和系统属性读取
- 安装包扩展必须走工厂和策略模式，避免在路由中堆 `if-else`
- 当前局域网发现采用 UDP 广播，目标是先让双端可发现，后续再接点对点传输

