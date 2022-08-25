package fr.ans.psc;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.utils.NodeUtils;
import io.gravitee.node.container.spring.SpringEnvironmentConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.gravitee.common.util.VertxProxyOptionsUtils;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class DigitalSignEsignsanteResource extends DigitalSignResource<DigitalSignResourceConfiguration> implements ApplicationContextAware {

    private final Logger logger = LoggerFactory.getLogger(DigitalSignEsignsanteResource.class);

    // Pattern reuse for duplicate slash removal
    private static final Pattern DUPLICATE_SLASH_REMOVER = Pattern.compile("(?<!(http:|https:))[//]+");

    private final int HTTP_PORT = 80;

    private final int HTTPS_PORT = 443;

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
        //TODO rm debug log
        System.out.println("Starting a digital signing resource using server at " + configuration().getDigitalSignatureServerUrl());

        URI serverUrl = URI.create(configuration().getDigitalSignatureServerUrl());
        String dgsHost = serverUrl.getHost();
        int dgsPort = serverUrl.getPort() != -1 ? serverUrl.getPort() : configuration().isUseSSL() ? HTTPS_PORT : HTTP_PORT;

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
                .setSsl(configuration().isUseSSL()).setVerifyHost(false).setTrustAll(true);

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

        //TODO rm debug log
        System.out.println("End of signing resource doStart method");
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

    public void signWithXmldsig(byte[] docToSign, Handler<DigitalSignResponse> responseHandler) {
        //TODO rm debug log
        System.out.println("in main sign method");

        HttpClient httpClient = httpClients.computeIfAbsent(Thread.currentThread(), context -> vertx.createHttpClient(httpClientOptions));
        WebClient webClient = WebClient.wrap(httpClient);

        Buffer buffer = Buffer.buffer(docToSign);
        MultipartForm form = MultipartForm.create()
                .attribute("idSignConf", configuration().getSigningConfigId())
                .attribute("secret", configuration().getClientSecret())
                .binaryFileUpload(
                        "file",
                        "file",
                        buffer,
                        MediaType.MEDIA_APPLICATION_OCTET_STREAM.toMediaString());

        //TODO rm debug log
        System.out.println("before client call");
        System.out.println(signingEndpointURI);

        webClient.post(signingEndpointURI)
                .putHeader("content-type", "multipart/form-data")
                .putHeader("Accept", "application/json")
                .sendMultipartForm(form)
                .onFailure(new io.vertx.core.Handler<Throwable>() {
                    @Override
                    public void handle(Throwable event) {
                        logger.error("An error occurs while submitting document to signature server", event);
                        //TODO rm debug log
                        System.out.println("An error occurs while submitting document to signature server " + Arrays.toString(event.getStackTrace()));
                        responseHandler.handle(new DigitalSignResponse(false, event.getMessage()));
                    }
                })
                .onSuccess(bufferHttpResponse -> {
                    if (bufferHttpResponse.statusCode() == HttpStatusCode.OK_200) {
                        //TODO log.info
                        //TODO rm debug log
                        System.out.println("success ! " + bufferHttpResponse.bodyAsString());
                        responseHandler.handle(new DigitalSignResponse(true, bufferHttpResponse.bodyAsString()));
                    } else {
                        //TODO log.error
                        //TODO rm debug log
                        System.out.println("failed ! " + bufferHttpResponse.bodyAsString());
                        responseHandler.handle(new DigitalSignResponse(false, bufferHttpResponse.bodyAsString()));
                    }
                });
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
