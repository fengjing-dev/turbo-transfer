package com.fatina.transfer.server.controller;

import com.fatina.transfer.server.ClientTracker;
import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.List;

/**
 * 已连接客户端列表：GET /api/clients
 */
public class ClientController implements HttpController {

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method) && "/api/clients".equals(path);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        List<ClientTracker.ClientInfo> clients = ClientTracker.instance().snapshot();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < clients.size(); i++) {
            ClientTracker.ClientInfo c = clients.get(i);
            if (i > 0) json.append(',');
            json.append('{')
                    .append("\"ip\":\"").append(HttpResponses.escapeJson(c.ip())).append("\",")
                    .append("\"userAgent\":\"").append(HttpResponses.escapeJson(c.userAgent() != null ? c.userAgent() : "")).append("\",")
                    .append("\"deviceModel\":\"").append(HttpResponses.escapeJson(c.deviceModel() != null ? c.deviceModel() : "")).append("\",")
                    .append("\"platform\":\"").append(HttpResponses.escapeJson(c.platform() != null ? c.platform() : "")).append("\",")
                    .append("\"platformVersion\":\"").append(HttpResponses.escapeJson(c.platformVersion() != null ? c.platformVersion() : "")).append("\",")
                    .append("\"lastSeen\":").append(c.lastSeenMillis()).append(',')
                    .append("\"requestCount\":").append(c.requestCount())
                    .append('}');
        }
        json.append(']');
        HttpResponses.json(ctx, HttpResponseStatus.OK, json.toString());
    }
}
