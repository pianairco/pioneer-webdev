package ir.piana.dev.common.handler;

import io.vertx.core.buffer.Buffer;
import ir.piana.dev.common.util.MapStrings;
import ir.piana.dev.jsonparser.json.JsonTarget;

public interface HandlerResponse<Res> extends CommonResponse {
    JsonTarget getJsonTarget();
    Buffer getBuffer();
    String getSerializedResponse();
    Res getDto();
    MapStrings getAdditionalParam();
}
