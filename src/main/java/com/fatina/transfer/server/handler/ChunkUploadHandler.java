package com.fatina.transfer.server.handler;

import com.fatina.transfer.server.NettyUploadServer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * 分块写盘处理器 (强力落盘补强版)
 *
 * @author Fatina 2026/06/29
 */
public class ChunkUploadHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if ("/upload/chunk".equals(request.uri()) && request.method() == HttpMethod.POST) {
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(factory, request);

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
                        }
                        if ("chunkIndex".equals(attribute.getName())) {
                            chunkIndex = Integer.parseInt(attribute.getValue());
                        }
                        if ("chunkSize".equals(attribute.getName())) {
                            chunkSize = Long.parseLong(attribute.getValue());
                        }
                    } else if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                        fileUpload = (FileUpload) data;
                    }
                }

                if (fileName != null && chunkIndex != -1 && fileUpload != null && fileUpload.isCompleted()) {
                    File parentDir = new File(NettyUploadServer.UPLOAD_DIR);
                    File targetFile = new File(parentDir, fileName);

                    long offset = chunkIndex * chunkSize;

                    try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
                         FileChannel channel = raf.getChannel()) {

                        // 补强1：动态扩展文件长度，防止并发乱序写时破坏文件描述符的边界
                        if (raf.length() < offset + fileUpload.length()) {
                            raf.setLength(offset + fileUpload.length());
                        }

                        channel.position(offset);
                        channel.write(fileUpload.getByteBuf().nioBuffer());

                        // 补强2：核心关键！显式强制将 Page Cache 中的内容同步刷入物理磁盘
                        channel.force(true);

                        System.out.printf("📥 分块物理落盘成功 -> %s [块序号: %d], 绝对路径: %s\n",
                                fileName, chunkIndex, targetFile.getAbsolutePath());
                    }

                    // 构造长连接响应
                    FullHttpResponse response = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                            ctx.alloc().buffer().writeBytes("SUCCESS".getBytes())
                    );
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

                    ctx.writeAndFlush(response);
                }
            } finally {
                decoder.destroy();
            }
        } else {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("❌ 核心通道捕获异常: " + cause.getMessage());
        ctx.close();
    }
}
