package com.fatina.transfer.server.controller;

import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.InputStream;

/**
 * 静态资源（手机端网页等）：GET 其余路径，从 classpath /web 读取。兜底放路由最后。
 * @author Fatina 2026/06/29
 */
public class StaticResourceController implements HttpController {

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method) && !path.startsWith("/api") && !path.startsWith("/upload");
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) throws Exception {
        String resourcePath = "/".equals(path) ? "/index.html" : path;
        try (InputStream in = getClass().getResourceAsStream("/web" + resourcePath)) {
            if (in == null) {
                HttpResponses.error(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }
            byte[] body = in.readAllBytes();
            HttpResponses.bytes(ctx, HttpResponseStatus.OK, contentType(resourcePath), body);
        }
    }

    private String contentType(String resourcePath) {
        if (resourcePath.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (resourcePath.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (resourcePath.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        return "application/octet-stream";
    }
}
