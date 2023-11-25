package ir.piana.dev.common.vertx.http.websocket;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

public class WebSocketWriter {
    public static void write (
            ServerWebSocket serverWebSocket,
            WebSocketMessageType type,
            String id,
            String payload) {
        serverWebSocket.writeTextMessage(
                JsonObject.mapFrom(new WebSocketMessage(type, id,
                        payload)).toString());
    }

    public static void write (
            ServerWebSocket serverWebSocket,
            WebSocketMessageType type,
            String id,
            Object payload) {
        serverWebSocket.writeTextMessage(
                JsonObject.mapFrom(new WebSocketMessage(type, id,
                        JsonObject.mapFrom(payload))).toString());
    }
}
