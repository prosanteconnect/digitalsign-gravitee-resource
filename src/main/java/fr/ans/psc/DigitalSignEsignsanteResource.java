package fr.ans.psc;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.google.gson.Gson;

import fr.ans.esignsante.ApiClient;
import fr.ans.esignsante.api.SignaturesApiControllerApi;
import fr.ans.esignsante.model.ESignSanteSignatureReport;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.util.VertxProxyOptionsUtils;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.utils.NodeUtils;
import io.gravitee.node.container.spring.SpringEnvironmentConfiguration;
import io.reactivex.annotations.NonNull;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

public class DigitalSignEsignsanteResource extends DigitalSignResource<DigitalSignResourceConfiguration>
		implements ApplicationContextAware {

	private final Logger logger = LoggerFactory.getLogger(DigitalSignEsignsanteResource.class);

	// Pattern reuse for duplicate slash removal
	private static final Pattern DUPLICATE_SLASH_REMOVER = Pattern.compile("(?<!(http:|https:))[//]+");

	private final int HTTP_PORT = 80;

	private final int HTTPS_PORT = 443;

	private final String ID_SIGN_CONF_KEY = "idSignConf";

	private final String SIGN_SECRET_KEY = "secret";

	private final String CONTENT_TYPE_HEADER = "content-type";

	private final String MULTIPART_FORM_HEADER = "multipart/form-data";

	private final String ACCEPT_HEADER = "Accept";

	private final String JSON_HEADER = "application/json";

	private ApplicationContext applicationContext;

	private HttpClientOptions httpClientOptions;

	private final Map<Context, HttpClient> httpClients = new ConcurrentHashMap<>();

	private Vertx vertx;

	private String userAgent;

	private String signingEndpointURI;

	@Override
	public void doStart() throws Exception {
		super.doStart();

		logger.info("Starting a digital signing resource using server at {}",
				configuration().getDigitalSignatureServerUrl());

		URI serverUrl = URI.create(configuration().getDigitalSignatureServerUrl());
		String dgsHost = serverUrl.getHost();
		int dgsPort = serverUrl.getPort() != -1 ? serverUrl.getPort()
				: configuration().isUseSSL() ? HTTPS_PORT : HTTP_PORT;

		if (configuration().getDigitalSignatureServerUrl() != null) {
			signingEndpointURI = DUPLICATE_SLASH_REMOVER.matcher(
					configuration().getDigitalSignatureServerUrl() + "/" + configuration().getDigitalSigningEndpoint())
					.replaceAll("/");
		}

		httpClientOptions = new HttpClientOptions().setDefaultHost(dgsHost).setDefaultPort(dgsPort).setIdleTimeout(60)
				.setConnectTimeout(10000).setSsl(configuration().isUseSSL()).setVerifyHost(false).setTrustAll(true);

		if (configuration().isUseSystemProxy()) {
			try {
				Configuration nodeConfig = new SpringEnvironmentConfiguration(applicationContext.getEnvironment());
				VertxProxyOptionsUtils.setSystemProxy(httpClientOptions, nodeConfig);
			} catch (IllegalStateException e) {
				logger.warn(
						"Digital Signature resource requires a system proxy to be defined but some configurations are missing or not well defined: {}",
						e.getMessage());
				logger.warn("Ignoring system proxy");
			}
		}

		userAgent = NodeUtils.userAgent(applicationContext.getBean(Node.class));
		vertx = applicationContext.getBean(Vertx.class);
	}

	@Override
	public void doStop() throws Exception {
		super.doStop();

		httpClients.values().forEach(httpClient -> {
			try {
				httpClient.close();
			} catch (IllegalStateException ise) {
				logger.warn(ise.getMessage());
			}
		});
	}

	public void sign(byte[] docToSign, List<AdditionalParameter> additionalParameters,
			Handler<DigitalSignResponse> responseHandler) {
		HttpClient httpClient = httpClients.computeIfAbsent(Vertx.currentContext(),
				__ -> vertx.createHttpClient(httpClientOptions));
		WebClient webClient = WebClient.wrap(httpClient);

		Buffer buffer = Buffer.buffer(docToSign);
		MultipartForm form = MultipartForm.create().attribute(ID_SIGN_CONF_KEY, configuration().getSigningConfigId())
				.attribute(SIGN_SECRET_KEY, configuration().getClientSecret())
				.binaryFileUpload("file", "file", buffer, MediaType.MEDIA_APPLICATION_OCTET_STREAM.toMediaString());

		if (additionalParameters != null && !additionalParameters.isEmpty()) {
			additionalParameters.forEach(param -> form.attribute(param.getName(), param.getValue()));
		}

		webClient.post(signingEndpointURI).putHeader(CONTENT_TYPE_HEADER, MULTIPART_FORM_HEADER)
				.putHeader(ACCEPT_HEADER, JSON_HEADER).putHeader(HttpHeaders.USER_AGENT.toString(), userAgent)
				.sendMultipartForm(form).onFailure(failure -> {
					logger.error("Could not send document do signature server", failure);
					responseHandler.handle(new DigitalSignResponse(failure));
				}).onSuccess(response -> {
					if (response.statusCode() == HttpStatusCode.OK_200) {
						logger.info("document succesfully signed");
						responseHandler.handle(new DigitalSignResponse(true, response.bodyAsString()));
					} else {
						logger.error("signing request rejected by Signature server");
						responseHandler.handle(new DigitalSignResponse(false, response.bodyAsString()));
					}
				});
	}

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public DigitalSignResponse sign(byte[] docToSign, List<AdditionalParameter> additionalParameters) {
		ApiClient client = new ApiClient();
		client.setBasePath(signingEndpointURI);
		SignaturesApiControllerApi api = new SignaturesApiControllerApi(client);
		File input = null;
		try {
			input = File.createTempFile("sign", "tmp");
			Files.write(input.toPath(),docToSign);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ESignSanteSignatureReport report = api.signatureXMLdsig("password", 3L, input);
		Gson gson = new Gson();
		return new DigitalSignResponse(true, gson.toJson(report, ESignSanteSignatureReport.class));
	}

}
