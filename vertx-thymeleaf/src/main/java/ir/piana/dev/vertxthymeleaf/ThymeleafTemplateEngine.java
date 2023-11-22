package ir.piana.dev.vertxthymeleaf;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Vertx;
import io.vertx.ext.web.common.template.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;

@VertxGen
public interface ThymeleafTemplateEngine extends TemplateEngine {
    TemplateMode DEFAULT_TEMPLATE_MODE = TemplateMode.HTML;

    /**
     * Create a template engine using defaults
     *
     * @return the engine
     */
    static ThymeleafTemplateEngine create(Vertx vertx, boolean cacheable) {
        return new ThymeleafTemplateEngineImpl(vertx, cacheable);
    }
}
