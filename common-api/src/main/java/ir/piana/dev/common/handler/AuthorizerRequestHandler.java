package ir.piana.dev.common.handler;

import ir.piana.dev.common.auth.RequiredRoles;

public interface AuthorizerRequestHandler<Req> {
    void authorize(
            HandlerRequest<Req> handlerRequest,
            HandlerInterStateTransporter transporter,
            RequiredRoles requiredRoles);
}
