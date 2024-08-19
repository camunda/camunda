/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

public class CCSMAuthConfiguration {

  // the url to Identity
  private String issuerUrl;
  // the url to Identity (back channel for container to container communication)
  private String issuerBackendUrl;
  // the redirect root url back to Optimize. If not provided, Optimize uses the container url
  private String redirectRootUrl;
  // Identity client id to use by Optimize
  private String clientId;
  // Identity client secret to use by Optimize
  private String clientSecret;
  // Identity audience
  private String audience;
  private String baseUrl;

  public CCSMAuthConfiguration() {}

  public String getIssuerUrl() {
    return issuerUrl;
  }

  public void setIssuerUrl(final String issuerUrl) {
    this.issuerUrl = issuerUrl;
  }

  public String getIssuerBackendUrl() {
    return issuerBackendUrl;
  }

  public void setIssuerBackendUrl(final String issuerBackendUrl) {
    this.issuerBackendUrl = issuerBackendUrl;
  }

  public String getRedirectRootUrl() {
    return redirectRootUrl;
  }

  public void setRedirectRootUrl(final String redirectRootUrl) {
    this.redirectRootUrl = redirectRootUrl;
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

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CCSMAuthConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $issuerUrl = getIssuerUrl();
    result = result * PRIME + ($issuerUrl == null ? 43 : $issuerUrl.hashCode());
    final Object $issuerBackendUrl = getIssuerBackendUrl();
    result = result * PRIME + ($issuerBackendUrl == null ? 43 : $issuerBackendUrl.hashCode());
    final Object $redirectRootUrl = getRedirectRootUrl();
    result = result * PRIME + ($redirectRootUrl == null ? 43 : $redirectRootUrl.hashCode());
    final Object $clientId = getClientId();
    result = result * PRIME + ($clientId == null ? 43 : $clientId.hashCode());
    final Object $clientSecret = getClientSecret();
    result = result * PRIME + ($clientSecret == null ? 43 : $clientSecret.hashCode());
    final Object $audience = getAudience();
    result = result * PRIME + ($audience == null ? 43 : $audience.hashCode());
    final Object $baseUrl = getBaseUrl();
    result = result * PRIME + ($baseUrl == null ? 43 : $baseUrl.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CCSMAuthConfiguration)) {
      return false;
    }
    final CCSMAuthConfiguration other = (CCSMAuthConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$issuerUrl = getIssuerUrl();
    final Object other$issuerUrl = other.getIssuerUrl();
    if (this$issuerUrl == null
        ? other$issuerUrl != null
        : !this$issuerUrl.equals(other$issuerUrl)) {
      return false;
    }
    final Object this$issuerBackendUrl = getIssuerBackendUrl();
    final Object other$issuerBackendUrl = other.getIssuerBackendUrl();
    if (this$issuerBackendUrl == null
        ? other$issuerBackendUrl != null
        : !this$issuerBackendUrl.equals(other$issuerBackendUrl)) {
      return false;
    }
    final Object this$redirectRootUrl = getRedirectRootUrl();
    final Object other$redirectRootUrl = other.getRedirectRootUrl();
    if (this$redirectRootUrl == null
        ? other$redirectRootUrl != null
        : !this$redirectRootUrl.equals(other$redirectRootUrl)) {
      return false;
    }
    final Object this$clientId = getClientId();
    final Object other$clientId = other.getClientId();
    if (this$clientId == null ? other$clientId != null : !this$clientId.equals(other$clientId)) {
      return false;
    }
    final Object this$clientSecret = getClientSecret();
    final Object other$clientSecret = other.getClientSecret();
    if (this$clientSecret == null
        ? other$clientSecret != null
        : !this$clientSecret.equals(other$clientSecret)) {
      return false;
    }
    final Object this$audience = getAudience();
    final Object other$audience = other.getAudience();
    if (this$audience == null ? other$audience != null : !this$audience.equals(other$audience)) {
      return false;
    }
    final Object this$baseUrl = getBaseUrl();
    final Object other$baseUrl = other.getBaseUrl();
    if (this$baseUrl == null ? other$baseUrl != null : !this$baseUrl.equals(other$baseUrl)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CCSMAuthConfiguration(issuerUrl="
        + getIssuerUrl()
        + ", issuerBackendUrl="
        + getIssuerBackendUrl()
        + ", redirectRootUrl="
        + getRedirectRootUrl()
        + ", clientId="
        + getClientId()
        + ", clientSecret="
        + getClientSecret()
        + ", audience="
        + getAudience()
        + ", baseUrl="
        + getBaseUrl()
        + ")";
  }
}
