package fr.ans.psc;

import io.gravitee.resource.api.ResourceConfiguration;

public class DigitalSignResourceConfiguration implements ResourceConfiguration {

    private String digitalSignatureServerUrl;

    private String digitalSigningEndpoint;

    private boolean useSystemProxy;

    private String signingConfigId;

    private String clientSecret;

    public String getDigitalSignatureServerUrl() {
        return digitalSignatureServerUrl;
    }

    public void setDigitalSignatureServerUrl(String digitalSignatureServerUrl) {
        this.digitalSignatureServerUrl = digitalSignatureServerUrl;
    }

    public String getDigitalSigningEndpoint() {
        return digitalSigningEndpoint;
    }

    public void setDigitalSigningEndpoint(String digitalSigningEndpoint) {
        this.digitalSigningEndpoint = digitalSigningEndpoint;
    }

    public boolean isUseSystemProxy() {
        return useSystemProxy;
    }

    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
    }

    public String getSigningConfigId() {
        return signingConfigId;
    }

    public void setSigningConfigId(String signingConfigId) {
        this.signingConfigId = signingConfigId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
