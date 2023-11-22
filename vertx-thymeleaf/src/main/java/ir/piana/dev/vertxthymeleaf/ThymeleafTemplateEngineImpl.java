package ir.piana.dev.vertxthymeleaf;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.common.WebEnvironment;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ThymeleafTemplateEngineImpl implements ThymeleafTemplateEngine {
    private final TemplateEngine templateEngine = new TemplateEngine();
    private final ResourceTemplateResolver templateResolver;

    public ThymeleafTemplateEngineImpl(Vertx vertx, boolean cacheable) {
        ResourceTemplateResolver templateResolver = new ResourceTemplateResolver(vertx);
        /*templateResolver.setCacheable(!WebEnvironment.development());*/
        templateResolver.setCacheable(cacheable);
        templateResolver.setTemplateMode(ThymeleafTemplateEngine.DEFAULT_TEMPLATE_MODE);

        this.templateResolver = templateResolver;
        this.templateEngine.setTemplateResolver(templateResolver);
        // There's no servlet context in Vert.x, so we override default link builder
        // See https://github.com/vert-x3/vertx-web/issues/161
        this.templateEngine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(
                    final IExpressionContext context, final String base, final Map<String, Object> parameters) {
                return "/";
            }
        });
    }

    @Override
    public <T> T unwrap() {
        return (T) templateEngine;
    }

    @Override
    public void clearCache() {
        templateEngine.clearTemplateCache();
    }

    @Override
    public void render(Map<String, Object> context, String templateFileName, Handler<AsyncResult<Buffer>> handler) {
        Buffer buffer = Buffer.buffer();

        try {
            synchronized (this) {
                templateEngine.process(templateFileName, new WebIContext(context, (String) context.get("lang")), new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) {
                        buffer.appendString(new String(cbuf, off, len));
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                });
            }

            handler.handle(Future.succeededFuture(buffer));
        } catch (Exception ex) {
            handler.handle(Future.failedFuture(ex));
        }
    }

    @Override
    public Future<Buffer> render(Map<String, Object> context, String templateFile) {
        Buffer buffer = Buffer.buffer();

        try {
            synchronized (this) {
                templateEngine.process(templateFile, new WebIContext(context, (String) context.get("lang")), new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) {
                        buffer.appendString(new String(cbuf, off, len));
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                });
            }

            return Future.succeededFuture(buffer);
        } catch (Exception ex) {
            return Future.failedFuture(ex);
        }
    }

    private static class WebIContext implements IContext {
        private final Map<String, Object> data;
        private final Locale locale;

        private WebIContext(Map<String, Object> data, String lang) {
            this.data = data;
            if (lang == null) {
                this.locale = Locale.getDefault();
            } else {
                this.locale = Locale.forLanguageTag(lang);
            }
        }

        @Override
        public java.util.Locale getLocale() {
            return locale;
        }

        @Override
        public boolean containsVariable(String name) {
            return data.containsKey(name);
        }

        @Override
        public Set<String> getVariableNames() {
            return data.keySet();
        }

        @Override
        public Object getVariable(String name) {
            return data.get(name);
        }
    }

    private static class ResourceTemplateResolver extends StringTemplateResolver {
        private final Vertx vertx;

        ResourceTemplateResolver(Vertx vertx) {
            super();
            this.vertx = vertx;
            setName("vertx/Thymeleaf3");
        }

        @Override
        protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, Map<String, Object> templateResolutionAttributes) {
            return new StringTemplateResource(
                    vertx.fileSystem()
                            .readFileBlocking(template)
                            .toString(Charset.defaultCharset()));
        }
    }
}
