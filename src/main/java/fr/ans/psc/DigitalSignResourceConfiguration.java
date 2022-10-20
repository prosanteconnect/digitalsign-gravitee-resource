package fr.ans.psc;

import io.gravitee.resource.api.ResourceConfiguration;

public class DigitalSignResourceConfiguration implements ResourceConfiguration {

    private String digitalSignatureServerUrl;

    private boolean useSystemProxy;

    private String signingConfigId;

    private String clientSecret;

    private boolean useSSL;

    public String getDigitalSignatureServerUrl() {
        return digitalSignatureServerUrl;
    }

    public void setDigitalSignatureServerUrl(String digitalSignatureServerUrl) {
        this.digitalSignatureServerUrl = digitalSignatureServerUrl;
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

    public boolean isUseSSL() {
        return useSSL;
    }

    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }
}
