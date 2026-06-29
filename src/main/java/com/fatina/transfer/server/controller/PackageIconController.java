package com.fatina.transfer.server.controller;

import com.fatina.transfer.icon.IconExtractionResult;
import com.fatina.transfer.icon.PackageIconService;
import com.fatina.transfer.server.NettyUploadServer;
import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 安装包图标提取：GET /api/pkg/icon?name=...
 * @author Fatina 2026/06/29
 */
public class PackageIconController implements HttpController {

    private static final PackageIconService PACKAGE_ICON_SERVICE = new PackageIconService();

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method) && "/api/pkg/icon".equals(path);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        Map<String, List<String>> params = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8).parameters();
        if (!params.containsKey("name") || params.get("name").isEmpty()) {
            HttpResponses.error(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String fileName = params.get("name").get(0);
        File file = new File(new File(NettyUploadServer.transferDir()), fileName);
        if (!file.exists() || !file.isFile()) {
            HttpResponses.error(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        try {
            IconExtractionResult iconResult = PACKAGE_ICON_SERVICE.extract(file);
            if (iconResult != null && iconResult.bytes() != null && iconResult.bytes().length > 0) {
                HttpResponses.bytes(ctx, HttpResponseStatus.OK, iconResult.contentType(), iconResult.bytes());
                return;
            }
        } catch (Throwable e) {
            System.err.println("安装包图标解析异常，已安全隔离: " + e.getMessage());
        }
        HttpResponses.error(ctx, HttpResponseStatus.NOT_FOUND);
    }
}
