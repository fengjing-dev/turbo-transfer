package com.fatina.transfer.server.router;

import com.fatina.transfer.server.ClientTracker;
import com.fatina.transfer.server.controller.ClientController;
import com.fatina.transfer.server.controller.DownloadController;
import com.fatina.transfer.server.controller.FileController;
import com.fatina.transfer.server.controller.FilePreviewController;
import com.fatina.transfer.server.controller.PackageIconController;
import com.fatina.transfer.server.controller.PeerController;
import com.fatina.transfer.server.controller.SettingsController;
import com.fatina.transfer.server.controller.StaticResourceController;
import com.fatina.transfer.server.controller.UploadController;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 唯一的 Netty 入站处理器：按方法 + 路径将请求路由到对应 controller，替代原先的 if-else 巨型 handler。
 * 静态资源 controller 兜底放最后。
 * @author Fatina 2026/06/29
 */
public class HttpRouter extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final List<HttpController> controllers = List.of(
            new FileController(),
            new DownloadController(),
            new PackageIconController(),
            new FilePreviewController(),
            new PeerController(),
            new ClientController(),
            new SettingsController(),
            new UploadController(),
            new StaticResourceController()
    );

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String path = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8).path();
        HttpMethod method = request.method();

        if (path.startsWith("/api/") || path.equals("/upload/chunk")) {
            ClientTracker.instance().recordAccess(
                    (InetSocketAddress) ctx.channel().remoteAddress(),
                    request.headers().get(HttpHeaderNames.USER_AGENT),
                    stripQuotes(request.headers().get("Sec-CH-UA-Model")),
                    stripQuotes(request.headers().get("Sec-CH-UA-Platform")),
                    stripQuotes(request.headers().get("Sec-CH-UA-Platform-Version")));
        }

        for (HttpController controller : controllers) {
            if (controller.supports(method, path)) {
                controller.handle(ctx, request, path);
                return;
            }
        }
        HttpResponses.error(ctx, HttpResponseStatus.NOT_FOUND);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("请求处理异常: " + cause.getMessage());
        ctx.close();
    }

    private static String stripQuotes(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
