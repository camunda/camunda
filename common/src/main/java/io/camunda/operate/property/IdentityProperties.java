/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.property;

public class IdentityProperties {
    private String issuerUrl;
    private String issuerBackendUrl;
    private String clientId;
    private String clientSecret;
    private String audience;

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public void setIssuerUrl(final String issuerUrl) {
        this.issuerUrl = issuerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getIssuerBackendUrl() {
        return issuerBackendUrl;
    }

    public void setIssuerBackendUrl(final String issuerBackendUrl) {
        this.issuerBackendUrl = issuerBackendUrl;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(final String audience) {
        this.audience = audience;
    }
}
