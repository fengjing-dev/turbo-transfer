package com.fatina.transfer.server.controller;

import com.fatina.transfer.preview.FilePreviewResult;
import com.fatina.transfer.preview.FilePreviewService;
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
 * 文件预览（PDF/PSD 等）：GET /api/file/preview?name=...
 * @author Fatina 2026/06/29
 */
public class FilePreviewController implements HttpController {

    private static final FilePreviewService FILE_PREVIEW_SERVICE = new FilePreviewService();

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method) && "/api/file/preview".equals(path);
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
            FilePreviewResult previewResult = FILE_PREVIEW_SERVICE.extract(file);
            if (previewResult != null && previewResult.bytes() != null && previewResult.bytes().length > 0) {
                HttpResponses.bytes(ctx, HttpResponseStatus.OK, previewResult.contentType(), previewResult.bytes());
                return;
            }
        } catch (Throwable e) {
            System.err.println("文件预览解析异常，已安全隔离: " + e.getMessage());
        }
        HttpResponses.error(ctx, HttpResponseStatus.NOT_FOUND);
    }
}
