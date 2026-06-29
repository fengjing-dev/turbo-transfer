package com.fatina.transfer.server.controller;

import com.fatina.transfer.server.NettyUploadServer;
import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.stream.ChunkedFile;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 文件下载（分块流式）：GET /api/download?name=...
 * @author Fatina 2026/06/29
 */
public class DownloadController implements HttpController {

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method) && "/api/download".equals(path);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) throws Exception {
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
    }
}
