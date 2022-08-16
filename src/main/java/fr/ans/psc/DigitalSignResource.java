package fr.ans.psc;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.utils.NodeUtils;
import io.gravitee.node.container.spring.SpringEnvironmentConfiguration;
import io.gravitee.resource.api.AbstractConfigurableResource;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.gravitee.common.util.VertxProxyOptionsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class DigitalSignResource extends AbstractConfigurableResource<DigitalSignResourceConfiguration> implements ApplicationContextAware {

    private final Logger logger = LoggerFactory.getLogger(DigitalSignResource.class);

    // Pattern reuse for duplicate slash removal
    private static final Pattern DUPLICATE_SLASH_REMOVER = Pattern.compile("(?<!(http:|https:))[//]+");

    private ApplicationContext applicationContext;

    private HttpClientOptions httpClientOptions;

    private final Map<Thread, HttpClient> httpClients = new ConcurrentHashMap<>();

    private Vertx vertx;

    private String userAgent;

    private String signingEndpointURI;

    @Override
    public void doStart() throws Exception {
        super.doStart();

        logger.info("Starting a digital signing resource using server at {}", configuration().getDigitalSignatureServerUrl());

        URI serverUrl = URI.create(configuration().getDigitalSignatureServerUrl());
        String dgsHost = serverUrl.getHost();
        int dgsPort = serverUrl.getPort() == -1 ? serverUrl.getPort() : 443;

        if (configuration().getDigitalSignatureServerUrl() != null) {
            signingEndpointURI = DUPLICATE_SLASH_REMOVER
                    .matcher(configuration().getDigitalSignatureServerUrl() + "/" + configuration().getDigitalSigningEndpoint())
                    .replaceAll("/");
        }

        httpClientOptions = new HttpClientOptions()
                .setDefaultHost(dgsHost)
                .setDefaultPort(dgsPort)
                .setIdleTimeout(60)
                .setConnectTimeout(10000)
                .setSsl(true).setVerifyHost(false).setTrustAll(true);

        if (configuration().isUseSystemProxy()) {
            try {
                Configuration nodeConfig = new SpringEnvironmentConfiguration(applicationContext.getEnvironment());
                VertxProxyOptionsUtils.setSystemProxy(httpClientOptions, nodeConfig);
            } catch (IllegalStateException e) {
                logger.warn(
                        "Digital Signature resource requires a system proxy to be defined but some configurations are missing or not well defined: {}",
                        e.getMessage()
                );
                logger.warn("Ignoring system proxy");
            }
        }

        userAgent = NodeUtils.userAgent(applicationContext.getBean(Node.class));
        vertx = applicationContext.getBean(Vertx.class);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        httpClients
                .values()
                .forEach(httpClient -> {
                    try {
                        httpClient.close();
                    } catch (IllegalStateException ise) {
                        logger.warn(ise.getMessage());
                    }
                });
    }

    public void signWithXmldsig(File docToSign, Handler<DigitalSignResponse> responseHandler) {
        HttpClient httpClient = httpClients.computeIfAbsent(Thread.currentThread(), context -> vertx.createHttpClient(httpClientOptions));

        final RequestOptions reqOptions = new RequestOptions()
                .setMethod(HttpMethod.POST)
                .setAbsoluteURI(signingEndpointURI)
                .setTimeout(30000L)
                .putHeader(HttpHeaders.USER_AGENT, userAgent);

        reqOptions.putHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);

        httpClient.request(reqOptions)
                .onFailure(new io.vertx.core.Handler<Throwable>() {
                    @Override
                    public void handle(Throwable event) {
                        logger.error("An error occurs while submitting document to signature server", event);
                        responseHandler.handle(new DigitalSignResponse(false, event.getMessage()));
                    }
                })
                .onSuccess(
                        new io.vertx.core.Handler<HttpClientRequest>() {
                            @Override
                            public void handle(HttpClientRequest request) {
                                request
                                        .response(
                                                new io.vertx.core.Handler<AsyncResult<HttpClientResponse>>() {
                                                    @Override
                                                    public void handle(AsyncResult<HttpClientResponse> asyncResponse) {
                                                        if (asyncResponse.failed()) {
                                                            logger.error("An error occurs while submitting document to signature server", asyncResponse.cause());
                                                            responseHandler.handle(new DigitalSignResponse(false, asyncResponse.cause().getMessage()));
                                                        } else {
                                                            final HttpClientResponse response = asyncResponse.result();
                                                            response.bodyHandler(buffer -> {
                                                                logger.debug(
                                                                        "Digital signature server returns a response with a {} status code",
                                                                        response.statusCode()
                                                                );
                                                                if (response.statusCode() == HttpStatusCode.OK_200) {
                                                                    String content = buffer.toString();
                                                                    responseHandler.handle(new DigitalSignResponse(true, content));
                                                                } else {
                                                                    responseHandler.handle(new DigitalSignResponse(false, buffer.toString()));
                                                                }
                                                            });
                                                        }
                                                    }
                                                }
                                        )
                                        .exceptionHandler(
                                                new io.vertx.core.Handler<Throwable>() {
                                                    @Override
                                                    public void handle(Throwable event) {
                                                        logger.error("An error occurs while submitting document to signature server", event);
                                                        responseHandler.handle(new DigitalSignResponse(false, event.getMessage()));
                                                    }
                                                }
                                        );
                                request.end();
                            }
                        }
                );

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
