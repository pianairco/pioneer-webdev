package ir.piana.dev.common.handler;

import ir.piana.dev.common.auth.AuthenticateAbleResponse;
import ir.piana.dev.common.auth.AuthenticateAbleStatus;
import ir.piana.dev.common.auth.RequiredRoles;
import ir.piana.dev.common.service.UniqueIdService;
import ir.piana.dev.common.util.*;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Configuration
public class HandlerManagerAutoConfiguration {

//    private final ContextLogger logger = ContextLogger.getLogger(this.getClass());

    @Bean("reactiveCommonThreadPool")
    public ExecutorService reactiveCommonThreadPool(@Autowired ReactiveCore reactiveCore) {
        return Executors.newFixedThreadPool(reactiveCore.threadPoolSize);
    }

    @Bean
    Method provideResponseMethod() {
        Method provideResponseMethod;
        try {
            return RequestHandler.class.getDeclaredMethod("provideResponse", HandlerRequest.class, HandlerInterStateTransporter.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    Map<String, RequiredRoles> requiredRolesMap(HandlerRoleConfig handlerRoleConfig) {
        final Map<String, RequiredRoles> requiredRolesMap = new LinkedHashMap<>();
        if (handlerRoleConfig.items == null)
            return requiredRolesMap;
        handlerRoleConfig.items.forEach(handlerRoleItem -> {
            requiredRolesMap.put(handlerRoleItem.handlerClassName,
                    new RequiredRoles(
                            handlerRoleItem.allRequired,
                            Stream.of(Optional.ofNullable(handlerRoleItem.roles).orElse("")
                                            .split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .toList()));
        });

        return requiredRolesMap;
    }

    public static Class<?> getHandlerClass(Object bean) throws ClassNotFoundException {
        if (bean.getClass().getName().contains("$$"))
            return Class.forName(bean.getClass().getGenericSuperclass().getTypeName());
        else
            return bean.getClass();
    }

    @Bean
    public Map<Class, Class> proxiedToOriginalClassMap(
            ApplicationContext applicationContext,
            ContextLoggerProvider contextLoggerProvider) {
        Map<Class, Class> map = new LinkedHashMap<>();
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(Handler.class);
        ContextLogger logger = contextLoggerProvider.registerLogger(HandlerManagerAutoConfiguration.class);

        beansWithAnnotation.entrySet().stream().forEach(entry -> {
            try {
                map.put(entry.getValue().getClass(), getHandlerClass(entry.getValue()));
            } catch (ClassNotFoundException e) {
                logger.error(null, "handler {} must be implement RequestHandler", entry.getKey());
            }
        });

        return map;
    }

    @Bean
    HandlerManager getHandlerManager(
            ApplicationContext applicationContext,
            Map<String, RequiredRoles> requiredRolesMap,
            HandlerRuntimeExceptionThrower handlerRuntimeExceptionThrower,
            Method provideResponseMethod,
            @Qualifier("reactiveCommonThreadPool") ExecutorService executorService,
            AuthenticationManager authenticationManager,
            HandlerContextThreadLocalProvider handlerContextThreadLocalProvider,
            HandlerSession handlerSession,
            ContextLoggerProvider contextLoggerProvider) {
        ContextLogger logger = contextLoggerProvider.registerLogger(HandlerManagerAutoConfiguration.class);
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(Handler.class);

        Map<Class<?>, HandlerContainer> handlerContainerMap = new LinkedHashMap<>();
        final List<Map.Entry> classNotFounds = new ArrayList<>();
        final Map<String, Class> classFounds = new LinkedHashMap<>();

        beansWithAnnotation.entrySet().stream().filter(entry -> {
            if (!(entry.getValue() instanceof RequestHandler)) {
                logger.error(null, "handler {} must be implement RequestHandler", entry.getKey());
                return false;
            }
            try {
                classFounds.put(entry.getKey(),
                            getHandlerClass(entry.getValue()));
                return true;
            } catch (ClassNotFoundException e) {
                classNotFounds.add(entry);
            }
            return false;
        }).forEach(entry -> {
            Map<Integer, Method> chainStepMethodMap = new LinkedHashMap<>();
            Map<Integer, Method> rollbackMethodMap = new LinkedHashMap<>();
            Class originalClass = classFounds.get(entry.getKey());

            /*if (entry.getValue() instanceof AuthorizableRequestHandler<?, ?>) {
                if (requiredRolesMap.containsKey(classFounds.get(entry.getKey()).getName())) {
                    ((AuthorizableRequestHandler) entry.getValue())
                            .setRequiredRoles(requiredRolesMap.get(
                                    classFounds.get(entry.getKey()).getName()
                            ));
                }
            }*/

            Method[] originalDeclaredMethods = originalClass.getDeclaredMethods();
            Method[] beanDeclaredMethods = originalClass.getDeclaredMethods();
            for (Method originalMethod : originalDeclaredMethods) {
                if (originalMethod.equals(provideResponseMethod))
                    continue;
                ChainStep chainStep = originalMethod.getAnnotation(ChainStep.class);
                AssignedRollback assignedRollback = originalMethod.getAnnotation(AssignedRollback.class);
                if (chainStep != null && originalMethod.getParameterCount() == 2 &&
                        originalMethod.getParameterTypes()[0].isAssignableFrom(HandlerRequest.class) &&
                        originalMethod.getParameterTypes()[1].isAssignableFrom(HandlerInterStateTransporter.class) &&
                        (originalMethod.getReturnType().isAssignableFrom(void.class) ||
                                originalMethod.getReturnType().isAssignableFrom(HandlerResponse.class) ||
                                originalMethod.getReturnType().isAssignableFrom(CompletableFuture.class))) {
                    if (chainStepMethodMap.containsKey(chainStep.order()))
                        throw new RuntimeException("ChainStep by same order : " + chainStep.order() + " on " + entry.getKey());
                    for (Method beanMethod : beanDeclaredMethods) {
                        if (beanMethod.getName().equals(originalMethod.getName()) &&
                                beanMethod.getReturnType().equals(originalMethod.getReturnType()) &&
                                beanMethod.getParameterCount() == originalMethod.getParameterCount() &&
                                beanMethod.getParameterTypes()[0].equals(originalMethod.getParameterTypes()[0]))
                            chainStepMethodMap.put(chainStep.order(), beanMethod);
                    }
                } else if (assignedRollback != null && originalMethod.getParameterCount() == 2 &&
                        originalMethod.getParameterTypes()[0].isAssignableFrom(HandlerRequest.class) &&
                        originalMethod.getParameterTypes()[1].isAssignableFrom(HandlerInterStateTransporter.class) &&
                        (originalMethod.getReturnType().isAssignableFrom(void.class))) {
                    if (rollbackMethodMap.containsKey(assignedRollback.matchedOrder()))
                        throw new RuntimeException("AssignedRollback by same order : " + assignedRollback.matchedOrder() + " on " + entry.getKey());
                    for (Method beanMethod : beanDeclaredMethods) {
                        if (beanMethod.getName().equals(originalMethod.getName()) &&
                                beanMethod.getReturnType().equals(originalMethod.getReturnType()) &&
                                beanMethod.getParameterCount() == originalMethod.getParameterCount() &&
                                beanMethod.getParameterTypes()[0].equals(originalMethod.getParameterTypes()[0]))
                            rollbackMethodMap.put(assignedRollback.matchedOrder(), beanMethod);
                    }
                }
            }
            handlerContainerMap.put(originalClass, new HandlerContainer(
                    entry.getKey(),
                    entry.getValue(),
                    new TreeMap<>(chainStepMethodMap),
                    new TreeMap<>(rollbackMethodMap)));
        });

        return new HandlerManagerImpl(handlerContainerMap, executorService,
                handlerRuntimeExceptionThrower,
                authenticationManager,
                handlerContextThreadLocalProvider,
                handlerSession,
                contextLoggerProvider,
                contextLoggerProvider.registerLogger(HandlerManager.class));
    }

    private class HandlerContainer {
        String handlerBeanName;
        Class handlerClass;
        Object handlerBean;
        Map<Integer, Method> chainStepMethodMap = new LinkedHashMap<>();
        Map<Integer, Method> rollbackMethodMap = new LinkedHashMap<>();

        public HandlerContainer(
                String handlerBeanName, Object handlerBean,
                Map<Integer, Method> chainStepMethodMap,
                Map<Integer, Method> rollbackMethodMap) {
            this.handlerBeanName = handlerBeanName;
            this.handlerClass = handlerBean.getClass();
            this.handlerBean = handlerBean;
            this.chainStepMethodMap = chainStepMethodMap;
            this.rollbackMethodMap = rollbackMethodMap;
        }
    }

    @Bean
    public AuthenticationRequestHandler<?> authenticationRequestHandler(
            AuthenticationManager authenticationManager) {
        return (handlerRequest, transporter) -> transporter.setUserAuthentication(
                authenticationManager.getUserAuthentication(transporter.getSessionId()));
    }

    @Bean
    public AuthorizerRequestHandler<?> authorizerRequestHandler(
            HandlerRuntimeExceptionThrower thrower) {
        return (handlerRequest, transporter, requiredRoles) -> {
            if(thrower != null) {
                if (requiredRoles.isAllRequired()) {
                    if (!transporter.getUserAuthentication().getUserAuthorization()
                            .hasAllRole(requiredRoles.getRoles()))
                        thrower.proceed(HandlerErrorType.PERMISSION_DENIED.generateDetailedError(
                                "permission.denied"));
                } else {
                    if (!transporter.getUserAuthentication().getUserAuthorization()
                            .hasAnyRole(requiredRoles.getRoles()))
                        thrower.proceed(HandlerErrorType.PERMISSION_DENIED.generateDetailedError(
                                "permission.denied"));
                }
            }
        };
    }

    @Bean
    public AuthenticatorRequestHandler authenticatorRequestHandler(
            AuthenticationManager authenticationManager,
            HandlerResponseBuilder responseBuilder,
            HandlerRuntimeExceptionThrower thrower
    ) {
        return (handlerRequest, transporter, authenticateAbleResponse) -> {
            if (authenticateAbleResponse != null) {
                if (authenticateAbleResponse.getAuthenticateAbleStatus() == AuthenticateAbleStatus.AUTHENTICATED) {
                    authenticationManager.reassign(
                            transporter.getSessionId(),
                            authenticateAbleResponse.getPrincipal(),
                            authenticateAbleResponse.getUserAuthorization());
                }
                else if (authenticateAbleResponse.getAuthenticateAbleStatus() == AuthenticateAbleStatus.REVOKED) {
                    authenticationManager.revoke(
                            transporter.getSessionId());
                }
                if (authenticateAbleResponse.getView() != null) {
                    return HandlerModelAndViewResponse.builder()
                            .view(authenticateAbleResponse.getView())
                            .model(authenticateAbleResponse.getModel())
                            .authPhrase(transporter.getSessionId())
                            .build();
                } else if (authenticateAbleResponse.getModel() != null) {
                    return responseBuilder.fromJsonTarget(authenticateAbleResponse.getModel())
                            .build();
                } else {
                    return responseBuilder.withoutBody()
                            .build();
                }
            }
            throw thrower.generate(HandlerErrorType.INTERNAL.generateDetailedError(
                    "authenticated.error"));
        };
    }

    @Bean
    public Map<Class<? extends AuthorizableRequestHandler<?, ?>>, RequiredRoles> handlerRequiredRolesMap(
            ApplicationContext applicationContext,
            ContextLoggerProvider contextLoggerProvider,
            Map<String, RequiredRoles> requiredRolesMap) {
        Map<Class<? extends AuthorizableRequestHandler<?, ?>>, RequiredRoles> map = new LinkedHashMap<>();
        ContextLogger logger = contextLoggerProvider.registerLogger(HandlerManagerAutoConfiguration.class);
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(Handler.class);
        beansWithAnnotation.entrySet().stream().filter(entry -> {
            if (!(entry.getValue() instanceof RequestHandler)) {
                logger.error(null, "handler {} must be implement RequestHandler", entry.getKey());
                return false;
            }
            return entry.getValue() instanceof AuthorizableRequestHandler<?,?>;
        }).forEach(entry -> {
            try {
                Class<? extends AuthorizableRequestHandler<?, ?>> theClass =
                        (Class<? extends AuthorizableRequestHandler<?, ?>>) getHandlerClass(
                                entry.getValue());
                map.put(theClass, Optional.ofNullable(requiredRolesMap.get(theClass.getName()))
                        .orElse(new RequiredRoles()));
            } catch (ClassNotFoundException e) {
                logger.error(null, "handler {} must be implement RequestHandler", entry.getKey());
            }
        });

        return map;
    }

    private static class HandlerManagerImpl implements HandlerManager {
        private ContextLoggerProvider contextLoggerProvider;
        private AuthenticationManager authenticationManager;
        private HandlerContextThreadLocalProvider handlerContextThreadLocalProvider;
        private HandlerSession handlerSession;
        private HandlerRuntimeExceptionThrower handlerRuntimeExceptionThrower;
        private final ContextLogger logger;
        ;
        private final ExecutorService executorService;

        private final Map<Class<?>, HandlerContainer> handlerContainerMap;

        private HandlerManagerImpl(Map<Class<?>, HandlerContainer> handlerContainerMap,
                                   ExecutorService executorService,
                                   HandlerRuntimeExceptionThrower handlerRuntimeExceptionThrower,
                                   AuthenticationManager authenticationManager,
                                   HandlerContextThreadLocalProvider handlerContextThreadLocalProvider,
                                   HandlerSession handlerSession,
                                   ContextLoggerProvider contextLoggerProvider,
                                   ContextLogger logger) {
            this.handlerContainerMap = handlerContainerMap;
            this.executorService = executorService;
            this.handlerRuntimeExceptionThrower = handlerRuntimeExceptionThrower;
            this.authenticationManager = authenticationManager;
            this.handlerContextThreadLocalProvider = handlerContextThreadLocalProvider;
            this.handlerSession = handlerSession;
            this.contextLoggerProvider = contextLoggerProvider;
            this.logger = logger;
            rollbackExecutors = Executors.newFixedThreadPool(10);
        }

        private final SelfExpiringMap<Long, HandlerContext<?>> existingHandlerContextMap = new SelfExpiringHashMap<>();

        ExecutorService rollbackExecutors;

        @Autowired
        private UniqueIdService uniqueIdService;

        @Autowired
        private Method provideResponseMethod;

        @Autowired
        private AuthenticationRequestHandler<?> authenticationRequestHandler;

        @Autowired
        private AuthorizerRequestHandler<?> authorizerRequestHandler;

        @Autowired
        private Map<Class<? extends AuthorizableRequestHandler<?, ?>>, RequiredRoles> handlerRequiredRolesMap;

        @Autowired
        private Map<Class, Class> proxiedToOriginalClassMap;

        @Autowired
        private AuthenticatorRequestHandler authenticatorRequestHandler;

        private final static Pattern UUID_PATTERN = Pattern.compile(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

        @Override
        public DeferredResult<HandlerResponse> execute(
                Class<?> beanClass, HandlerRequest<?> handlerRequest) {

            DeferredResult deferredResult = new DeferredResult();
            final FinalContainer<CompletableFuture> futures = new FinalContainer<>();
            final FinalContainer<SelfExpiringHashMap<String, Object>> handlerSessionContainer = new FinalContainer<>();
            final HandlerContainer handlerContainer = handlerContainerMap.get(beanClass);
            final FinalContainer<Long> uniqueIdContainer = new FinalContainer<>();
            final FinalContainer<Boolean> responseGenerated = new FinalContainer<>(false);

            futures.set(CompletableFuture.supplyAsync(() -> {
                uniqueIdContainer.set(uniqueIdService.getId());
                if (handlerContainerMap.containsKey(uniqueIdContainer.get()))
                    throw new RuntimeException("duplicate id!");

                String sessionId = Optional.ofNullable(handlerRequest.getAuthPhrase()).orElse("");
                if (!UUID_PATTERN.matcher(sessionId).matches()) {
                    sessionId = UUID.randomUUID().toString();
                }
                handlerSessionContainer.set(authenticationManager.getSession(sessionId));
                HandlerContext<?> handlerContext = BaseHandlerContext.create(
                        handlerContainer.handlerBeanName, uniqueIdContainer.get(), handlerRequest,
                        sessionId);
                existingHandlerContextMap.put(handlerContext.uniqueId(), handlerContext, 30_000l);
                return handlerContext;
            }));

            final List<Method> rollbackMethods = new ArrayList<>();

            if (handlerContainer != null) {
                if (handlerContainer.handlerBean instanceof AuthenticableRequestHandler<?, ?>) {
                    futures.set(futures.get().thenApplyAsync(ctx -> {
                        try {
                            handlerSession.set(handlerSessionContainer.get());
                            handlerContextThreadLocalProvider.set((HandlerContext) ctx);
                            authenticationRequestHandler.authenticate(
                                    ((HandlerContext) ctx).request(),
                                    ((HandlerContext) ctx).getInterstateTransporter());
                            /*((AuthenticableRequestHandler) handlerContainer.handlerBean).authenticate(
                                    ((HandlerContext) ctx).request(),
                                    ((HandlerContext) ctx).getInterstateTransporter()
                            );*/
                            return ctx;
                        } finally {
                            handlerContextThreadLocalProvider.remove();
                            handlerSession.remove();
                        }
                    }));
                }
                if (handlerContainer.handlerBean instanceof AuthorizableRequestHandler<?, ?>) {
                    futures.set(futures.get().thenApplyAsync(ctx -> {
                        try {
                            handlerSession.set(handlerSessionContainer.get());
                            handlerContextThreadLocalProvider.set((HandlerContext) ctx);
                            authorizerRequestHandler.authorize(
                                    ((HandlerContext) ctx).request(),
                                    ((HandlerContext) ctx).getInterstateTransporter(),
                                    handlerRequiredRolesMap.get(proxiedToOriginalClassMap.get(
                                            handlerContainer.handlerBean.getClass()))
                            );
                            /*((AuthorizableRequestHandler) handlerContainer.handlerBean).authorize(
                                    ((HandlerContext) ctx).request(),
                                    ((HandlerContext) ctx).getInterstateTransporter()
                            );*/
                            return ctx;
                        } finally {
                            handlerContextThreadLocalProvider.remove();
                            handlerSession.remove();
                        }
                    }));
                }

                handlerContainer.chainStepMethodMap.entrySet().forEach(entry -> {
                    if (entry.getValue().getReturnType().isAssignableFrom(CompletableFuture.class)) {
                        futures.set(futures.get().thenComposeAsync(ctx -> {
                                    try {
                                        handlerSession.set(handlerSessionContainer.get());
                                        handlerContextThreadLocalProvider.set((HandlerContext) ctx);
                                        return entry.getValue().invoke(handlerContainer.handlerBean,
                                                ((HandlerContext) ctx).request(),
                                                ((HandlerContext) ctx).getInterstateTransporter());
                                    } catch (IllegalAccessException e) {
                                        logger.error(e);
                                        throw new HandlerRuntimeException(
                                                (HandlerContext<?>) ctx,
                                                HandlerErrorType.INTERNAL.generateDetailedError(
                                                        "invoke method call exception"), e);
                                    } catch (InvocationTargetException e) {
                                        if (e.getTargetException() instanceof HandlerRuntimeException)
                                            throw (HandlerRuntimeException) e.getTargetException();
                                        throw new RuntimeException(e.getTargetException());
//                                handlerExceptionThrower.proceed(HandlerErrorType.INTERNAL.generateDetailedError("error.unknown"));
                                    } finally {
                                        handlerContextThreadLocalProvider.remove();
                                        handlerSession.remove();
                                    }
                                }, executorService)
                                .thenApplyAsync(
                                        httpResponse -> {
                                            HandlerContext context = existingHandlerContextMap.get(uniqueIdContainer.get());
                                            context.put(entry.getValue().getName(), httpResponse);
                                            return context;
                                        }, executorService));
                    } else {
                        futures.set(futures.get().thenApplyAsync(ctx -> {
                            try {
                                handlerSession.set(handlerSessionContainer.get());
                                handlerContextThreadLocalProvider.set((HandlerContext) ctx);
                                Object returnValue = entry.getValue().invoke(handlerContainer.handlerBean,
                                        ((HandlerContext) ctx).request(),
                                        ((HandlerContext) ctx).getInterstateTransporter());
                                /*if (returnValue instanceof HandlerResponse<?>) {
                                    ((BaseHandlerContext<Object>)existingHandlerContextMap.get(uniqueIdContainer.get()))
                                            .addHandlerResponse((HandlerResponse) returnValue);
                                    responseGenerated.set(true);
                                }*/
                                return existingHandlerContextMap.get(uniqueIdContainer.get());
                            } catch (IllegalAccessException e) {
                                throw new HandlerRuntimeException(
                                        (HandlerContext<?>) ctx,
                                        HandlerErrorType.INTERNAL.generateDetailedError("invoke method call exception"), e);
                            } catch (InvocationTargetException e) {
                                if (e.getTargetException() instanceof HandlerRuntimeException)
                                    throw (HandlerRuntimeException) e.getTargetException();
                                throw new RuntimeException(e.getTargetException());
//                                handlerExceptionThrower.proceed(HandlerErrorType.INTERNAL.generateDetailedError("error.unknown"));
                            } finally {
                                handlerContextThreadLocalProvider.remove();
                                handlerSession.remove();
                            }
                        }, executorService));
                    }
                    if (handlerContainer.rollbackMethodMap.containsKey(entry.getKey()))
                        rollbackMethods.add(handlerContainer.rollbackMethodMap.get(entry.getKey()));
                });
            }

            if(handlerContainer.handlerBean instanceof AuthenticateAbleRequestHandler<?,?>) {
//                authenticatorRequestHandler

                futures.set(futures.get().thenApplyAsync(ctx -> {
                    try {
                        handlerSession.set(handlerSessionContainer.get());
                        handlerContextThreadLocalProvider.set((HandlerContext) ctx);

                        return ((AuthenticateAbleRequestHandler)handlerContainer.handlerBean).doAuthenticate(
                                ((HandlerContext) ctx).request(),
                                ((HandlerContext) ctx).getInterstateTransporter());
                    } finally {
                        handlerContextThreadLocalProvider.remove();
                        handlerSession.remove();
                    }
                }, executorService).thenApplyAsync(authenticateAbleResponse -> {
                            try {
                                handlerSession.set(handlerSessionContainer.get());
                                handlerContextThreadLocalProvider.set(existingHandlerContextMap.get(uniqueIdContainer.get()));

                                CommonResponse commonResponse = authenticatorRequestHandler.provideResponse(
                                        existingHandlerContextMap.get(uniqueIdContainer.get()).request(),
                                        existingHandlerContextMap.get(uniqueIdContainer.get()).getInterstateTransporter(),
                                        (AuthenticateAbleResponse) authenticateAbleResponse
                                );

                                existingHandlerContextMap.get(uniqueIdContainer.get()).getInterstateTransporter()
                                        .put("handlerResponseCompleter", commonResponse);
                                return existingHandlerContextMap.get(uniqueIdContainer.get());
                            } finally {
                                handlerContextThreadLocalProvider.remove();
                                handlerSession.remove();
                            }
                }));
            }



            CompletableFuture c = futures.get().thenAcceptAsync(ctx -> {
                try {
                    handlerSession.set(handlerSessionContainer.get());
                    handlerContextThreadLocalProvider.set((HandlerContext) ctx);
                    Object handlerResponse = provideResponseMethod.invoke(
                            handlerContainer.handlerBean,
                            ((HandlerContext) ctx).request(),
                            ((HandlerContext) ctx).getInterstateTransporter());
                    if (((HandlerContext) ctx).responded()) {
                        logger.error("Already sent response!");
                    } else {
                        deferredResult.setResult(((HandlerResponseBuilder.HandlerResponseImpl)handlerResponse)
                                .setAuthPhrase(((HandlerContext) ctx).getInterstateTransporter().getSessionId()));
                    }
                } catch (IllegalAccessException e) {
                    throw new HandlerRuntimeException(
                            (HandlerContext<?>) ctx,
                            HandlerErrorType.INTERNAL.generateDetailedError(
                                    "invoke method call exception"), e);
                } catch (InvocationTargetException e) {
                    if (e.getTargetException() instanceof HandlerRuntimeException)
                        throw (HandlerRuntimeException) e.getTargetException();
                    throw new RuntimeException(e.getTargetException());
//                                handlerExceptionThrower.proceed(
//                                HandlerErrorType.INTERNAL.generateDetailedError("error.unknown"));
                } finally {
                    handlerContextThreadLocalProvider.remove();
                    handlerSession.remove();
                }
            }).exceptionallyAsync(ex -> {
                HandlerContext handlerContext = existingHandlerContextMap.remove(uniqueIdContainer.get());
                try {
                    handlerSession.set(handlerSessionContainer.get());
                    handlerContextThreadLocalProvider.set(handlerContext);
                    /*if (ex != null) {*/
                    Throwable cause = (Throwable) ex;
                    if (ex instanceof CompletionException) {
                        cause = ((CompletionException) ex).getCause();
                    }
                    if (cause instanceof DetailedRuntimeException) {
                        cause = ((DetailedRuntimeException) cause).toHandlerRuntimeException(handlerContext);
                    }
                    if (cause instanceof HandlerRuntimeException) {
                        logger.error(
                                cause.getMessage(),
                                ((HandlerRuntimeException) cause).getDetailedError().getParams());
                        deferredResult.setErrorResult(cause);
                    } else {
                        logger.error(cause.getMessage());
                        HandlerRuntimeException handlerException = new HandlerRuntimeException(
                                handlerContext,
                                HandlerErrorType.UNKNOWN.generateDetailedError("unknown error occurred!"),
                                cause);
                        deferredResult.setErrorResult(handlerException);
                    }

                    /**
                     * ToDo: rollback strategy should be stronger than this
                     */
                    rollbackExecutors.execute(() -> doRollback(
                            handlerContainer.handlerBean, rollbackMethods, handlerContext));

                    return null;
                } finally {
                    handlerContextThreadLocalProvider.remove();
                    handlerSession.remove();
                }
            }, executorService);

            return deferredResult;
        }

        private void doRollback(
                Object handlerBean,
                List<Method> rollbackMethods,
                HandlerContext handlerContext) {
            for (Method rollbackMethod : rollbackMethods) {
                try {
                    handlerContextThreadLocalProvider.set(handlerContext);
                    rollbackMethod.invoke(handlerBean,
                            handlerContext.request(),
                            handlerContext.getInterstateTransporter());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } finally {
                    handlerContextThreadLocalProvider.remove();
                }
            }
        }
    }

    @Setter
    @Component
    @ConfigurationProperties(prefix = "ir.piana.dev.common.reactive-core")
    static class ReactiveCore {
        private int threadPoolSize;
    }

    @Setter
    @Component
    @ConfigurationProperties(prefix = "ir.piana.dev.common.handler-roles")
    static class HandlerRoleConfig {
        private List<HandlerRoleItem> items;
    }

    @Setter
    static class HandlerRoleItem {
        private String handlerClassName;
        private boolean allRequired;
        private String roles;
    }
}
