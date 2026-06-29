package com.fatina.transfer.server.router;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

/**
 * HTTP 路由处理单元。每个业务接口实现一个 controller，由 {@link HttpRouter} 按方法+路径分发。
 * @author Fatina 2026/06/29
 */
public interface HttpController {

    /**
     * 是否处理该请求（path 为去除 query 后的路径）。
     */
    boolean supports(HttpMethod method, String path);

    /**
     * 处理请求并写出响应。
     */
    void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) throws Exception;
}
