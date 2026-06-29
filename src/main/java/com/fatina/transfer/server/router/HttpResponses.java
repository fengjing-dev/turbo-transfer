package com.fatina.transfer.server.router;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;

/**
 * 统一的 HTTP 响应输出工具，集中处理编码、长度、CORS 头与连接关闭。
 * @author Fatina 2026/06/29
 */
public final class HttpResponses {

    private HttpResponses() {
    }

    public static void json(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
        write(ctx, status, "application/json; charset=UTF-8", json.getBytes(StandardCharsets.UTF_8));
    }

    public static void text(ChannelHandlerContext ctx, HttpResponseStatus status, String text) {
        write(ctx, status, "text/plain; charset=UTF-8", text.getBytes(StandardCharsets.UTF_8));
    }

    public static void bytes(ChannelHandlerContext ctx, HttpResponseStatus status, String contentType, byte[] body) {
        write(ctx, status, contentType, body);
    }

    public static void error(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        finish(ctx, response);
    }

    /**
     * JSON 字符串值转义（反斜杠、引号），用于拼接 Windows 路径等含特殊字符的值。
     */
    public static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void write(ChannelHandlerContext ctx, HttpResponseStatus status, String contentType, byte[] body) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, ctx.alloc().buffer().writeBytes(body));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        finish(ctx, response);
    }

    private static void finish(ChannelHandlerContext ctx, FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
