package ir.piana.dev.common.vertx.http.websocket;

import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.RoutingContext;
import ir.piana.dev.common.handler.*;

public abstract class WebSocketEstablisherRequestHandler<Req, Res> extends AuthorizableRequestHandler<Req, Res> {
//    private RequiredRoles requiredRoles = new RequiredRoles();

    protected WebSocketEstablisherRequestHandler(
            ContextLoggerProvider contextLoggerProvider) {
        super(contextLoggerProvider);
    }

    final public CommonResponse<Res> provideResponse(
            HandlerRequest<Req> handlerRequest, HandlerInterStateTransporter transporter) throws HandlerRuntimeException {
        return responseBuilder.withoutBody().build();
    }

    final public void establish(
            RoutingContext routingContext, HandlerRequest handlerRequest, String authPhrase) throws HandlerRuntimeException {
        routingContext.request()
                .toWebSocket()
                .onFailure(System.out::println)
                .onSuccess(websocket -> {
                    // Inside the future's handler (or compose) use rc.request().resume()
                    routingContext.request().resume();
//                    websocket.handler(System.out::println);
                    established(websocket, handlerRequest, authPhrase);
                });
    }

    /*final public DeferredResult<ServerWebSocket> establish(RoutingContext routingContext) throws HandlerRuntimeException {
        DeferredResult<ServerWebSocket> deferredResult = new DeferredResult<>();

        // rc.request().toWebSocket() to get the websocket.
        Future<ServerWebSocket> webSocketFuture =
        routingContext.request()
                .toWebSocket()
                .onFailure(deferredResult::setErrorResult)
                .onSuccess(websocket -> {
                    // Inside the future's handler (or compose) use rc.request().resume()
                    routingContext.request().resume();
//                    websocket.handler(System.out::println);
                    deferredResult.setResult(websocket);
                });

        webSocketFuture.onFailure(routingContext::fail);
        webSocketFuture.onSuccess(websocket -> {
            // Inside the future's handler (or compose) use rc.request().resume()
            routingContext.request().resume();
            deferredResult.setResult(websocket);
        });
        return deferredResult;
    }*/

    public abstract void established(ServerWebSocket webSocket, HandlerRequest handlerRequest, String authPhrase);
}
