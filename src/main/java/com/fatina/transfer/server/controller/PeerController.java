package com.fatina.transfer.server.controller;

import com.fatina.transfer.peer.PeerInfo;
import com.fatina.transfer.server.NettyUploadServer;
import com.fatina.transfer.server.router.HttpController;
import com.fatina.transfer.server.router.HttpResponses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.List;

/**
 * 局域网设备列表：GET /api/peers
 * @author Fatina 2026/06/29
 */
public class PeerController implements HttpController {

    @Override
    public boolean supports(HttpMethod method, String path) {
        return HttpMethod.GET.equals(method) && "/api/peers".equals(path);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        HttpResponses.json(ctx, HttpResponseStatus.OK, buildPeersJson());
    }

    private String buildPeersJson() {
        NettyUploadServer server = NettyUploadServer.current();
        List<PeerInfo> peers = server == null ? List.of() : server.discoveredPeers();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < peers.size(); i++) {
            PeerInfo peer = peers.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"nodeId\":\"").append(HttpResponses.escapeJson(peer.nodeId())).append("\",")
                    .append("\"nodeName\":\"").append(HttpResponses.escapeJson(peer.nodeName())).append("\",")
                    .append("\"host\":\"").append(HttpResponses.escapeJson(peer.host())).append("\",")
                    .append("\"port\":").append(peer.port()).append(',')
                    .append("\"mode\":\"").append(peer.runMode().name()).append("\",")
                    .append("\"baseUrl\":\"").append(HttpResponses.escapeJson(peer.baseUrl())).append("\",")
                    .append("\"lastSeen\":").append(peer.lastSeenEpochMillis())
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }
}
