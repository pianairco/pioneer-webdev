package ir.piana.dev.common.handler;

public interface AuthenticationRequestHandler<Req> {
    void authenticate(
            HandlerRequest<Req> handlerRequest, HandlerInterStateTransporter transporter);
}
