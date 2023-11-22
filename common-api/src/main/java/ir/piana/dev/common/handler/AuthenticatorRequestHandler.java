package ir.piana.dev.common.handler;

import ir.piana.dev.common.auth.AuthenticateAbleResponse;

public interface AuthenticatorRequestHandler {
    CommonResponse<?> provideResponse(
            HandlerRequest<?> handlerRequest,
            HandlerInterStateTransporter transporter,
            AuthenticateAbleResponse authenticateAbleResponse);
}
