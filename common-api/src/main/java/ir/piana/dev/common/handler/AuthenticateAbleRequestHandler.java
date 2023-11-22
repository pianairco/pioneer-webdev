package ir.piana.dev.common.handler;

import ir.piana.dev.common.auth.AuthenticateAbleResponse;
import ir.piana.dev.common.auth.UserAuthentication;
import ir.piana.dev.jsonparser.json.JsonTarget;

public abstract class AuthenticateAbleRequestHandler<Req, Res> extends AuthorizableRequestHandler<Req, Res> {
    protected AuthenticateAbleRequestHandler(
            ContextLoggerProvider contextLoggerProvider) {
        super(contextLoggerProvider);
    }

    @Override
    public final CommonResponse<Res> provideResponse(
            HandlerRequest<Req> handlerRequest,
            HandlerInterStateTransporter transporter) {
        return transporter.getValue("handlerResponseCompleter");
    }

    public abstract AuthenticateAbleResponse doAuthenticate(
            HandlerRequest<Req> handlerRequest, HandlerInterStateTransporter transporter);
}
