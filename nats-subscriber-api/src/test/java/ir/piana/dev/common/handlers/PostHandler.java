package ir.piana.dev.common.handlers;

import ir.piana.dev.common.handler.*;
import ir.piana.dev.common.participants.TransformParticipant;
import ir.piana.dev.jsonparser.json.JsonParser;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@Handler
public class PostHandler<Res> extends BaseRequestHandler<PostHandler.Request, Res> {
    @Autowired
    private HandlerResponseBuilder handlerResponseBuilder;

    @Autowired
    private JsonParser jsonParser;

    @Autowired
    private TransformParticipant transformParticipant;

    @Value("classpath:real-to-ap.yml")
    private Resource realResource;

    @Value("classpath:legal-to-ap.yml")
    private Resource legalResource;

    @Autowired
    private HandlerResponseBuilder responseBuilder;

    @Autowired
    protected PostHandler(
            ContextLoggerProvider contextLoggerProvider) {
        super(contextLoggerProvider);
    }

    @ChainStep(order = 1)
//    @Transactional(propagation = Propagation.REQUIRED)
    public void step1(HandlerRequest <Request> handlerRequest, HandlerInterStateTransporter transporter) {
        contextLogger.info("step 1");
    }

    @ChainStep(order = 2)
    public void step2(HandlerRequest<Request> handlerRequest, HandlerInterStateTransporter transporter) {
        contextLogger.info("step 2");
    }

    @Override
    public HandlerResponse provideResponse(
            HandlerRequest<Request> handlerRequest,
            HandlerInterStateTransporter transporter) {
        contextLogger.info("provide response");
        return responseBuilder.fromDto(new Response(
                1, handlerRequest.getDto().message)).build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        private String message;

        public Request(String message) {
            this.message = message;
        }

    }

    @Getter
    @Setter
    public static class Response {
        private int id;
        private String message;

        public Response(int id, String message) {
            this.id = id;
            this.message = message;
        }
    }
}
