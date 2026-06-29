package com.fatina.transfer.server.controller;

import com.fatina.transfer.server.NettyUploadServer;
import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * 分块上传落盘：POST /upload/chunk
 * 按 chunkIndex * chunkSize 定位偏移写入，强制刷盘以保证并发乱序写的正确性。
 * @author Fatina 2026/06/29
 */
public class UploadController implements HttpController {

    private static final HttpDataFactory FACTORY = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.POST.equals(method) && "/upload/chunk".equals(path);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) throws Exception {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(FACTORY, request);

        String fileName = null;
        int chunkIndex = -1;
        long chunkSize = 0;
        FileUpload fileUpload = null;

        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    Attribute attribute = (Attribute) data;
                    if ("fileName".equals(attribute.getName())) {
                        fileName = attribute.getValue();
                    } else if ("chunkIndex".equals(attribute.getName())) {
                        chunkIndex = Integer.parseInt(attribute.getValue());
                    } else if ("chunkSize".equals(attribute.getName())) {
                        chunkSize = Long.parseLong(attribute.getValue());
                    }
                } else if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    fileUpload = (FileUpload) data;
                }
            }

            if (fileName == null || chunkIndex < 0 || fileUpload == null || !fileUpload.isCompleted()) {
                HttpResponses.error(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            File parentDir = new File(NettyUploadServer.transferDir());
            File targetFile = new File(parentDir, fileName);
            long offset = (long) chunkIndex * chunkSize;

            try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
                 FileChannel channel = raf.getChannel()) {

                if (raf.length() < offset + fileUpload.length()) {
                    raf.setLength(offset + fileUpload.length());
                }

                channel.position(offset);
                channel.write(fileUpload.getByteBuf().nioBuffer());
                channel.force(true);

                System.out.printf("📥 分块物理落盘成功 -> %s [块序号: %d], 绝对路径: %s%n",
                        fileName, chunkIndex, targetFile.getAbsolutePath());
            }

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    ctx.alloc().buffer().writeBytes("SUCCESS".getBytes()));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            ctx.writeAndFlush(response);
        } finally {
            decoder.destroy();
        }
    }
}
