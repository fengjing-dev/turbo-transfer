package com.fatina.transfer.server.controller;

import com.fatina.transfer.server.NettyUploadServer;
import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.File;
import java.util.Set;

/**
 * 文件库列表：GET /api/files
 * @author Fatina 2026/06/29
 */
public class FileController implements HttpController {

    private static final Set<String> HIDDEN_FILES = Set.of(
            "desktop.ini", "thumbs.db", ".ds_store"
    );

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method) && "/api/files".equals(path);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        HttpResponses.json(ctx, HttpResponseStatus.OK, buildFilesJson());
    }

    private String buildFilesJson() {
        File dir = new File(NettyUploadServer.transferDir());
        File[] files = dir.listFiles();
        StringBuilder json = new StringBuilder("[");
        if (files != null) {
            int addedCount = 0;
            for (File file : files) {
                if (file.isFile() && !HIDDEN_FILES.contains(file.getName().toLowerCase())) {
                    if (addedCount > 0) {
                        json.append(',');
                    }
                    json.append(String.format("{\"name\":\"%s\",\"size\":%d,\"lastModified\":%d}",
                            HttpResponses.escapeJson(file.getName()), file.length(), file.lastModified()));
                    addedCount++;
                }
            }
        }
        json.append(']');
        return json.toString();
    }
}
