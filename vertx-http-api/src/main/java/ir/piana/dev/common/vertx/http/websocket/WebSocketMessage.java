package ir.piana.dev.common.vertx.http.websocket;

public record WebSocketMessage(WebSocketMessageType type, String id, Object payload) {
}
