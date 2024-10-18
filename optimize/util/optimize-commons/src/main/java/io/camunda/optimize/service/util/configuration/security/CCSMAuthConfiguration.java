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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
