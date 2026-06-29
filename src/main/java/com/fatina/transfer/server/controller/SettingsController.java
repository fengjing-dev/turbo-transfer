package com.fatina.transfer.server.controller;

import com.fatina.transfer.server.NettyUploadServer;
import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 传输目录设置：GET 读取当前目录，POST 切换并持久化。
 * 路径：/api/settings/transfer-dir
 * @author Fatina 2026/06/29
 */
public class SettingsController implements HttpController {

    private static final String PATH = "/api/settings/transfer-dir";

    @Override
    public boolean supports(HttpMethod method, String path) {
        return PATH.equals(path) && (HttpMethod.GET.equals(method) || HttpMethod.POST.equals(method));
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        if (HttpMethod.GET.equals(request.method())) {
            HttpResponses.json(ctx, HttpResponseStatus.OK, dirJson(NettyUploadServer.transferDir()));
            return;
        }

        String target = request.content().toString(StandardCharsets.UTF_8).trim();
        if (target.isEmpty()) {
            HttpResponses.error(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        try {
            Path applied = NettyUploadServer.sharedPaths().changeTransferDir(Path.of(target));
            HttpResponses.json(ctx, HttpResponseStatus.OK, dirJson(applied.toString()));
        } catch (Exception e) {
            HttpResponses.text(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "切换传输目录失败: " + e.getMessage());
        }
    }

    private String dirJson(String dir) {
        return "{\"dir\":\"" + HttpResponses.escapeJson(dir) + "\"}";
    }
}
