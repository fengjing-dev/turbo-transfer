package com.fatina.transfer.server.handler;

import com.fatina.transfer.icon.IconExtractionResult;
import com.fatina.transfer.icon.PackageIconService;
import com.fatina.transfer.peer.PeerInfo;
import com.fatina.transfer.preview.FilePreviewResult;
import com.fatina.transfer.preview.FilePreviewService;
import com.fatina.transfer.server.NettyUploadServer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 静态资源与 HTTP API 处理器。
 * @author Fatina 2026/06/29
 */
public class HttpStaticResourceHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final PackageIconService PACKAGE_ICON_SERVICE = new PackageIconService();
    private static final FilePreviewService FILE_PREVIEW_SERVICE = new FilePreviewService();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();

        if (request.method() == HttpMethod.GET && "/api/files".equals(uri)) {
            writeJson(ctx, HttpResponseStatus.OK, buildFilesJson());
            return;
        }

        if (request.method() == HttpMethod.GET && "/api/peers".equals(uri)) {
            writeJson(ctx, HttpResponseStatus.OK, buildPeersJson());
            return;
        }

        if (request.method() == HttpMethod.GET && uri.startsWith("/api/pkg/icon")) {
            QueryStringDecoder decoder = new QueryStringDecoder(uri, StandardCharsets.UTF_8);
            var params = decoder.parameters();
            if (!params.containsKey("name") || params.get("name").isEmpty()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            String fileName = params.get("name").get(0);
            File file = new File(new File(NettyUploadServer.UPLOAD_DIR), fileName);
            if (!file.exists() || !file.isFile()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }

            try {
                IconExtractionResult iconResult = PACKAGE_ICON_SERVICE.extract(file);
                if (iconResult != null && iconResult.bytes() != null && iconResult.bytes().length > 0) {
                    FullHttpResponse response = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ctx.alloc().buffer().writeBytes(iconResult.bytes()));
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, iconResult.contentType());
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, iconResult.bytes().length);
                    response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                    return;
                }
            } catch (Throwable e) {
                System.err.println("安装包图标解析异常，已安全隔离: " + e.getMessage());
            }
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        if (request.method() == HttpMethod.GET && uri.startsWith("/api/file/preview")) {
            QueryStringDecoder decoder = new QueryStringDecoder(uri, StandardCharsets.UTF_8);
            var params = decoder.parameters();
            if (!params.containsKey("name") || params.get("name").isEmpty()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            String fileName = params.get("name").get(0);
            File file = new File(new File(NettyUploadServer.UPLOAD_DIR), fileName);
            if (!file.exists() || !file.isFile()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }

            try {
                FilePreviewResult previewResult = FILE_PREVIEW_SERVICE.extract(file);
                if (previewResult != null && previewResult.bytes() != null && previewResult.bytes().length > 0) {
                    FullHttpResponse response = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ctx.alloc().buffer().writeBytes(previewResult.bytes()));
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, previewResult.contentType());
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, previewResult.bytes().length);
                    response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                    return;
                }
            } catch (Throwable e) {
                System.err.println("文件预览解析异常，已安全隔离: " + e.getMessage());
            }
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        if (request.method() == HttpMethod.GET && uri.startsWith("/api/download")) {
            QueryStringDecoder decoder = new QueryStringDecoder(uri, StandardCharsets.UTF_8);
            var params = decoder.parameters();
            if (!params.containsKey("name") || params.get("name").isEmpty()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            String fileName = params.get("name").get(0);
            File file = new File(new File(NettyUploadServer.UPLOAD_DIR), fileName);
            if (!file.exists() || !file.isFile()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }

            RandomAccessFile raf = new RandomAccessFile(file, "r");
            long fileLength = raf.length();

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);

            String encodedName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedName + "\"");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

            ctx.write(response);
            ctx.writeAndFlush(new ChunkedFile(raf, 0, fileLength, 8192)).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        if (request.method() == HttpMethod.GET && !uri.startsWith("/upload")) {
            if ("/".equals(uri)) {
                uri = "/index.html";
            }
            InputStream in = getClass().getResourceAsStream("/web" + uri);
            if (in == null) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }

            byte[] body = in.readAllBytes();
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ctx.alloc().buffer().writeBytes(body));

            if (uri.endsWith(".html")) {
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
            } else if (uri.endsWith(".js")) {
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/javascript; charset=UTF-8");
            }

            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireChannelRead(request.retain());
        }
    }

    private String buildFilesJson() {
        File dir = new File(NettyUploadServer.UPLOAD_DIR);
        File[] files = dir.listFiles();
        StringBuilder json = new StringBuilder("[");
        if (files != null) {
            int addedCount = 0;
            for (File file : files) {
                if (file.isFile()) {
                    if (addedCount > 0) {
                        json.append(',');
                    }
                    json.append(String.format("{\"name\":\"%s\",\"size\":%d}",
                            file.getName().replace("\"", "\\\""), file.length()));
                    addedCount++;
                }
            }
        }
        json.append(']');
        return json.toString();
    }

    private String buildPeersJson() {
        NettyUploadServer server = NettyUploadServer.current();
        List<PeerInfo> peers = server == null ? List.of() : server.discoveredPeers();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < peers.size(); i++) {
            PeerInfo peer = peers.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"nodeId\":\"").append(escape(peer.nodeId())).append("\",")
                    .append("\"nodeName\":\"").append(escape(peer.nodeName())).append("\",")
                    .append("\"host\":\"").append(escape(peer.host())).append("\",")
                    .append("\"port\":").append(peer.port()).append(',')
                    .append("\"mode\":\"").append(peer.runMode().name()).append("\",")
                    .append("\"baseUrl\":\"").append(escape(peer.baseUrl())).append("\",")
                    .append("\"lastSeen\":").append(peer.lastSeenEpochMillis())
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void writeJson(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                ctx.alloc().buffer().writeBytes(json.getBytes(StandardCharsets.UTF_8))
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
