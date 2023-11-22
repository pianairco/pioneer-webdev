package ir.piana.dev.common.handler;

import ir.piana.dev.common.util.MapStrings;
import ir.piana.dev.jsonparser.json.JsonTarget;

public interface HandlerRequest<Req> {
    JsonTarget getJsonTarget();
    String getAuthPhrase();
    String getSerializedRequest();
    Req getDto();
    MapStrings getAdditionalParam();
}
